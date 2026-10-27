package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlRuntime;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.Struct;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;
import top.aihmc.omnimorph.molang.storage.StringPool;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class YsmBoneQuerySmokeTest {
    public static void main(String[] args) throws Exception {
        Fixture first = new Fixture(true);
        Fixture second = new Fixture(false);
        YsmModelPart body = first.runtime.getPart("root");
        body.addAnimPos(2d, 3d, 4d);
        body.posRaw().set(5d, 6d, 7d);
        body.addAnimRot(1d, 2d, 3d);
        body.rotRaw().set(4d, 5d, 6d);
        body.mulAnimScale(2d, 3d, 4d);
        body.scaleRaw().set(0.5d, 2d, 3d);
        vector(first, "bone_pos", 7f, 9f, 11f);
        vector(first, "bone_rot", 15f, 27f, 39f);
        vector(first, "bone_scale", 1f, 6f, 12f);
        vector(second, "bone_pos", 0f, 0f, 0f);
        vector(second, "bone_rot", 10f, 20f, 30f);
        vector(second, "bone_scale", 1f, 1f, 1f);
        equal(7f, first.number("ysm.bone_pos('ROOT').x;"), "existing runtime name lookup");

        Object value = first.eval("v.saved=ysm.bone_pos('root');");
        require(value instanceof Struct, "bone query returns Struct");
        Struct view = (Struct) value;
        require(view.copy() == view, "reference copy retains the live bone view");
        require(view.getProperty(StringPool.computeIfAbsent("unknown")) == null, "unknown vector field is null");
        first.eval("v.saved.x=999;");
        equal(7f, first.number("v.saved.x;"), "Struct writes ignored");
        body.addAnimPos(10d, 0d, 0d);
        equal(17f, first.number("v.saved.x;"), "saved view observes later bone state");
        require(second.eval("v.saved;") == null, "actual Avatar variable registries stay isolated");

        YsmModelPart arm = first.runtime.getArmPart("root");
        arm.addAnimPos(30d, 31d, 32d);
        first.context.is_first_person = 1f;
        vector(first, "bone_pos", 30f, 31f, 32f);
        vector(first, "bone_rot", 40f, 50f, 60f);
        equal(17f, first.number("v.saved.x;"), "saved body view retains its original bone after view switch");
        require(first.eval("ysm.bone_pos('body_only');") == null, "independent arm model does not leak a body bone");
        second.context.is_first_person = 1f;
        vector(second, "bone_rot", 10f, 20f, 30f);
        first.context.is_first_person = 0f;
        equal(17f, first.number("ysm.bone_pos('root').x;"), "return to body target");

        for (String functionName : new String[]{"bone_rot", "bone_pos", "bone_scale", "bone_pivot_abs"}) {
            require(first.eval("ysm." + functionName + "('missing');") == null, "missing bone returns null");
            require(first.eval("ysm." + functionName + "('');") == null, "empty identifier returns null");
            require(first.eval("ysm." + functionName + "(123);") == null, "non-string identifier returns null");
            Function function = (Function) new MolangBindings.YsmBinding().getProperty(functionName);
            require(!function.validateArgumentSize(0) && function.validateArgumentSize(1)
                    && !function.validateArgumentSize(2), "strict reference argument count");
        }
        testSamplingInputs(true);
        testSamplingInputs(false);
        testPivotCache();
        testReferenceCombinedRotation();
        System.out.println("M4 bone vector bindings: reference coordinates, live/read-only Struct, actual Avatar registries and body/arm isolation passed.");
    }

    private static void testReferenceCombinedRotation() throws Exception {
        YsmGeometry geometry = YsmGeometryParser.parse("""
                {"minecraft:geometry":[{"description":{},"bones":[
                  {"name":"root","pivot":[16,0,0],"rotation":[90,0,0]}
                ]}]}
                """);
        Fixture fixture = new Fixture(geometry, null);
        YsmGeometry.Bone bone = geometry.bones.get("root");
        YsmModelPart part = fixture.runtime.getPart("root");
        part.addAnimPos(2d, 0d, 0d);
        part.addAnimRot(0d, 0d, 90d);
        part.mulAnimScale(2d, 1d, 1d);
        // Independent column-major coefficients: T(-18/16,0,0) Rz(90) Rx(-90) S(2,1,1) T(1,0,0).
        float[] expected = {0f, 2f, 0f, 0f, 0f, 0f, -1f, 0f, -1f, 0f, 0f, 0f, -1.125f, 2f, 0f, 1f};
        PoseStack rendered = new PoseStack();
        Method renderTransform = YsmRenderer.class.getDeclaredMethod("applyBoneTransform",
                YsmGeometry.Bone.class, YsmModelPart.class, PoseStack.class);
        renderTransform.setAccessible(true);
        renderTransform.invoke(fixture.runtime.renderer(), bone, part, rendered);
        PoseStack located = new PoseStack();
        Method locatorTransform = YsmModelRuntime.class.getDeclaredMethod("applyBoneTransform",
                PoseStack.class, YsmGeometry.Bone.class, boolean.class, YsmModelPart.class);
        locatorTransform.setAccessible(true);
        locatorTransform.invoke(fixture.runtime, located, bone, false, part);
        float[] renderCoefficients = rendered.last().pose().get(new float[16]);
        float[] locatorCoefficients = located.last().pose().get(new float[16]);
        for (int index = 0; index < expected.length; index++) {
            equal(expected[index], renderCoefficients[index], "reference combined rotation render coefficient " + index);
            equal(expected[index], locatorCoefficients[index], "reference combined rotation locator coefficient " + index);
        }
    }

    private static void testPivotCache() throws Exception {
        YsmGeometry geometry = YsmGeometryParser.parse("""
                {"minecraft:geometry":[{"description":{},"bones":[
                  {"name":"root","pivot":[16,0,0],"rotation":[0,0,90]},
                  {"name":"child","parent":"root","pivot":[16,16,0]}
                ]}]}
                """);
        Fixture fixture = new Fixture(geometry, YsmGeometryParser.parse("""
                {"minecraft:geometry":[{"description":{},"bones":[
                  {"name":"root","pivot":[16,0,0],"rotation":[0,0,90]},
                  {"name":"child","parent":"root","pivot":[16,16,0]}
                ]}]}
                """));
        YsmModelPart root = fixture.runtime.getPart("root");
        YsmModelPart child = fixture.runtime.getPart("child");
        root.addAnimPos(2d, 0d, 0d);
        root.mulAnimScale(2d, 1d, 1d);
        child.addAnimPos(3d, 0d, 0d);
        fixture.eval("v.pivot=ysm.bone_pivot_abs('root');");
        equal(0f, fixture.number("v.pivot.x;"), "tracking begins with zero cached pivot");
        Method update = YsmRenderer.class.getDeclaredMethod("updateTrackedPivots", boolean.class, YsmRenderPose.class);
        update.setAccessible(true);
        update.invoke(fixture.runtime.renderer(), false, null);
        equal(18f, fixture.number("v.pivot.x;"), "reference pre-rotation translation and mirrored pixel units");
        equal(0f, fixture.number("v.pivot.y;"), "own rotation does not rotate own translation");
        equal(0f, fixture.number("ysm.bone_pivot_abs('child').x;"), "untracked bone was not eagerly cached");
        update.invoke(fixture.runtime.renderer(), false, null);
        equal(34f, fixture.number("ysm.bone_pivot_abs('child').x;"), "parent rotation affects child pivot");
        equal(-6f, fixture.number("ysm.bone_pivot_abs('child').y;"), "parent scale affects child displacement");
        equal(0f, fixture.number("ysm.bone_pivot_abs('child').z;"), "child Z vector");

        YsmRenderPose saved = fixture.runtime.captureRenderPose(false);
        root.animPosRaw().set(9d, 0d, 0d);
        child.animPosRaw().set(99d, 0d, 0d);
        root.setVisible(false);
        update.invoke(fixture.runtime.renderer(), false, null);
        equal(18f, fixture.number("v.pivot.x;"), "hidden root retains its previous rendered cache");
        equal(-6f, fixture.number("ysm.bone_pivot_abs('child').y;"), "hidden ancestor retains child's cache");
        update.invoke(fixture.runtime.renderer(), false, saved);
        equal(18f, fixture.number("v.pivot.x;"), "deferred cache uses visible submission-time pose");
        equal(-6f, fixture.number("ysm.bone_pivot_abs('child').y;"), "deferred child cannot use later mutable input");
        root.setVisible(true);
        root.animScaleRaw().set(0d, 1d, 1d);
        update.invoke(fixture.runtime.renderer(), false, null);
        equal(18f, fixture.number("v.pivot.x;"), "one zero scale component prevents cache update");

        fixture.context.is_first_person = 1f;
        fixture.runtime.getArmPart("root").addAnimPos(8d, 0d, 0d);
        equal(0f, fixture.number("ysm.bone_pivot_abs('root').x;"), "arm starts with independent cache");
        update.invoke(fixture.runtime.renderer(), true, null);
        equal(24f, fixture.number("ysm.bone_pivot_abs('root').x;"), "arm uses its own geometry and pose");
        fixture.context.is_first_person = 0f;
        equal(18f, fixture.number("v.pivot.x;"), "arm render does not overwrite body's saved view");
        fixture.runtime.boneQueries().beginFrame(300);
        try (var scope = fixture.runtime.boneQueries().beginEvaluation()) {
            root.animScaleRaw().set(1d, 1d, 1d);
            root.animPosRaw().set(4d, 0d, 0d);
            update.invoke(fixture.runtime.renderer(), false, null);
            equal(20f, fixture.number("v.pivot.x;"), "pivot cache is not stale animation-input snapshot data");
        }
        fixture.runtime.boneQueries().clear();
        equal(0f, fixture.number("v.pivot.x;"), "unload cache clearing invalidates saved pivot value");
    }

    private static void testSamplingInputs(boolean arms) throws Exception {
        Fixture fixture = new Fixture(arms);
        YsmModelPart body = fixture.runtime.getPart("root");
        YsmModelPart arm = fixture.runtime.getArmPart("root");
        body.animPosRaw().set(5d, 0d, 0d);
        fixture.runtime.boneQueries().captureOutput(false);
        arm.animPosRaw().set(20d, 0d, 0d);
        fixture.runtime.boneQueries().captureOutput(true);
        var clips = YsmAnimationParser.parse("""
                {"animations":{"query_loop":{"loop":true,"animation_length":1,"bones":{
                  "root":{"position":["ysm.bone_pos('root').x+1",0,0]}
                }}}}
                """, fixture.engine);
        YsmAnimationPlayer player = fixture.runtime.animations();
        player.registerAnimations(clips);
        // Body and first-person clips are separate domains; the arm pass samples only authored arm clips.
        player.registerArmAnimations(clips);
        player.play("query_loop", true, 1f, 0f);
        Method sample = YsmAnimationPlayer.class.getDeclaredMethod("sampleAnimations", ExpressionEvaluator.class, boolean.class);
        sample.setAccessible(true);
        for (int frame = 100; frame <= 101; frame++) {
            fixture.runtime.boneQueries().beginFrame(frame);
            fixture.context.is_first_person = 0f;
            sample.invoke(player, ExpressionEvaluator.evaluator(fixture.context), false);
            equal(6f + frame - 100f, (float) body.animPosRaw().x, "body reads previous complete frame before reset");
            fixture.context.is_first_person = 1f;
            sample.invoke(player, ExpressionEvaluator.evaluator(fixture.context), true);
            equal(21f + frame - 100f, (float) arm.animPosRaw().x, "arm owns a separate previous-frame input");
            fixture.context.is_first_person = 0f;
            sample.invoke(player, ExpressionEvaluator.evaluator(fixture.context), false);
            equal(6f + frame - 100f, (float) body.animPosRaw().x,
                    "repeated body pass cannot feed its own current-frame output back as input");
        }
        try (var scope = fixture.runtime.boneQueries().beginEvaluation()) {
            fixture.runtime.boneQueries().clear();
        }
        body.animPosRaw().set(30d, 0d, 0d);
        fixture.runtime.boneQueries().beginFrame(200);
        try (var scope = fixture.runtime.boneQueries().beginEvaluation()) {
            body.animPosRaw().set(99d, 0d, 0d);
            equal(30f, fixture.number("ysm.bone_pos('root').x;"), "clear invalidates stale scopes and restores evaluation depth");
        }
        equal(99f, fixture.number("ysm.bone_pos('root').x;"), "scope cleanup returns to the live bone value");
    }

    private static void vector(Fixture fixture, String function, float x, float y, float z) throws Exception {
        equal(x, fixture.number("ysm." + function + "('root').x;"), function + ".x");
        equal(y, fixture.number("ysm." + function + "('root').y;"), function + ".y");
        equal(z, fixture.number("ysm." + function + "('root').z;"), function + ".z");
    }

    private static final class Fixture {
        private final Avatar.MolangContext context;
        private final YsmModelRuntime runtime;
        private final MolangEngine engine;

        private Fixture(boolean arms) throws Exception {
            this(geometry(false), arms ? geometry(true) : null);
        }

        private Fixture(YsmGeometry bodyGeometry, YsmGeometry armGeometry) throws Exception {
            // Allocate only the owner shell: game registry/permissions/GPU bootstrap is outside this CPU fixture.
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Avatar owner = (Avatar) ((Unsafe) unsafeField.get(null)).allocateInstance(Avatar.class);
            install(owner, "controls", new AvatarControlRuntime());
            context = new Avatar.MolangContext(owner);
            install(owner, "molangContext", context);
            Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                    YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                    String.class, String.class, Map.class, float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            runtime = constructor.newInstance(owner, bodyGeometry, armGeometry, null,
                    "fixture", false, "NEW", "bone-query-fixture", Map.of(), 1f, 1f, true);
            install(owner, "ysmRuntime", runtime);
            engine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
        }

        private Object eval(String source) throws Exception {
            return ExpressionEvaluator.evaluator(context).evalAll(engine.parse(source), true);
        }

        private float number(String source) throws Exception {
            return ValueConversions.asFloat(eval(source));
        }
    }

    private static YsmGeometry geometry(boolean arms) throws Exception {
        String rotation = arms ? "[40,50,60]" : "[10,20,30]";
        String extra = arms ? "" : ",{\"name\":\"body_only\"}";
        return YsmGeometryParser.parse("{\"minecraft:geometry\":[{\"description\":{},\"bones\":["
                + "{\"name\":\"root\",\"pivot\":[16,20,30],\"rotation\":" + rotation + "}" + extra + "]}]}");
    }

    private static void install(Avatar owner, String name, Object value) throws ReflectiveOperationException {
        Field field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
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
