package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

/** Checks mutable instance isolation against two runtimes from the same original model data. */
public final class YsmInstanceIsolationProbe {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mojang.blaze3d.systems.RenderSystem").getMethod("initRenderThread").invoke(null);
        try (OmniMorphModel model = OmniMorphModel.load(Path.of(args[0]))) {
            Object first = model.createRuntime();
            try (var firstOwner = new PreviewAvatarResources(first.getClass().getMethod("owner").invoke(first))) {
                Object second = model.createRuntime();
                try (var secondOwner = new PreviewAvatarResources(second.getClass().getMethod("owner").invoke(second))) {
                    verify(model, first, second, args[1], args[2]);
                }
            }
        }
    }

    private static void verifyQueries(Object first, Object second) throws Exception {
        String prefix = "top.aihmc.omnimorph.molang.";
        Class<?> engineType = Class.forName(prefix + "MolangEngine");
        Class<?> bindingType = Class.forName(prefix + "runtime.binding.ObjectBinding");
        Object firstOwner = first.getClass().getMethod("owner").invoke(first);
        Object binding = firstOwner.getClass().getMethod("getAvatarBindings").invoke(firstOwner);
        Object engine = engineType.getMethod("fromCustomBinding", bindingType).invoke(null, binding);
        List<?> expressions = (List<?>) engineType.getMethod("parse", String.class)
            .invoke(engine, "query.health * 2 + q.anim_time");
        Class<?> evaluatorType = Class.forName(prefix + "runtime.ExpressionEvaluator");
        Class<?> expressionType = Class.forName(prefix + "parser.ast.Expression");
        Object[] runtimes = {first, second};
        for (int index = 0; index < runtimes.length; index++) {
            Object owner = runtimes[index].getClass().getMethod("owner").invoke(runtimes[index]);
            Object context = owner.getClass().getMethod("getMolangContext").invoke(owner);
            context.getClass().getField("anim_time").setFloat(context, 0.25f);
            Object evaluator = evaluatorType.getMethod("evaluator", Object.class).invoke(null, context);
            float result = (float) evaluatorType.getMethod("evalAsFloat", expressionType)
                .invoke(evaluator, expressions.getFirst());
            float expected = index == 0 ? 6.25f : 14.25f;
            if (result != expected) {
                throw new AssertionError("Original query override crossed instances or lost native anim_time: " + result);
            }
        }
        System.out.println("OMP CONFIGURED QUERY PASS health=3,7 originalAnimTime=0.25 sharedExpression=true");
    }

    private static void verify(OmniMorphModel model, Object first, Object second,
                               String clip, String partName) throws Exception {
        Object firstPart = first.getClass().getMethod("getPart", String.class).invoke(first, partName);
        Object secondPart = second.getClass().getMethod("getPart", String.class).invoke(second, partName);
        if (first == second || firstPart == secondPart || firstPart == null || secondPart == null) {
            throw new AssertionError("Instances share mutable runtime/part state or requested part is absent");
        }
        var animation = new JsonObject();
        animation.addProperty("name", clip);
        var queryValues = new JsonObject();
        queryValues.addProperty("health", 3);
        animation.add("queries", queryValues);
        var firstAnimation = new YsmAnimationInput(first, animation);
        queryValues.addProperty("health", 7);
        var secondAnimation = new YsmAnimationInput(second, animation);
        verifyQueries(first, second);
        firstAnimation.sample(0.1f);
        secondAnimation.sample(0.1f);
        try (var firstBatches = new OmniMorphBatches(model.geometry(), first);
             var secondBatches = new OmniMorphBatches(model.geometry(), second)) {
            List<double[]> baseline = firstBatches.transform(false);
            var movement = animation.deepCopy();
            movement.getAsJsonObject("queries").addProperty("ground_speed", 10);
            Object secondOwner = second.getClass().getMethod("owner").invoke(second);
            Object secondContext = secondOwner.getClass().getMethod("getMolangContext").invoke(secondOwner);
            ConfiguredMolangQueries.configure(secondContext, movement);
            secondAnimation.sample(0.1f);
            secondBatches.refresh();
            double queryChange = difference(baseline, secondBatches.transform(false));
            firstBatches.refresh();
            if (queryChange <= 1e-6 || difference(baseline, firstBatches.transform(false)) != 0) {
                throw new AssertionError("Configured ground_speed did not drive isolated original jump pose");
            }
            System.out.println("YSM QUERY DRIVEN POSE PASS groundSpeed=10 time=0.1 change=" + queryChange);
            var invalid = animation.deepCopy();
            invalid.getAsJsonObject("queries").addProperty("preview_unknown_query", 1);
            try {
                ConfiguredMolangQueries.configure(secondContext, invalid);
                throw new AssertionError("Unknown configured query was accepted");
            } catch (IllegalArgumentException expected) {
                System.out.println("YSM UNKNOWN QUERY REJECTED " + expected.getMessage());
            }
            ConfiguredMolangQueries.configure(secondContext, animation);
            secondAnimation.sample(0.3f);
            secondBatches.refresh();
            List<double[]> changed = secondBatches.transform(false);
            firstBatches.refresh();
            if (difference(baseline, firstBatches.transform(false)) != 0) {
                throw new AssertionError("Sampling second animation changed first instance vertices");
            }
            double animationChange = difference(baseline, changed);
            if (animationChange <= 1e-6) {
                throw new AssertionError("Chosen samples do not exercise animation state changes");
            }
            firstPart.getClass().getMethod("setVisible", boolean.class).invoke(firstPart, false);
            secondPart.getClass().getMethod("setVisible", boolean.class).invoke(secondPart, true);
            if ((boolean) firstPart.getClass().getMethod("visibleRaw").invoke(firstPart)
                || !(boolean) secondPart.getClass().getMethod("visibleRaw").invoke(secondPart)) {
                throw new AssertionError("Visibility mutations crossed instance boundaries");
            }
            firstBatches.refresh();
            secondBatches.refresh();
            long hidden = hiddenCount(firstBatches.drawBatches());
            long visiblePeerHidden = hiddenCount(secondBatches.drawBatches());
            if (hidden <= visiblePeerHidden) {
                throw new AssertionError("Original visibility did not reach independent draw batches");
            }
            System.out.println("YSM INSTANCE ISOLATION PASS animationChange=" + animationChange
                + " firstHidden=" + hidden + " secondHidden=" + visiblePeerHidden);
        }
    }

    private static long hiddenCount(List<Object> batches) throws Exception {
        long count = 0;
        for (Object batch : batches) {
            Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
            if (!(boolean) semantics.getClass().getMethod("visible").invoke(semantics)) {
                count++;
            }
        }
        return count;
    }

    static double difference(List<double[]> first, List<double[]> second) {
        if (first.size() != second.size()) {
            throw new AssertionError("Unexpected batch topology change");
        }
        double maximum = 0;
        for (int batch = 0; batch < first.size(); batch++) {
            double[] a = first.get(batch);
            double[] b = second.get(batch);
            if (a.length != b.length) {
                throw new AssertionError("Unexpected vertex topology change");
            }
            for (int value = 0; value < a.length; value++) {
                maximum = Math.max(maximum, Math.abs(a[value] - b[value]));
            }
        }
        return maximum;
    }
}
