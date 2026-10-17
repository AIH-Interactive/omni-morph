package top.aihmc.omnimorph.model.ysm;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.decoration.ArmorStand;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;

public final class YsmFilterSmokeTest {
    private static final MolangEngine ENGINE = MolangEngine.fromCustomBinding(new MolangBindings());

    private YsmFilterSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path environment = Files.createTempDirectory("figura-filter-environment-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", environment.toString());
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            runTests();
        } finally {
            if (previous == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previous);
            }
            try (var files = Files.walk(environment)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(environment)) {
                        throw new IllegalStateException("Unexpected fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void runTests() throws Exception {
        testFirstOrder();
        testSecondOrder();
        testBoneAndAvatarIsolation();
        testRenderFrameDomains();
        Function first = (Function) new MolangBindings.YsmBinding().getProperty("first_order");
        Function second = (Function) new MolangBindings.YsmBinding().getProperty("second_order");
        require(!first.validateArgumentSize(1) && first.validateArgumentSize(2) && first.validateArgumentSize(5),
                "first-order reference argument count");
        require(!second.validateArgumentSize(1) && second.validateArgumentSize(2) && second.validateArgumentSize(5),
                "second-order reference argument count");
        System.out.println("M4 real first/second-order bindings, numeric vectors, frame/bone/view/Avatar isolation and cleanup passed.");
    }

    private static void testFirstOrder() throws Exception {
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        update(context, YsmFilterRuntime.Domain.BODY, 100f);
        equal(10f, evaluate(context, "ysm.first_order('p',10);"), "creation returns input, as in reference");
        update(context, YsmFilterRuntime.Domain.BODY, 101f);
        equal(0.5f, evaluate(context, "ysm.first_order('p',20);"), "advance uses previously supplied input");
        update(context, YsmFilterRuntime.Domain.BODY, 101f);
        equal(0.5f, evaluate(context, "ysm.first_order('p',20);"), "same render tick cannot advance twice");
        update(context, YsmFilterRuntime.Domain.BODY, 102f);
        equal(1.475f, evaluate(context, "ysm.first_order('p',20);"), "second independent first-order recurrence coefficient");
        update(context, YsmFilterRuntime.Domain.BODY, 500f);
        equal(20f, evaluate(context, "ysm.first_order('p',20,0);"), "large step clamps to response");
        update(context, YsmFilterRuntime.Domain.BODY, 501f);
        equal(20f, evaluate(context, "ysm.first_order('p',2,0);"), "zero response snaps on update before new parameter capture");
        update(context, YsmFilterRuntime.Domain.BODY, 502f);
        equal(2f, evaluate(context, "ysm.first_order('p',2,0);"), "zero response update snaps to last input");
        equal(0f, evaluate(context, "ysm.first_order('',5);"), "empty identifier rejected");
        equal(0f, evaluate(context, "ysm.first_order(1,5);"), "non-string identifier rejected");
        context.is_first_person = 1f;
        update(context, YsmFilterRuntime.Domain.FIRST_PERSON, 100f);
        equal(80f, evaluate(context, "ysm.first_order('p',80);"), "arm domain does not reuse body state");
        update(context, YsmFilterRuntime.Domain.FIRST_PERSON, 101f);
        equal(4f, evaluate(context, "ysm.first_order('p',80);"), "arm domain advances independently");
        context.filters.clear();
        equal(9f, evaluate(context, "ysm.first_order('p',9);"), "model cleanup recreates fresh filter state");
    }

    private static void testSecondOrder() throws Exception {
        Avatar.MolangContext context = new Avatar.MolangContext(null);
        update(context, YsmFilterRuntime.Domain.BODY, 100f);
        equal(10f, evaluate(context, "ysm.second_order('spring',10,1,0,0);"), "second-order creation value");
        update(context, YsmFilterRuntime.Domain.BODY, 101f);
        equal(0f, evaluate(context, "ysm.second_order('spring',20,1,0,0);"), "first position step starts with zero velocity");
        update(context, YsmFilterRuntime.Domain.BODY, 102f);
        equal((float) (0.1d * Math.PI * Math.PI), evaluate(context, "ysm.second_order('spring',20,1,0,0);"),
                "independent second-order velocity/position recurrence");
        Avatar.MolangContext fast = new Avatar.MolangContext(null);
        update(fast, YsmFilterRuntime.Domain.BODY, 100f);
        evaluate(fast, "ysm.second_order('fast',1,5,0,0);");
        update(fast, YsmFilterRuntime.Domain.BODY, 500f);
        equal((float) (0.25d * Math.PI * Math.PI), evaluate(fast, "ysm.second_order('fast',1,5,0,0);"),
                "hitch clamps to .1 seconds and uses two stable substeps");
        context.filters.secondOrder(YsmFilterRuntime.Domain.BODY, "invalid", 0f, Float.NaN, Float.NaN, Float.NaN);
        update(context, YsmFilterRuntime.Domain.BODY, 103f);
        require(Float.isFinite(context.filters.secondOrder(YsmFilterRuntime.Domain.BODY, "invalid", 0f,
                Float.NaN, Float.NaN, Float.NaN)), "nonfinite coefficients do not poison simulated state");

        Avatar.MolangContext defaults = new Avatar.MolangContext(null);
        update(defaults, YsmFilterRuntime.Domain.BODY, 100f);
        evaluate(defaults, "ysm.second_order('default',3);");
        evaluate(defaults, "ysm.second_order('explicit',3,1,1,1);");
        for (int tick = 101; tick <= 110; tick++) {
            update(defaults, YsmFilterRuntime.Domain.BODY, tick);
            equal(evaluate(defaults, "ysm.second_order('explicit',3,1,1,1);"),
                    evaluate(defaults, "ysm.second_order('default',3);"),
                    "omitted second-order parameters retain reference defaults");
        }
    }

    private static void testBoneAndAvatarIsolation() throws Exception {
        Avatar.MolangContext first = new Avatar.MolangContext(null);
        Avatar.MolangContext second = new Avatar.MolangContext(null);
        update(first, YsmFilterRuntime.Domain.BODY, 100f);
        update(second, YsmFilterRuntime.Domain.BODY, 100f);
        equal(10f, evaluate(first, "ysm.first_order('shared',10);"), "first Avatar creates its state");
        equal(90f, evaluate(second, "ysm.first_order('shared',90);"), "second Avatar uses its own same-named state");
        update(first, YsmFilterRuntime.Domain.BODY, 101f);
        equal(0.5f, evaluate(first, "ysm.first_order('shared',10);"), "only first Avatar advances");
        update(second, YsmFilterRuntime.Domain.BODY, 101f);
        equal(4.5f, evaluate(second, "ysm.first_order('shared',90);"), "second Avatar retains independent input");
        var clip = YsmAnimationParser.parse("""
                {"animations":{"fixture":{"bones":{
                  "left":{"position":["ysm.second_order('same',2,1,0,0)",0,0]},
                  "right":{"position":["ysm.second_order('same',9,1,0,0)",0,0]}
                }}}}
                """, ENGINE).get("fixture");
        var evaluator = ExpressionEvaluator.evaluator(first);
        equal(2f, YsmAnimationSampler.sampleBone(clip.boneAnimations.get("left"), 0f, evaluator).position()[0],
                "production sampler installs left bone scope");
        equal(9f, YsmAnimationSampler.sampleBone(clip.boneAnimations.get("right"), 0f, evaluator).position()[0],
                "right bone cannot consume left second-order state");
        update(first, YsmFilterRuntime.Domain.BODY, 102f);
        YsmAnimationSampler.sampleBone(clip.boneAnimations.get("left"), 0f, evaluator);
        YsmAnimationSampler.sampleBone(clip.boneAnimations.get("right"), 0f, evaluator);
        update(first, YsmFilterRuntime.Domain.BODY, 103f);
        float left = YsmAnimationSampler.sampleBone(clip.boneAnimations.get("left"), 0f, evaluator).position()[0];
        float right = YsmAnimationSampler.sampleBone(clip.boneAnimations.get("right"), 0f, evaluator).position()[0];
        equal((float) (0.02d * Math.PI * Math.PI), left, "left bone independent numeric vector");
        equal((float) (0.09d * Math.PI * Math.PI), right, "right bone independent numeric vector");
        try (var outer = first.filters.beginBone("outer")) {
            equal(7f, evaluate(first, "ysm.first_order('global',7);"), "first-order creates an unscoped key");
            equal(8f, evaluate(first, "ysm.second_order('scoped',8);"), "outer scoped filter created");
            try (var inner = first.filters.beginBone("inner")) {
                equal(0f, evaluate(first, "ysm.first_order('global',99);"),
                        "first-order name intentionally shared across bones, as in reference");
                equal(9f, evaluate(first, "ysm.second_order('scoped',9);"), "inner scope owns different state");
            }
            equal(0f, evaluate(first, "ysm.second_order('scoped',10);"), "nested scope restores outer state");
        }
        equal(11f, evaluate(first, "ysm.second_order('scoped',11);"), "normal close restores unscoped context");
        try (var outer = first.filters.beginBone("outer")) {
            try (var inner = first.filters.beginBone("inner")) {
                first.filters.clear();
            }
        }
        equal(12f, evaluate(first, "ysm.second_order('new',12);"), "fresh unscoped filter after clear");
        try (var outer = first.filters.beginBone("outer")) {
            equal(13f, evaluate(first, "ysm.second_order('new',13);"),
                    "clear invalidates old nested scopes rather than resurrecting outer bone");
        }
    }

    private static void testRenderFrameDomains() {
        YsmFilterRuntime filters = new YsmFilterRuntime();
        for (YsmFilterRuntime.Domain domain : YsmFilterRuntime.Domain.values()) {
            filters.update(domain, 100f, 1L, null);
            float input = domain == YsmFilterRuntime.Domain.BODY ? 10f
                    : domain == YsmFilterRuntime.Domain.FIRST_PERSON ? 40f : 90f;
            equal(input, filters.firstOrder(domain, "same", input, 1f), "domain first creation: " + domain);
            equal(0f, filters.firstOrder(domain, "same", input, 1f),
                    "reference repeated creation-frame call reads internal zero: " + domain);
            equal(input, filters.secondOrder(domain, "spring", input, 1f, 0f, 0f),
                    "second-order creates independent domain state: " + domain);
        }
        filters.update(YsmFilterRuntime.Domain.BODY, 101f, 2L, null);
        equal(0.5f, filters.firstOrder(YsmFilterRuntime.Domain.BODY, "same", 20f, 1f), "body advances on frame two");
        filters.update(YsmFilterRuntime.Domain.BODY, 150f, 2L, null);
        equal(0.5f, filters.firstOrder(YsmFilterRuntime.Domain.BODY, "same", 20f, 1f),
                "changed render time in repeated pass cannot advance a domain twice");
        filters.update(YsmFilterRuntime.Domain.PREVIEW, 101f, 2L, null);
        equal(4.5f, filters.firstOrder(YsmFilterRuntime.Domain.PREVIEW, "same", 90f, 1f),
                "preview advances after body in the same global frame");
        filters.update(YsmFilterRuntime.Domain.FIRST_PERSON, 101f, 2L, null);
        equal(2f, filters.firstOrder(YsmFilterRuntime.Domain.FIRST_PERSON, "same", 40f, 1f),
                "arm advances after preview in the same global frame");
        for (YsmFilterRuntime.Domain domain : YsmFilterRuntime.Domain.values()) {
            float input = domain == YsmFilterRuntime.Domain.BODY ? 10f
                    : domain == YsmFilterRuntime.Domain.FIRST_PERSON ? 40f : 90f;
            equal(0f, filters.secondOrder(domain, "spring", input, 1f, 0f, 0f),
                    "first spring update starts at zero velocity: " + domain);
            filters.update(domain, 102f, 3L, null);
            equal((float) (input * 0.01d * Math.PI * Math.PI),
                    filters.secondOrder(domain, "spring", input, 1f, 0f, 0f),
                    "second spring update preserves each domain's input: " + domain);
        }
        filters.update(YsmFilterRuntime.Domain.BODY, 102f, 3L, null);
        equal(1.475f, filters.firstOrder(YsmFilterRuntime.Domain.BODY, "same", 20f, 1f),
                "rejected repeated pass did not overwrite the simulation clock");
        filters.clear();
        filters.update(YsmFilterRuntime.Domain.PREVIEW, 101f, 2L, null);
        equal(7f, filters.firstOrder(YsmFilterRuntime.Domain.PREVIEW, "same", 7f, 1f),
                "cleanup resets both domain state and render frame guard");
    }

    private static void update(Avatar.MolangContext context, YsmFilterRuntime.Domain domain, float ticks) {
        context.filters.update(domain, ticks, null);
    }

    private static float evaluate(Avatar.MolangContext context, String source) throws Exception {
        if (context.entity == null) {
            // Numeric fixtures supply only a typed entity; no game world or entity behavior is simulated.
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            context.entity = (ArmorStand) ((Unsafe) unsafeField.get(null)).allocateInstance(ArmorStand.class);
        }
        return ExpressionEvaluator.evaluator(context).evalAsFloat(ENGINE.parse(source).getFirst());
    }

    private static void equal(float expected, float actual, String message) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) < 0.00002f, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
