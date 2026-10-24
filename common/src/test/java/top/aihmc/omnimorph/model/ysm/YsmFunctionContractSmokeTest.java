package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.CameraType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Abilities;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlRuntime;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.model.ysm.action.YsmActionDefinition;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import top.aihmc.omnimorph.molang.runtime.binding.ValueConversions;
import top.aihmc.omnimorph.molang.runtime.binding.MolangBindings;
import top.aihmc.omnimorph.molang.runtime.Variable;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class YsmFunctionContractSmokeTest {
    public static void main(String[] args) throws Exception {
        Path environment = Files.createTempDirectory("figura-functions-environment-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", environment.toString());
        try {
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
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        testNamespaceAliases(first, second);
        testPerlinEntityContract();
        testTimeQueryContracts();
        testPositionQueryContracts();
        testControllerConstants();
        testControllerFunctionContracts();
        testControllerReloadHint();
        testAnimationCompletionQueries();
        testProjectEventAliases();
        testBindingDiagnostics();
        testTextureName();
        testCameraQueries();
        testRenderPreviewQueries();
        testHeadQueryContracts();
        testProjectScalarExtensions();
        testProjectControlQueries();
        testProjectActionQueries();
        testLocalHandQueryPulses();
        testCapeFlapQuery();
        testLocalYawSpeed();
        testShieldQuery();
        testFlyingQuery();
        testJumpingQuery();
        testPlayerFoodQuery();
        testOptionalModDefaults();
        testDebugOutputContracts();
        testFilterPreviewDomain();
        testControllerExecutionDiagnostics();
        first.eval("t.root=71;");
        number(71f, first.eval("t.root;"), "named temp binding supports assignment");
        number(7f, first.eval("fn.sum(3,4);"), "formal load/call numeric arguments");
        number(71f, first.eval("t.root;"), "caller temp survives a function scope");
        number(37f, first.eval("fn.nested(3,4);"), "nested function result and restored parent local");
        number(36f, first.eval("fn.parent(3);"), "child same-named local cannot overwrite parent local");
        number(3f, first.eval("fn.early(2);"), "multi-statement early return");
        require(first.context.variables.getPath("after_return") == null, "statements after return do not run");
        require("模型文字".equals(ValueConversions.asString(first.eval("fn.identity('模型文字');"))),
                "reference StringExpression/string round trip");
        require(Boolean.FALSE.equals(first.eval("fn.identity(ysm.mouse(0));")), "typed Boolean argument/return");
        require(Boolean.TRUE.equals(first.eval("fn.string_equal('模型文字');")), "typed string equality result");
        require(Boolean.FALSE.equals(first.eval("fn.string_equal('other');")), "typed unequal string result");
        number(0f, first.eval("fn.identity();"), "missing argument return normalizes to reference zero");
        number(7f, first.eval("fn.second(v.missing,7);"), "nullable argument must not reject other captured parameters");
        number(1f, first.eval("fn.count();"), "first model persistent variable");
        number(1f, second.eval("fn.count();"), "second model variable isolation");
        number(2f, first.eval("fn.count();"), "first model retained state");
        number(7f, first.eval("fn.recover();"), "function exception restores parent temp");
        require(first.runtime.functions().currentArguments().isEmpty(), "normal/error calls release argument stack");
        number(71f, first.eval("t.root;"), "exception restores outer temp");
        first.context.anim_time = 10f;
        first.context.controller.setPath("weight", 5f);
        second.context.anim_time = 20f;
        second.context.controller.setPath("weight", 2f);
        number(15f, first.eval("fn.context();"), "formal query/controller binding context");
        number(22f, second.eval("fn.context();"), "second model controller/query isolation");
        testFormalEvents(first, second);
        int scopes = 0;
        try {
            while (first.context.temp.pushScope(List.of())) {
                scopes++;
            }
            require(scopes == 32, "temporary scope depth is bounded");
            first.eval("t.saved=42;");
            try {
                first.eval("fn.child(3);");
                throw new AssertionError("function must reject an unavailable temporary scope");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("scope limit"), "explicit temporary scope failure");
            }
            number(42f, first.eval("t.saved;"), "rejected call cannot overwrite its caller temporary");
            require(first.runtime.functions().currentArguments().isEmpty(), "rejected scope does not push arguments");
        } finally {
            for (int i = 0; i < scopes; i++) {
                first.context.temp.popScope();
            }
        }
        number(7f, first.eval("fn.sum(3,4);"), "function recovery after scope depth rejection");
        number(71f, first.eval("t.root;"), "manual scope cleanup restores root temporary");
        try (var frame = first.context.executionBudget().beginFrame(100)) {
            try {
                first.eval("fn.endless();");
                throw new AssertionError("nested function loops must exhaust the shared budget");
            } catch (MolangExecutionBudget.Exceeded expected) {
                require(first.runtime.functions().currentArguments().isEmpty(), "budget failure releases arguments");
            }
        }
        try (var frame = first.context.executionBudget().beginFrame(101)) {
            number(7f, first.eval("fn.sum(3,4);"), "next-frame recovery after budget exhaustion");
            number(71f, first.eval("t.root;"), "budget failure restores outer temp state");
        }
        System.out.println("M4 formal function loading/Avatar bindings: typed arguments/returns, temp/string/context isolation and error/budget recovery passed.");
    }

    private static void testFormalEvents(Fixture first, Fixture second) throws Exception {
        Object animationOwner = new Object();
        first.eval("ysm.sync(0,1); ysm.defer('layer',9,7);");
        require(first.context.variables.getPath("sync_count") == null, "ordinary sampling suppresses sync");
        first.runtime.functions().flushDeferred(animationOwner, ExpressionEvaluator.evaluator(first.context));
        require(first.context.variables.getPath("order") == null, "ordinary sampling suppresses defer");
        try (var effects = first.runtime.functions().beginClientEffects(animationOwner)) {
            first.eval("ysm.sync(0,0);");
            number(0f, first.eval("v.horn;"), "corpus numeric sync event applies immediately");
            first.eval("ysm.sync(0,1); ysm.defer('layer',1,7); ysm.defer('layer',2,8);");
        }
        number(1f, first.eval("v.horn;"), "second corpus numeric sync event updates state");
        number(2f, first.eval("v.sync_count;"), "formal event files receive both sync calls");
        require(second.context.variables.getPath("sync_count") == null, "formal event state stays in its Avatar");
        first.runtime.functions().flushDeferred(animationOwner, ExpressionEvaluator.evaluator(first.context));
        number(21f, first.eval("v.order;"), "formally loaded defer handler replays in reverse capture order");
        number(7f, first.eval("v.payload;"), "defer captures typed secondary arguments");
        first.runtime.functions().flushDeferred(animationOwner, ExpressionEvaluator.evaluator(first.context));
        number(21f, first.eval("v.order;"), "formal deferred calls run once");
        try (var effects = first.runtime.functions().beginClientEffects(animationOwner)) {
            first.eval("ysm.defer('layer',v.missing,9);");
        }
        first.runtime.functions().flushDeferred(animationOwner, ExpressionEvaluator.evaluator(first.context));
        number(210f, first.eval("v.order;"), "nullable deferred capture retains a valid argument list");
        number(9f, first.eval("v.payload;"), "nullable first payload preserves subsequent payload");
        number(71f, first.eval("t.root;"), "event scope restores calling temporary storage");
        require(first.runtime.functions().currentArguments().isEmpty(), "formal events release captured arguments");
    }

    private static void testBindingDiagnostics() throws Exception {
        Fixture plain = new Fixture(false);
        Fixture encrypted = new Fixture(true);
        plain.context.controller.setPath("controller", "player_ctrl_main");
        plain.context.controller.setPath("current_state", "walking");
        String location = ValueConversions.asString(plain.eval("fn.diagnostic();"));
        require(location.contains("model=function-contract-fixture")
                        && location.contains("resource=functions/diagnostic.molang")
                        && location.contains("function=diagnostic")
                        && location.contains("controller=player_ctrl_main")
                        && location.contains("state=walking"),
                "real function execution supplies resource/function/controller/state diagnosis");
        require("runtime/unsupported-binding".equals(ValueConversions.asString(encrypted.eval("fn.diagnostic();"))),
                "encrypted diagnostic context does not expose resource/function/model names");
        for (Fixture fixture : List.of(plain, encrypted)) {
            try {
                fixture.eval("ysm.not_registered(v.diagnostic_effect=1);");
                throw new AssertionError("unknown binding must fail explicitly");
            } catch (UnsupportedOperationException expected) {
                if (fixture.runtime.encryptedSource()) {
                    require("Encrypted YSM operation failed: runtime/unsupported-binding".equals(expected.getMessage())
                                    && expected.getCause() == null,
                            "encrypted unknown binding error is fixed and has no cause");
                } else {
                    require(expected.getMessage().contains("ysm.not_registered")
                                    && expected.getMessage().contains("model=function-contract-fixture")
                                    && expected.getMessage().contains("state=walking"),
                            "unknown binding includes plain model and state");
                }
            }
            require(fixture.context.variables.getPath("diagnostic_effect") == null,
                    "unknown binding rejects before argument effects");
            number(7f, fixture.eval("fn.sum(3,4);"), "unknown binding leaves subsequent function healthy");
        }
    }

    private static void testTextureName() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        require(first.evalValue("ysm.texture_name;") == null, "texture_name rejects a missing player context");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        RemotePlayer player = (RemotePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(RemotePlayer.class);
        first.context.entity = player;
        second.context.entity = player;
        require("fixture".equals(first.evalValue("ysm.texture_name;")), "texture_name returns current runtime ID as a raw string");
        Field texture = YsmModelRuntime.class.getDeclaredField("textureId");
        texture.setAccessible(true);
        // Mutate only the CPU runtime value; actual texture upload/switching belongs to the texture milestone.
        texture.set(first.runtime, "skin_pink");
        require(Boolean.TRUE.equals(first.eval("ysm.texture_name=='skin_pink';")), "corpus texture-name comparison");
        require(Boolean.TRUE.equals(first.eval("'skin_pink'==ysm.texture_name;")), "reverse texture-name comparison");
        require(Boolean.FALSE.equals(first.eval("ysm.texture_name!='skin_pink';")), "texture-name inequality");
        require("fixture".equals(second.evalValue("ysm.texture_name;")), "same entity with another Avatar has independent texture selection");
        first.context.entity = null;
        require(first.evalValue("ysm.texture_name;") == null, "removed player context does not expose stale selection");
    }

    private static void testCameraQueries() throws Exception {
        Fixture local = new Fixture();
        Fixture observer = new Fixture();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        RemotePlayer player = (RemotePlayer) unsafe.allocateInstance(RemotePlayer.class);
        RemotePlayer other = (RemotePlayer) unsafe.allocateInstance(RemotePlayer.class);
        local.context.entity = player;
        observer.context.entity = other;
        for (CameraType cameraType : CameraType.values()) {
            local.context.setYsmCameraContext(player, player, player, cameraType, true);
            require(Integer.valueOf(cameraType.ordinal()).equals(local.evalValue("ysm.person_view;")),
                    "local camera query preserves all three native ordinals");
            require(Boolean.valueOf(cameraType == CameraType.FIRST_PERSON).equals(local.evalValue("q.is_first_person;")),
                    "query first-person preserves raw Boolean camera meaning");
            require(Boolean.valueOf(cameraType == CameraType.FIRST_PERSON)
                            .equals(local.evalValue("ysm.first_person_mod_hide;")),
                    "local hide query follows camera mode");
        }
        local.context.is_first_person = 0f;
        local.context.setYsmCameraContext(player, player, player, CameraType.FIRST_PERSON, true);
        require(Boolean.TRUE.equals(local.evalValue("ysm.first_person_mod_hide;")),
                "full body first-person view does not require the arm-pass flag");
        require(Boolean.TRUE.equals(local.evalValue("q.is_first_person;")), "body pass reads local camera mode");
        observer.context.is_first_person = 1f;
        observer.context.setYsmCameraContext(other, player, player, CameraType.FIRST_PERSON, true);
        require(Boolean.FALSE.equals(observer.evalValue("ysm.first_person_mod_hide;")),
                "observer remains visible even with an arm-pass flag");
        require(Integer.valueOf(2).equals(observer.evalValue("ysm.person_view;")), "observer uses reference front-view fallback");
        require(Boolean.FALSE.equals(observer.evalValue("q.is_first_person;")), "observer arm flag cannot override camera scope");
        local.context.setYsmCameraContext(player, player, other, CameraType.FIRST_PERSON, true);
        require(Boolean.FALSE.equals(local.evalValue("ysm.first_person_mod_hide;")), "detached camera rejects local-player hiding");
        for (EntityRenderMode mode : EntityRenderMode.values()) {
            boolean preview = mode == EntityRenderMode.FIGURA_GUI || mode == EntityRenderMode.PAPERDOLL
                    || mode == EntityRenderMode.MINECRAFT_GUI;
            local.context.setYsmCameraContext(player, player, player, CameraType.FIRST_PERSON,
                    !YsmParticleRuntime.isPreview(mode));
            require(Boolean.valueOf(!preview).equals(local.evalValue("ysm.first_person_mod_hide;")),
                    "world/arm/GUI camera classification: " + mode);
            require(Boolean.valueOf(!preview).equals(local.evalValue("q.is_first_person;")),
                    "query camera uses the same world/preview classification: " + mode);
        }
        local.context.entity = null;
        require(local.evalValue("ysm.first_person_mod_hide;") == null && local.evalValue("ysm.person_view;") == null,
                "camera bindings enforce the reference entity gate");
        require(local.evalValue("q.is_first_person;") == null, "query first-person requires an Entity");
        local.context.reset();
        require(local.context.ysm_camera_type == 2, "reset clears local first-person camera state");
    }

    private static void testShieldQuery() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        require(first.evalValue("ysm.in_shield_block_cooldown;") == null, "shield query enforces Player gate");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        FixturePlayer player = (FixturePlayer) unsafe.allocateInstance(FixturePlayer.class);
        FixturePlayer other = (FixturePlayer) unsafe.allocateInstance(FixturePlayer.class);
        first.context.entity = player;
        second.context.entity = other;
        require(Boolean.FALSE.equals(first.evalValue("ysm.in_shield_block_cooldown;")), "not blocking returns Boolean false");
        player.blocking = true;
        require(Boolean.TRUE.equals(first.evalValue("ysm.in_shield_block_cooldown;")), "legacy query reads current isBlocking state");
        require(Boolean.FALSE.equals(second.evalValue("ysm.in_shield_block_cooldown;")), "another Player/Avatar has independent blocking input");
        player.blocking = false;
        require(Boolean.FALSE.equals(first.evalValue("ysm.in_shield_block_cooldown;")), "release is immediate without invented cooldown");
        Avatar.MolangContext unowned = new Avatar.MolangContext(null);
        unowned.entity = player;
        player.blocking = true;
        MolangEngine engine = MolangEngine.fromCustomBinding(Avatar.createMolangBindings(unowned));
        require(Boolean.FALSE.equals(ExpressionEvaluator.evaluator(unowned).eval(engine.parse("ysm.in_shield_block_cooldown;").getFirst())),
                "missing player-model capability uses the reference false fallback");
    }

    private static void testRenderPreviewQueries() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var entity = (net.minecraft.world.entity.decoration.ArmorStand) ((Unsafe) unsafeField.get(null))
                .allocateInstance(net.minecraft.world.entity.decoration.ArmorStand.class);
        first.context.entity = entity;
        second.context.entity = entity;
        first.context.rendering_in_inventory = -9f;
        first.context.rendering_in_paperdoll = -9f;
        for (EntityRenderMode mode : EntityRenderMode.values()) {
            first.context.owner.renderMode = mode;
            boolean modelPreview = mode == EntityRenderMode.FIGURA_GUI || mode == EntityRenderMode.MINECRAFT_GUI
                    || mode == EntityRenderMode.PAPERDOLL;
            boolean guiPreview = mode == EntityRenderMode.MINECRAFT_GUI || mode == EntityRenderMode.PAPERDOLL;
            require(Boolean.valueOf(modelPreview).equals(first.evalValue("ysm.rendering_in_inventory;")),
                    "YSM model-preview query raw Boolean mapping: " + mode);
            require(Boolean.valueOf(guiPreview).equals(first.evalValue("ysm.rendering_in_paperdoll;")),
                    "YSM GUI-preview query raw Boolean mapping: " + mode);
            require(Float.valueOf(modelPreview ? 1f : 0f).equals(first.evalValue("q.rendering_in_inventory;"))
                            && Float.valueOf(guiPreview ? 1f : 0f).equals(first.evalValue("q.rendering_in_paperdoll;")),
                    "project query aliases preserve Float type while reading actual native preview mode: " + mode);
        }
        first.context.owner.renderMode = EntityRenderMode.PAPERDOLL;
        second.context.owner.renderMode = EntityRenderMode.RENDER;
        require(Boolean.TRUE.equals(first.evalValue("ysm.rendering_in_paperdoll;"))
                        && Boolean.FALSE.equals(second.evalValue("ysm.rendering_in_paperdoll;")),
                "two Avatar previews remain isolated even with the same entity");
        require(Float.valueOf(0f).equals(first.evalValue("q.rotation_to_camera(0);"))
                        && Float.valueOf(0f).equals(first.evalValue("q.rotation_to_camera(1);")),
                "HUD doll matches reference OLD_HUD zero camera rotation before global camera access");
        require(first.evalValue("q.rotation_to_camera(2);") == null,
                "invalid camera axis remains null even during a HUD doll pass");
        first.context.owner.renderMode = EntityRenderMode.FIGURA_GUI;
        require(Boolean.TRUE.equals(first.evalValue("ysm.rendering_in_inventory;"))
                        && Boolean.FALSE.equals(first.evalValue("ysm.rendering_in_paperdoll;")),
                "model picker preview is distinct from GUI entity/HUD preview");
        first.context.owner.renderMode = EntityRenderMode.RENDER;
        require(Boolean.FALSE.equals(first.evalValue("ysm.rendering_in_inventory;"))
                        && Boolean.FALSE.equals(first.evalValue("ysm.rendering_in_paperdoll;")),
                "return to world rendering clears preview query values without cached GUI state");
        first.context.entity = null;
        require(first.evalValue("ysm.rendering_in_inventory;") == null
                        && first.evalValue("ysm.rendering_in_paperdoll;") == null, "reference YSM preview variables require Entity");
        install(first.context.owner, "ysmRuntime", null);
        first.context.rendering_in_inventory = 0.25f;
        first.context.rendering_in_paperdoll = 0.75f;
        require(Float.valueOf(0.25f).equals(first.evalValue("q.rendering_in_inventory;"))
                        && Float.valueOf(0.75f).equals(first.evalValue("q.rendering_in_paperdoll;")),
                "original Figura query fields retain their preexisting scalar behavior");
    }

    private static void testLocalYawSpeed() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var player = (HandFixturePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(HandFixturePlayer.class);
        Field position = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
        position.setAccessible(true);
        position.set(player, net.minecraft.world.phys.Vec3.ZERO);
        player.tickCount = 10;
        player.setYRot(170f);
        first.context.updateYsmPositionContext(player);
        number(0f, first.evalValue("q.yaw_speed;"), "local yaw begins with a zero-rate baseline");
        player.tickCount = 12;
        player.setYRot(-170f);
        first.context.updateYsmPositionContext(player);
        number(200f, first.evalValue("q.yaw_speed;"), "local yaw wraps angles and divides by skipped ticks");
        player.setYRot(160f);
        first.context.updateYsmPositionContext(player);
        number(200f, first.evalValue("q.yaw_speed;"), "repeated passes in the same tick retain the sampled rate");
        player.tickCount = 13;
        first.context.updateYsmPositionContext(player);
        number(-600f, first.evalValue("q.yaw_speed;"), "next tick uses the previous tick sample rather than a repeated pass");
        second.context.updateYsmPositionContext(player);
        number(0f, second.evalValue("q.yaw_speed;"), "another Avatar has its own initial yaw baseline");
        number(-600f, first.evalValue("q.yaw_speed;"), "another Avatar cannot mutate the first yaw rate");
        player.tickCount = 5;
        player.setYRot(0f);
        first.context.updateYsmPositionContext(player);
        number(0f, first.evalValue("q.yaw_speed;"), "backward tick resets the local yaw baseline");
        player.tickCount = 0;
        first.context.updateYsmPositionContext(player);
        player.tickCount = 1;
        player.setYRot(30f);
        first.context.updateYsmPositionContext(player);
        number(0f, first.evalValue("q.yaw_speed;"), "reference previous tick zero does not produce a rate");
        player.tickCount = 2;
        player.setYRot(40f);
        first.context.updateYsmPositionContext(player);
        number(200f, first.evalValue("q.yaw_speed;"), "valid subsequent tick resumes yaw-rate calculation");
        first.context.reset();
        first.context.updateYsmPositionContext(player);
        number(0f, first.evalValue("q.yaw_speed;"), "context reset clears local yaw sampling state");
        first.context.updateYsmPositionContext(null);
        require(first.evalValue("q.yaw_speed;") == null, "removed actor cannot leak the previous yaw rate");
    }

    private static void testCapeFlapQuery() throws Exception {
        Fixture fixture = new Fixture();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var player = (HandFixturePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(HandFixturePlayer.class);
        Field position = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
        position.setAccessible(true);
        position.set(player, net.minecraft.world.phys.Vec3.ZERO);
        fixture.context.entity = player;
        fixture.context.cape_flap_amount = -99f;
        number(6f / 108f, fixture.evalValue("q.cape_flap_amount;"), "reference neutral cloak scalar ignores stale context");
        player.crouching = true;
        number(31f / 108f, fixture.evalValue("q.cape_flap_amount;"), "reference crouch contributes twenty-five units");
        player.crouching = false;
        player.xo = -3d;
        position.set(player, new net.minecraft.world.phys.Vec3(-1d, 0d, 0d));
        player.yBodyRotO = -90f;
        player.yBodyRot = 90f;
        fixture.context.ysm_partial_tick = 0.25f;
        number(0.75f, fixture.evalValue("q.cape_flap_amount;"), "position interpolation and reference forward clamp");
        fixture.context.ysm_partial_tick = 1f;
        number(56f / 108f, fixture.evalValue("q.cape_flap_amount;"), "current position uses native current body yaw");
        player.yBodyRot = 270f;
        number(6f / 108f, fixture.evalValue("q.cape_flap_amount;"), "opposite yaw clamps negative forward contribution");
        player.xo = 0d;
        player.yo = -4d;
        player.zo = 0d;
        player.yBodyRot = 0f;
        position.set(player, new net.minecraft.world.phys.Vec3(0d, -4d, 4d));
        fixture.context.ysm_partial_tick = 0.5f;
        number(1f, fixture.evalValue("q.cape_flap_amount;"), "vertical and forward upper clamps produce unit maximum");
        player.yo = 2d;
        player.zo = -4d;
        position.set(player, new net.minecraft.world.phys.Vec3(0d, 2d, -4d));
        number(0f, fixture.evalValue("q.cape_flap_amount;"), "negative vertical and forward clamp produce zero");
        Fixture other = new Fixture();
        require(other.evalValue("q.cape_flap_amount;") == null, "cape flap state is not shared across Avatars");
    }

    private static void testLocalHandQueryPulses() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        var player = (HandFixturePlayer) unsafe.allocateInstance(HandFixturePlayer.class);
        player.mainItem = ItemStack.EMPTY;
        player.offItem = ItemStack.EMPTY;
        player.setUUID(java.util.UUID.randomUUID());
        var client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        client.player = player;
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousClient = instance.get(null);
        var hands = YsmInputState.device().hands();
        try {
            instance.set(null, client);
            Fixture owner = new Fixture();
            Fixture observer = new Fixture();
            install(owner.context.owner, "isHost", true);
            owner.context.owner.renderMode = EntityRenderMode.RENDER;
            owner.context.entity = player;
            observer.context.entity = player;
            owner.context.ysm_partial_tick = 0.25f;
            require("player".equals(owner.evalValue("ysm.entity_type;"))
                            && Boolean.TRUE.equals(owner.evalValue("ysm.is_player;")),
                    "native Player identity uses reference string and Boolean types");
            number(1f, owner.evalValue("ysm.local_player;"), "local-player project alias reads matching UUID");
            number(1f, owner.evalValue("ysm.is_local_player;"), "both local-player project aliases agree");
            var remote = (HandFixturePlayer) unsafe.allocateInstance(HandFixturePlayer.class);
            remote.setUUID(java.util.UUID.randomUUID());
            observer.context.entity = remote;
            number(0f, observer.evalValue("ysm.local_player;"), "different player UUID is not local");
            observer.context.entity = player;
            number(1f, observer.evalValue("ysm.local_player;"), "local identity is independent of model ownership");
            client.player = null;
            number(0f, owner.evalValue("ysm.is_local_player;"), "disconnected client has no local player");
            client.player = player;
            player.experienceLevel = 17;
            owner.context.player_level = -1;
            require(Integer.valueOf(17).equals(owner.evalValue("q.player_level;")),
                    "player level reads native experience with raw Integer type");
            player.experienceLevel = 3;
            require(Integer.valueOf(3).equals(owner.evalValue("q.player_level;")), "experience changes immediately");
            player.fixtureSkin = new net.minecraft.world.entity.player.PlayerSkin(null, null, null,
                    net.minecraft.world.entity.player.PlayerModelType.WIDE, false);
            require(Boolean.FALSE.equals(owner.evalValue("q.has_cape;")), "missing cape texture is false");
            player.fixtureSkin = new net.minecraft.world.entity.player.PlayerSkin(null,
                    new net.minecraft.core.ClientAsset.ResourceTexture(
                            net.minecraft.resources.Identifier.parse("fixture:cape")), null,
                    net.minecraft.world.entity.player.PlayerModelType.WIDE, false);
            player.showCape = true;
            require(Boolean.TRUE.equals(owner.evalValue("q.has_cape;")), "visible native cape with model part enabled");
            player.hidden = true;
            require(Boolean.FALSE.equals(owner.evalValue("q.has_cape;")), "invisible player cape is hidden");
            player.hidden = false;
            player.showCape = false;
            require(Boolean.FALSE.equals(owner.evalValue("q.has_cape;")), "cape model-part setting is respected");
            player.showCape = true;
            require(Boolean.TRUE.equals(owner.evalValue("q.has_cape;")), "cape visibility changes without stale context fields");
            hands.prepare(player, true);
            hands.mouseEvent(0, 1);
            require(Boolean.TRUE.equals(owner.evalValue("q.is_swinging;")), "owned model sees its device swing pulse");
            require(Boolean.FALSE.equals(observer.evalValue("q.is_swinging;")), "another Avatar cannot borrow the device pulse");
            number(1f, owner.evalValue("ysm.swing_time;"), "initial local pulse has one tick minimum");
            number(1f / 6f, owner.evalValue("q.attack_time;"), "local main-hand pulse supplies normalized attack progress");
            hands.tick(false);
            require(Double.valueOf(0.0625d).equals(owner.evalValue("q.swing_time;")), "local pulse age plus partial tick converts to seconds");
            number(1.25f, owner.evalValue("ysm.swing_time;"), "YSM local swing query retains tick units");
            owner.context.owner.renderMode = EntityRenderMode.FIGURA_GUI;
            require(Boolean.FALSE.equals(owner.evalValue("q.is_swinging;")), "preview cannot consume local device pulses");
            owner.context.owner.renderMode = EntityRenderMode.RENDER;
            player.sleeping = true;
            require(Boolean.TRUE.equals(owner.evalValue("q.is_swinging;")), "owned-model pulse retains reference override before native sleep predicate");
            hands.prepare(player, false);
            require(Boolean.FALSE.equals(owner.evalValue("q.is_swinging;")), "focus loss clears local pulse and native sleep remains false");
            player.sleeping = false;
            hands.prepare(player, true);
            var consumable = Consumable.builder().consumeSeconds(2f)
                    .animation(net.minecraft.world.item.ItemUseAnimation.EAT).build();
            Items.APPLE.builtInRegistryHolder().bindComponents(
                    DataComponentMap.builder()
                            .set(DataComponents.CONSUMABLE, consumable).build());
            player.mainItem = new ItemStack(Items.APPLE);
            hands.mouseEvent(1, 1);
            require(Boolean.TRUE.equals(owner.evalValue("q.is_using_item;")), "local pending use pulse bridges native item handoff");
            require(Boolean.FALSE.equals(observer.evalValue("q.is_using_item;")), "pending use belongs only to the local model owner");
            require(Double.valueOf(0.05d).equals(owner.evalValue("q.item_in_use_duration;")), "pending use minimum tick converts to seconds");
            require(Double.valueOf(0.25d).equals(owner.evalValue("q.item_remaining_use_duration;")), "pending remaining duration follows reference six-tick fallback");
            hands.tick(false);
            require(Boolean.TRUE.equals(owner.evalValue("q.is_using_item;")), "first pending-use tick stays active");
            hands.tick(false);
            require(Boolean.FALSE.equals(owner.evalValue("q.is_using_item;")), "pending-use pulse expires after its two-tick window");
            hands.mouseEvent(1, 1);
            hands.mouseEvent(1, 0);
            require(Boolean.FALSE.equals(owner.evalValue("q.is_using_item;")), "mouse release cancels pending use immediately");
            hands.mouseEvent(0, 1);
            for (int tick = 0; tick < 10; tick++) {
                hands.tick(false);
            }
            require(Boolean.FALSE.equals(owner.evalValue("q.is_swinging;")), "swing pulse expires without stale query state");
        } finally {
            hands.prepare(null, false);
            instance.set(null, previousClient);
        }
    }

    private static final class HandFixturePlayer extends LocalPlayer {
        private net.minecraft.world.entity.player.PlayerSkin fixtureSkin;
        private boolean showCape;
        private boolean hidden;
        private ItemStack mainItem;
        private ItemStack offItem;
        private boolean sleeping;
        private boolean crouching;

        @Override
        public boolean isCrouching() {
            return crouching;
        }

        @Override
        public net.minecraft.world.entity.player.PlayerSkin getSkin() {
            return fixtureSkin;
        }

        @Override
        public boolean isInvisible() {
            return hidden;
        }

        @Override
        public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part) {
            return part == net.minecraft.world.entity.player.PlayerModelPart.CAPE && showCape;
        }

        private HandFixturePlayer() {
            super(null, null, null, null, null, null, false, null);
        }

        @Override
        public boolean isSleeping() {
            return sleeping;
        }

        @Override
        public boolean isUsingItem() {
            return false;
        }

        @Override
        public ItemStack getUseItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack getItemBySlot(EquipmentSlot slot) {
            return switch (slot) {
                case MAINHAND -> mainItem;
                case OFFHAND -> offItem;
                default -> ItemStack.EMPTY;
            };
        }

        @Override
        public float getAttackAnim(float partialTick) {
            return 0f;
        }
    }

    private static void testProjectControlQueries() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var numberControl = new AvatarControlDefinition("fixture_number", AvatarControlType.NUMBER)
                .setMin(-10).setMax(10).setDefault(1.25d);
        var toggle = new AvatarControlDefinition("fixture_toggle", AvatarControlType.TOGGLE).setDefault(true);
        var choice = new AvatarControlDefinition("fixture_choice", AvatarControlType.ENUM)
                .setOptions(List.of("red", "blue", "模型")).setDefault("blue");
        var text = new AvatarControlDefinition("fixture_text", AvatarControlType.TEXT).setDefault("2.75");
        first.context.owner.controls.register(numberControl);
        first.context.owner.controls.register(toggle);
        first.context.owner.controls.register(choice);
        first.context.owner.controls.register(text);
        var registry = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                first.context.owner.getAvatarBindings().getProperty("query");
        for (String name : List.of("control", "ysm_control", "avatar_control", "ysm_control_number",
                "ysm_control_bool", "ysm_control_enum")) {
            Function function = (Function) registry.getProperty(name);
            require(function.validateArgumentSize(0) && function.validateArgumentSize(1)
                            && function.validateArgumentSize(2), "legacy control query has a permissive signature: " + name);
            number(0f, first.evalValue("q." + name + "();"), "control query zero-argument fallback: " + name);
        }
        List<String> numericAliases = List.of("control", "ysm_control", "avatar_control", "ysm_control_number");
        for (String name : numericAliases) {
            number(1.25f, first.evalValue("q." + name + "('fixture_number');"), "control numeric aliases: " + name);
            number(1f, first.evalValue("q." + name + "('fixture_toggle');"), "control Boolean conversion: " + name);
            number(2.75f, first.evalValue("q." + name + "('fixture_text');"), "control numeric string conversion: " + name);
            number(0f, first.evalValue("q." + name + "();"), "legacy control missing argument fallback: " + name);
            number(0f, first.evalValue("q." + name + "('absent');"), "legacy unknown control fallback: " + name);
            number(0f, second.evalValue("q." + name + "('fixture_number');"), "control belongs to its Avatar: " + name);
        }
        numberControl.setValue(-2.5d);
        toggle.setValue(false);
        choice.setValue("模型");
        number(-2.5f, first.evalValue("q.control('fixture_number',v.extra_control_argument=1);"),
                "control query reads changed definition and ignores unused extra arguments");
        require(first.context.variables.getPath("extra_control_argument") == null, "unused control argument has no effects");
        number(0f, first.evalValue("q.ysm_control_bool('fixture_toggle');"), "control bool changes to Float zero");
        number(1f, first.evalValue("q.ysm_control_bool('fixture_number');"), "negative number converts to Float true");
        number(2f, first.evalValue("q.ysm_control_enum('fixture_choice');"), "enum query returns changed option index");
        number(0f, first.evalValue("q.ysm_control_enum('absent');"), "missing enum fallback");
        number(-2.5f, first.evalValue("q.ysm_control_enum('fixture_number');"), "non-enum control retains numeric fallback");
        text.setValue("not numeric");
        number(0f, first.evalValue("q.control('fixture_text');"), "non-numeric control text normalizes to zero");

        first.context.controller.setPath("fixture_dynamic", 3f);
        first.context.variables.setPath("fixture_dynamic", -99f);
        number(3f, first.evalValue("ctrl.fixture_dynamic;"), "legacy dynamic ctrl reads its c scope");
        first.context.controller.setPath("fixture_dynamic", 7f);
        number(7f, first.evalValue("ctrl.fixture_dynamic;"), "dynamic ctrl reflects changed controller scope");
        number(7f, first.evalValue("ctrl.fixture_dynamic();"), "zero-argument dynamic ctrl reads the same scope");
        first.context.controller.setPath("fixture_dynamic_bool", true);
        first.context.controller.setPath("fixture_dynamic_text", "模型");
        require(Boolean.TRUE.equals(first.evalValue("ctrl.fixture_dynamic_bool;")), "dynamic ctrl preserves raw Boolean");
        require(Boolean.TRUE.equals(first.evalValue("ctrl.fixture_dynamic_text=='模型';")),
                "dynamic ctrl preserves a string for formal Molang comparison");
        number(0f, second.evalValue("ctrl.fixture_dynamic;"), "dynamic controller scope is Avatar-local");
        number(0f, first.evalValue("ctrl.fixture_dynamic(v.dynamic_argument_effect=1);"),
                "legacy callable fallback remains a no-op");
        require(first.context.variables.getPath("dynamic_argument_effect") == null,
                "legacy callable fallback does not evaluate ignored arguments");
        Avatar.MolangContext ownerless = new Avatar.MolangContext(null);
        var engine = MolangEngine.fromCustomBinding(Avatar.createMolangBindings(ownerless));
        Object result = ExpressionEvaluator.evaluator(ownerless).eval(
                engine.parse("q.control(v.no_control_owner=1);").getFirst());
        number(0f, result, "missing control owner returns zero");
        require(ownerless.variables.getPath("no_control_owner") == null, "missing owner rejects before evaluating control ID");
    }

    private static void testProjectActionQueries() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var clip = new YsmAnimationClip("animation.fixture_action", 2f, YsmAnimationClip.LoopMode.ONCE);
        first.runtime.animations().registerAnimations(Map.of(clip.name, clip));
        first.runtime.actions().register(new YsmActionDefinition("fixture_action").setAnimation(clip.name));
        number(0f, first.evalValue("q.ysm_action_active('fixture_action');"), "registered action starts inactive");
        require(Boolean.FALSE.equals(first.evalValue("ctrl.playing_extra_animation;")), "extra-animation predicate starts as raw Boolean false");
        require(first.runtime.actions().trigger("fixture_action"), "registered action starts through real action runtime");
        require(Boolean.TRUE.equals(first.evalValue("ctrl.playing_extra_animation;"))
                        && Boolean.FALSE.equals(second.evalValue("ctrl.playing_extra_animation;")),
                "extra-animation predicate tracks the real action playback and stays Avatar-local");
        number(1f, first.evalValue("q.ysm_action_active('fixture_action');"), "action query returns raw Float active state");
        var advance = first.runtime.animations().getClass().getDeclaredMethod("advanceAnimationTimeline",
                float.class, ExpressionEvaluator.class);
        advance.setAccessible(true);
        advance.invoke(first.runtime.animations(), 0.375f, ExpressionEvaluator.evaluator(first.context));
        number(0.375f, first.evalValue("q.ysm_action_time('fixture_action');"), "action time reads actual advanced playback");
        number(0f, second.evalValue("q.ysm_action_active('fixture_action');"), "same named action is Avatar-local");
        number(0f, second.evalValue("q.ysm_action_time('fixture_action');"), "action time is Avatar-local");
        first.runtime.actions().stop("fixture_action");
        require(Boolean.FALSE.equals(first.evalValue("ctrl.playing_extra_animation;")), "stopping playback clears extra-animation state");
        number(0f, first.evalValue("q.ysm_action_active('fixture_action');"), "stopped action is inactive");
        number(0f, first.evalValue("q.ysm_action_time('fixture_action');"), "stopped action releases playback time");
        for (String name : List.of("ysm_action_active", "ysm_action_time")) {
            number(0f, first.evalValue("q." + name + "();"), "legacy action query missing argument fallback: " + name);
            number(0f, first.evalValue("q." + name + "('unknown');"), "unknown action fallback: " + name);
        }
    }

    private static void testHeadQueryContracts() throws Exception {
        Fixture fixture = new Fixture();
        Fixture second = new Fixture();
        var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
        var capture = fixture.runtime.animations().getClass().getDeclaredMethod("updateYsmHeadRotation",
                net.minecraft.client.renderer.entity.state.LivingEntityRenderState.class,
                net.minecraft.world.entity.LivingEntity.class);
        capture.setAccessible(true);
        state.yRot = 30f;
        state.xRot = 20f;
        capture.invoke(fixture.runtime.animations(), state, null);
        fixture.context.head_x_rot = 999f;
        fixture.context.head_y_rot = 999f;
        for (var entry : Map.of("q.head_x_rotation", -30f, "ysm.head_yaw", -30f,
                "q.head_y_rotation", -20f, "ysm.head_pitch", -20f).entrySet()) {
            require(entry.getValue().equals(fixture.evalValue(entry.getKey() + ";")),
                    "formal head query reads production captured YSM axes: " + entry.getKey());
            number(0f, second.evalValue(entry.getKey() + ";"), "head capture stays in its Avatar: " + entry.getKey());
        }
        state.yRot = 120f;
        state.xRot = -10f;
        capture.invoke(fixture.runtime.animations(), state, null);
        number(-85f, fixture.evalValue("q.head_x_rotation;"), "head yaw uses production clamp");
        number(10f, fixture.evalValue("q.head_y_rotation;"), "head pitch captures changed frame input");
        fixture.context.reset();
        for (String name : List.of("q.head_x_rotation", "q.head_y_rotation", "ysm.head_yaw", "ysm.head_pitch")) {
            number(0f, fixture.evalValue(name + ";"), "head capture clears on context reset: " + name);
        }
        Avatar.MolangContext legacy = new Avatar.MolangContext(null);
        legacy.head_x_rot = 11f;
        legacy.head_y_rot = -22f;
        legacy.ysm_head_yaw = 99f;
        legacy.ysm_head_pitch = 99f;
        var engine = MolangEngine.fromCustomBinding(Avatar.createMolangBindings(legacy));
        var evaluator = ExpressionEvaluator.evaluator(legacy);
        number(11f, evaluator.eval(engine.parse("q.head_x_rotation;").getFirst()), "ordinary Figura head x contract is preserved");
        number(-22f, evaluator.eval(engine.parse("q.head_y_rotation;").getFirst()), "ordinary Figura head y contract is preserved");
    }

    private static void testProjectScalarExtensions() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Map<String, Float> constants = Map.of("geometry_is_model", 1f, "geometry_is_entity", 1f,
                "geometry_is_block", 0f, "geometry_is_flat", 0f);
        for (var entry : constants.entrySet()) {
            String name = entry.getKey();
            first.context.variables.setPath(name, -99f);
            first.context.controller.setPath(name, -99f);
            require(entry.getValue().equals(first.evalValue("q." + name + ";")),
                    "project geometry scalar is a raw Float independent of model variables: " + name);
            require(entry.getValue().equals(second.evalValue("q." + name + ";")),
                    "project geometry scalar is independent of Avatar context: " + name);
        }
        for (float flag : new float[]{0f, 1f, 0f}) {
            first.context.is_first_person = flag;
            require(Float.valueOf(flag).equals(first.evalValue("ysm.is_first_person;"))
                            && Float.valueOf(flag).equals(first.evalValue("ysm.first_person;")),
                    "project arm-pass aliases preserve their raw Float flag");
            number(0f, second.evalValue("ysm.first_person;"), "project arm-pass aliases stay in their Avatar");
        }
        first.context.reset();
        number(0f, first.evalValue("ysm.first_person;"), "project arm-pass alias clears on reset");
    }

    private static void testPositionQueryContracts() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var registry = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                first.context.owner.getAvatarBindings().getProperty("query");
        for (String name : List.of("position", "position_delta")) {
            Function function = (Function) registry.getProperty(name);
            require(function.validateArgumentSize(1) && !function.validateArgumentSize(0)
                            && !function.validateArgumentSize(2), "position query requires one axis: " + name);
            require(first.evalValue("q." + name + "(v.missing_position_axis=1);") == null,
                    "position query requires an entity: " + name);
        }
        require(first.context.variables.getPath("missing_position_axis") == null,
                "missing entity does not evaluate the axis");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        RemotePlayer entity = (RemotePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(RemotePlayer.class);
        Field position = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
        position.setAccessible(true);
        position.set(entity, new net.minecraft.world.phys.Vec3(14.5, 28.5, -2.5));
        entity.xo = 10.5;
        entity.yo = 20.5;
        entity.zo = -10.5;
        first.context.entity = entity;
        first.context.ysm_partial_tick = 0.25f;
        first.context.pos_delta_x = 0.125;
        first.context.pos_delta_y = -0.25;
        first.context.pos_delta_z = 0.5000000001;
        double[] expectedPosition = {11.5, 22.5, -8.5};
        double[] expectedDelta = {0.125, -0.25, 0.5000000001};
        for (int axis = 0; axis < 3; axis++) {
            Object actual = first.evalValue("q.position(" + axis + ");");
            require(actual instanceof Double value && value == expectedPosition[axis],
                    "native old/current position interpolates as raw Double: " + axis);
            Object delta = first.evalValue("q.position_delta(" + axis + ");");
            require(delta instanceof Double value && value == expectedDelta[axis],
                    "captured position delta retains double precision: " + axis);
        }
        require(Double.valueOf(22.5).equals(first.evalValue("q.position(1.9);")),
                "fractional axis truncates to integer");
        for (String name : List.of("position", "position_delta")) {
            require(first.evalValue("q." + name + "(-1);") == null
                            && first.evalValue("q." + name + "(3);") == null,
                    "invalid position axis returns null: " + name);
            require(second.evalValue("q." + name + "(0);") == null,
                    "position context stays in its Avatar: " + name);
        }
        first.context.ysm_partial_tick = 1f;
        require(Double.valueOf(14.5).equals(first.evalValue("q.position(0);")),
                "position reads a changed frame fraction immediately");
        first.context.setYsmRenderTime(10.25f, 10);
        first.context.updateYsmPositionContext(entity);
        require(Double.valueOf(0d).equals(first.evalValue("q.position_delta(0);")),
                "first tracker sample does not measure from the world origin");
        first.context.setYsmRenderTime(10.5f, 10);
        first.context.updateYsmPositionContext(entity);
        number(0.25f, first.context.ysm_position_delta_ticks, "tracker captures seek interval in ticks");
        number(0.0125f, first.evalValue("q.delta_time;"), "captured query interval converts ticks to seconds");
        number(0.0125f, first.evalValue("ysm.time_delta;"), "YSM interval shares the same captured seconds");
        double[] trackerDelta = {1d, 2d, 2d};
        String[] componentAxes = {"x", "y", "z"};
        float[] rawPosition = {14.5f, 28.5f, -2.5f};
        for (int axis = 0; axis < 3; axis++) {
            require(Double.valueOf(trackerDelta[axis]).equals(first.evalValue("q.position_delta(" + axis + ");")),
                    "advancing seek samples interpolated position difference: " + axis);
            require(Float.valueOf(rawPosition[axis]).equals(first.evalValue("q.position_" + componentAxes[axis] + ";")),
                    "project position component preserves raw Float current coordinate: " + axis);
            require(Float.valueOf((float) trackerDelta[axis]).equals(
                            first.evalValue("q.position_delta_" + componentAxes[axis] + ";")),
                    "project delta component preserves Float tracker value: " + axis);
            number(0f, second.evalValue("q.position_" + componentAxes[axis] + ";"), "project component Avatar isolation");
        }
        first.context.updateYsmPositionContext(entity);
        require(Double.valueOf(1d).equals(first.evalValue("q.position_delta(0);")),
                "same-time body/hand sampling preserves the captured delta");
        number(0.25f, first.context.ysm_position_delta_ticks, "same-time pass retains tracker interval");
        first.context.setYsmRenderTime(10.25f, 10);
        first.context.updateYsmPositionContext(entity);
        require(Double.valueOf(1d).equals(first.evalValue("q.position_delta(0);")),
                "backward seek preserves the reference tracker sample");
        first.context.setYsmRenderTime(10.75f, 10);
        first.context.updateYsmPositionContext(entity);
        require(Double.valueOf(1d).equals(first.evalValue("q.position_delta(0);")),
                "forward seek resumes from the last advancing sample");
        RemotePlayer replacement = (RemotePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(RemotePlayer.class);
        position.set(replacement, new net.minecraft.world.phys.Vec3(10000d, 20000d, -30000d));
        replacement.xo = 10000d;
        replacement.yo = 20000d;
        replacement.zo = -30000d;
        first.context.updateYsmPositionContext(replacement);
        require(Double.valueOf(0d).equals(first.evalValue("q.position_delta(0);")),
                "entity replacement starts a fresh tracker without a teleport spike");
        first.context.updateYsmPositionContext(null);
        require(first.evalValue("q.position(0);") == null && first.evalValue("q.position_delta(0);") == null,
                "removed entity clears the position-query scope");
        first.context.reset();
        first.context.setYsmRenderTime(1.5f, 1);
        first.context.updateYsmPositionContext(entity);
        require(Double.valueOf(0d).equals(first.evalValue("q.position_delta(0);")),
                "reset restores the first-sample tracker behavior");
        first.context.setYsmRenderTime(1.50001f, 1);
        first.context.updateYsmPositionContext(entity);
        number(0f, first.evalValue("q.delta_time;"), "subthreshold seek interval follows reference zero seconds");
        number(0f, first.evalValue("ysm.time_delta;"), "YSM seconds uses the same minimum seek threshold");
        first.context.reset();
        require(first.evalValue("q.position(0);") == null && first.evalValue("q.position_delta(0);") == null,
                "reset removes stale positional values");
        for (String axis : componentAxes) {
            number(0f, first.evalValue("q.position_" + axis + ";"), "project position component reset");
            number(0f, first.evalValue("q.position_delta_" + axis + ";"), "project delta component reset");
        }
    }

    private static void testControllerReloadHint() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var bindings = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                first.context.owner.getAvatarBindings().getProperty("ctrl");
        Function reload = (Function) bindings.getProperty("indicate_reload");
        require(reload.validateArgumentSize(0) && !reload.validateArgumentSize(1), "reload hint requires exactly zero arguments");
        require(first.evalValue("ctrl.indicate_reload();") == null, "reload hint without a controller is a null no-op");
        try {
            first.engine.parse("ctrl.indicate_reload(v.invalid_reload=1);");
            throw new AssertionError("reload hint accepted an argument");
        } catch (top.aihmc.omnimorph.molang.parser.ParseException expected) {
            require(first.context.variables.getPath("invalid_reload") == null, "invalid reload signature has no argument effects");
        }
        var clip = new YsmAnimationClip("animation.reload_hint", 2f, YsmAnimationClip.LoopMode.LOOP);
        var player = first.runtime.animations();
        player.registerAnimations(Map.of(clip.name, clip));
        second.runtime.animations().registerAnimations(Map.of(clip.name, clip));
        first.context.controller.setPath("controller", "first");
        first.evalValue("ctrl.set_beginning_transition_length(0.25);");
        first.evalValue("ctrl.set_animation('animation.reload_hint');");
        var original = player.getControllerFunctionAnimation("first");
        original.time = 0.75f;
        first.evalValue("ctrl.set_animation('animation.reload_hint');");
        require(player.getControllerFunctionAnimation("first") == original && original.time == 0.75f,
                "ordinary repeated request retains the same playback instance and time");
        first.context.controller.setPath("controller", "other");
        first.evalValue("ctrl.set_animation('animation.reload_hint');");
        var other = player.getControllerFunctionAnimation("other");
        second.context.controller.setPath("controller", "first");
        second.evalValue("ctrl.set_animation('animation.reload_hint');");
        var secondOriginal = second.runtime.animations().getControllerFunctionAnimation("first");
        first.context.controller.setPath("controller", "first");
        require(first.evalValue("ctrl.indicate_reload();") == null, "reload hint returns raw null");
        require(player.getControllerFunctionAnimation("first") == original && original.time == 0.75f,
                "hint leaves current playback intact until the next animation request");
        first.evalValue("ctrl.set_animation('animation.reload_hint');");
        var replacement = player.getControllerFunctionAnimation("first");
        require(replacement != original && replacement.time == 0f && replacement.weight == 0f,
                "next request restarts playback with the configured beginning transition");
        require(original.targetWeight == 0f && player.getActiveAnimations().containsValue(original),
                "outgoing pose fades separately from the new same-name slot");
        require(player.getControllerFunctionAnimation("other") == other
                        && second.runtime.animations().getControllerFunctionAnimation("first") == secondOriginal,
                "reload does not affect another controller or Avatar");
        first.evalValue("ctrl.set_animation('animation.reload_hint');");
        require(player.getControllerFunctionAnimation("first") == replacement, "reload hint is consumed exactly once");
        first.evalValue("ctrl.indicate_reload();");
        first.evalValue("ctrl.reset();");
        first.evalValue("ctrl.set_animation('animation.reload_hint');");
        var afterReset = player.getControllerFunctionAnimation("first");
        require(afterReset != replacement && afterReset.time == 0f,
                "reset retires the previous slot before replaying the same animation");
        first.evalValue("ctrl.set_animation('animation.reload_hint');");
        require(player.getControllerFunctionAnimation("first") == afterReset, "reset clears a pending reload hint");
    }

    private static void testProjectEventAliases() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        ListTag events = new ListTag();
        add(events, "@probe", "v.event_count=v.event_count+1; v.event_payload=args[0]; v.event_extra=args[1];"
                + " v.event_nested=ysm.event('child',args[0]); v.event_controller=c.controller; t.saved=99; return args[0];");
        add(events, "@child", "return args[0];");
        add(events, "@recursive", "return ysm.event('recursive');");
        first.runtime.functions().load(events);
        second.runtime.functions().load(events);
        first.context.controller.setPath("controller", "event-outer");
        first.evalValue("t.saved=7;");
        int calls = 0;
        var bindings = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                first.context.owner.getAvatarBindings().getProperty("ysm");
        for (String alias : List.of("event", "run_event", "trigger_event")) {
            require(bindings.getProperty(alias) instanceof Function, "project event alias signature: " + alias);
            String payload = "模型-" + alias;
            Object eventResult = first.evalValue("ysm." + alias + "('probe','" + payload + "',v.event_arg=5);");
            require(payload.equals(ValueConversions.asString(eventResult)),
                    "event alias returns a reference string value: " + alias + " result=" + eventResult
                    + " count=" + first.context.variables.getPath("event_count")
                    + " payload=" + first.context.variables.getPath("event_payload")
                    + " nested=" + first.context.variables.getPath("event_nested")
                    + " registry=" + first.runtime.functions().eventDebugNames().keySet());
            calls++;
            require(payload.equals(ValueConversions.asString(first.context.variables.getPath("event_payload")))
                            && payload.equals(ValueConversions.asString(first.context.variables.getPath("event_nested"))),
                    "project alias passes payload after the event-name argument and nests scopes: " + alias);
            number(calls, first.context.variables.getPath("event_count"), "event alias runs exactly once");
            number(5f, first.context.variables.getPath("event_extra"), "extra event arguments evaluate before event execution");
            require("event-outer".equals(first.context.variables.getPath("event_controller")),
                    "manual event preserves the caller controller scope");
            number(7f, first.evalValue("t.saved;"), "event temporary scope restores its caller");
            require(Float.valueOf(0f).equals(first.evalValue("ysm." + alias + "();")), "project empty event call returns Float zero");
            require(Float.valueOf(0f).equals(first.evalValue("ysm." + alias + "('missing',v.unknown_event_arg=1);"))
                            && first.context.variables.getPath("unknown_event_arg") == null,
                    "unknown event does not evaluate payload arguments");
            require(Float.valueOf(0f).equals(first.evalValue("ysm." + alias + "(27,v.invalid_event_arg=1);"))
                            && first.context.variables.getPath("invalid_event_arg") == null,
                    "nonstrings do not select an event or evaluate payload arguments");
        }
        require(Boolean.TRUE.equals(first.evalValue("ysm.event('probe',1==1);")), "event return preserves raw Boolean");
        require(Float.valueOf(42f).equals(first.evalValue("ysm.run_event('probe',42);")), "event return preserves raw Float");
        require(second.context.variables.getPath("event_count") == null, "manual events cannot mutate another Avatar");
        require("separate".equals(ValueConversions.asString(second.evalValue("ysm.trigger_event('probe','separate');"))),
                "another Avatar executes its own event registry");
        number(1f, second.context.variables.getPath("event_count"), "another Avatar has its own event counter");
        require(Float.valueOf(0f).equals(first.evalValue("ysm.event('recursive');")), "recursive events stop at the bounded call depth");
        require("recovered".equals(ValueConversions.asString(first.evalValue("ysm.event('probe','recovered');"))),
                "event execution recovers after recursion rejection");
        number(7f, first.evalValue("t.saved;"), "recursive failure leaves outer temporary scope intact");
        Avatar.MolangContext unowned = new Avatar.MolangContext(null);
        require(Float.valueOf(0f).equals(ExpressionEvaluator.evaluator(unowned)
                        .eval(first.engine.parse("ysm.event(v.unowned_event_name=1);").getFirst()))
                        && unowned.variables.getPath("unowned_event_name") == null,
                "missing owner rejects a project event before its name expression");
    }

    private static void testAnimationCompletionQueries() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var shortClip = new YsmAnimationClip("animation.short", 1f, YsmAnimationClip.LoopMode.ONCE);
        var longClip = new YsmAnimationClip("animation.long", 2f, YsmAnimationClip.LoopMode.LOOP);
        String controllers = """
                {"animation_controllers":{
                  "mixed":{"states":{"default":{"animations":[{"animation.short":"v.short_enabled"},"animation.long"]}}},
                  "other":{"states":{"default":{"animations":["animation.long"]}}},
                  "empty":{"states":{"default":{}}}
                }}
                """;
        for (Fixture fixture : List.of(first, second)) {
            fixture.runtime.animations().registerAnimations(Map.of(shortClip.name, shortClip, longClip.name, longClip));
            fixture.runtime.animations().registerControllers(
                    top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser.parse(controllers, fixture.engine));
            fixture.context.variables.setPath("short_enabled", 1f);
            var bindings = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                    fixture.context.owner.getAvatarBindings().getProperty("query");
            for (String name : List.of("any_animation_finished", "all_animations_finished")) {
                require(bindings.getProperty(name) instanceof top.aihmc.omnimorph.molang.runtime.Variable
                                && !(bindings.getProperty(name) instanceof Function), "completion query is a scalar: " + name);
                require(Boolean.FALSE.equals(fixture.evalValue("q." + name + ";")), "absent playback flags are false");
            }
            completionFlags(fixture, "empty", true, true);
            fixture.runtime.animations().controllerRuntime().update(ExpressionEvaluator.evaluator(fixture.context), 0f);
            completionFlags(fixture, "mixed", false, false);
        }
        var player = first.runtime.animations();
        var evaluator = ExpressionEvaluator.evaluator(first.context);
        var advance = player.getClass().getDeclaredMethod("advanceAnimationTimeline", float.class, ExpressionEvaluator.class);
        advance.setAccessible(true);
        advance.invoke(player, 1f, evaluator);
        completionFlags(first, "mixed", false, false);
        require(!player.isControllerAnimationFinished("mixed", shortClip.name), "exact reference length is not past the completion boundary");
        advance.invoke(player, 0.01f, evaluator);
        completionFlags(first, "mixed", true, false);
        completionFlags(first, "other", false, false);
        player.controllerRuntime().update(evaluator, 0f);
        require(player.getControllerAnimation("mixed", shortClip.name) == null, "completed once clip retires through the real controller update");
        completionFlags(first, "mixed", true, false);
        first.context.variables.setPath("short_enabled", 0f);
        player.controllerRuntime().update(evaluator, 0f);
        completionFlags(first, "mixed", false, false);
        first.context.variables.setPath("short_enabled", 1f);
        player.controllerRuntime().update(evaluator, 0f);
        require(player.getControllerAnimation("mixed", shortClip.name) != null, "reactivated reference has a fresh playback instance");
        completionFlags(first, "mixed", false, false);
        advance.invoke(player, 1.1f, evaluator);
        completionFlags(first, "mixed", true, true);
        completionFlags(first, "other", true, true);
        require(player.getControllerAnimation("other", longClip.name).time < 1f,
                "loop completion survives native timeline wrapping below its length");
        advance.invoke(player, 0.1f, evaluator);
        completionFlags(first, "other", true, true);
        var function = player.playControllerFunctionAnimation("other", longClip.name, YsmAnimationClip.LoopMode.LOOP, 1f, 0f);
        require(!function.hasCompletedCycle(), "fresh function playback does not inherit another slot's completion");
        completionFlags(first, "other", true, false);
        player.clearControllerFunctionAnimation("other", 0f);
        completionFlags(first, "other", true, true);
        completionFlags(second, "mixed", false, false);
        first.context.anim_time = 100f;
        first.context.life_time = 0f;
        require(Boolean.FALSE.equals(first.evalValue("q.any_animation_finished;"))
                        && Boolean.FALSE.equals(first.evalValue("q.all_animations_finished;")),
                "controller scope restores false flags and does not infer completion from unrelated time fields");
    }

    private static void completionFlags(Fixture fixture, String controller, boolean any, boolean all) throws Exception {
        try (var scope = fixture.runtime.animations().controllerRuntime()
                .openControllerContext(controller, ExpressionEvaluator.evaluator(fixture.context))) {
            require(Boolean.valueOf(any).equals(fixture.evalValue("q.any_animation_finished;"))
                            && Boolean.valueOf(all).equals(fixture.evalValue("q.all_animations_finished;")),
                    "raw Boolean completion flags for controller=" + controller + " expected any=" + any + " all=" + all);
        }
    }

    private static void testControllerFunctionContracts() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var registry = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                first.context.owner.getAvatarBindings().getProperty("ctrl");
        Map<String, List<Integer>> validSizes = Map.of(
                "set_animation", List.of(1, 2), "reset", List.of(0),
                "set_beginning_transition_length", List.of(1));
        for (var entry : validSizes.entrySet()) {
            Function function = (Function) registry.getProperty(entry.getKey());
            for (int size = 0; size <= 4; size++) {
                require(function.validateArgumentSize(size) == entry.getValue().contains(size),
                        "reference controller function arity: " + entry.getKey() + "/" + size);
            }
        }
        require(first.evalValue("ctrl.set_animation(v.outside_controller=1);") == null,
                "set_animation outside a controller returns null");
        require(first.evalValue("ctrl.set_beginning_transition_length(v.outside_transition=1);") == null,
                "transition setter outside a controller returns null");
        require(first.context.variables.getPath("outside_controller") == null
                        && first.context.variables.getPath("outside_transition") == null,
                "missing controller rejects before argument effects");

        var clip = new YsmAnimationClip("animation.contract", 1f, YsmAnimationClip.LoopMode.LOOP);
        var player = first.runtime.animations();
        player.registerAnimations(Map.of(clip.name, clip));
        first.context.controller.setPath("controller", "first");
        require(first.evalValue("ctrl.set_beginning_transition_length(0.25);") == null,
                "transition setter returns null on success");
        first.evalValue("ctrl.set_beginning_transition_length(-1);");
        player.setControllerFunctionTransitionLength("first", Float.NaN);
        player.setControllerFunctionTransitionLength("first", Float.POSITIVE_INFINITY);
        number(0.25f, player.controllerFunctionTransitionLength("first"),
                "negative and nonfinite transitions preserve the previous seconds value");
        require(first.evalValue("ctrl.set_animation('animation.contract');") == null,
                "set_animation returns null on success");
        var firstPlaying = player.getControllerFunctionAnimation("first");
        require(firstPlaying.loopMode == YsmAnimationClip.LoopMode.LOOP,
                "omitted loop mode preserves the animation default");
        number(4f, firstPlaying.fadeSpeed, "quarter-second transition uses seconds");
        first.evalValue("ctrl.set_animation('animation.contract',10.9);");
        require(firstPlaying.loopMode == YsmAnimationClip.LoopMode.LOOP, "loop selector truncates instead of rounding");
        first.evalValue("ctrl.set_animation('animation.contract',11.9);");
        require(firstPlaying.loopMode == YsmAnimationClip.LoopMode.ONCE, "fractional once selector truncates");
        first.evalValue("ctrl.set_animation('animation.contract',99);");
        require(firstPlaying.loopMode == YsmAnimationClip.LoopMode.LOOP, "unknown selector preserves animation default");
        require(first.evalValue("ctrl.set_animation('',v.empty_animation_mode=11);") == null
                        && first.context.variables.getPath("empty_animation_mode") == null,
                "empty animation skips mode argument effects");
        require(first.evalValue("ctrl.set_animation('missing');") == null,
                "unknown animation returns null");

        first.context.controller.setPath("controller", "second");
        first.evalValue("ctrl.set_beginning_transition_length(2);");
        first.evalValue("ctrl.set_animation('animation.contract');");
        var secondPlaying = player.getControllerFunctionAnimation("second");
        number(0.5f, secondPlaying.fadeSpeed, "second controller owns its two-second transition");
        number(0.25f, player.controllerFunctionTransitionLength("first"), "controller transition isolation");
        number(0f, second.runtime.animations().controllerFunctionTransitionLength("first"), "Avatar transition isolation");
        first.context.controller.setPath("controller", "first");
        require(first.evalValue("ctrl.reset();") == null, "reset returns null");
        require(player.getControllerFunctionAnimation("first") == null
                        && player.getControllerFunctionAnimation("second") == secondPlaying,
                "reset clears only its controller function slot");
        number(0f, firstPlaying.targetWeight, "reset fades the retired slot");
        number(4f, firstPlaying.fadeSpeed, "reset uses its own transition duration");
        number(0.25f, player.controllerFunctionTransitionLength("first"), "reset retains controller transition configuration");
        player.clearControllerFunctionTransitions();
        number(0f, player.controllerFunctionTransitionLength("first"), "runtime cleanup clears first transition");
        number(0f, player.controllerFunctionTransitionLength("second"), "runtime cleanup clears second transition");
    }

    private static void testControllerConstants() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Map<String, Float> expected = Map.of(
                "state_continue", 2f, "state_stop", 3f, "state_break", 3f,
                "state_pause", 4f, "state_bypass", 5f,
                "loop", 10f, "play_once", 11f, "hold_on_last_frame", 12f);
        var registry = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                first.context.owner.getAvatarBindings().getProperty("ctrl");
        for (var entry : expected.entrySet()) {
            String name = entry.getKey();
            require(registry.getProperty(name) instanceof Variable && !(registry.getProperty(name) instanceof Function),
                    "controller constant is a scalar Variable: " + name);
            Object value = first.evalValue("ctrl." + name + ";");
            require(value instanceof Float && value.equals(entry.getValue()), "reference constValue numeric normalization: " + name);
            first.context.controller.setPath(name, 99f);
            first.context.variables.setPath(name, -1f);
            number(entry.getValue(), first.evalValue("ctrl." + name + ";"), "model/controller values cannot shadow enum constants: " + name);
            number(entry.getValue(), second.evalValue("ctrl." + name + ";"), "constant is independent of another Avatar: " + name);
        }
        var clip = new YsmAnimationClip("animation.constant-loop", 1f, YsmAnimationClip.LoopMode.ONCE);
        first.runtime.animations().registerAnimations(Map.of(clip.name, clip));
        first.context.controller.setPath("controller", "constant-controller");
        Map<String, YsmAnimationClip.LoopMode> modes = Map.of(
                "loop", YsmAnimationClip.LoopMode.LOOP,
                "play_once", YsmAnimationClip.LoopMode.ONCE,
                "hold_on_last_frame", YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME);
        for (var entry : modes.entrySet()) {
            first.evalValue("ctrl.set_animation('animation.constant-loop',ctrl." + entry.getKey() + ");");
            var playing = first.runtime.animations().getControllerFunctionAnimation("constant-controller");
            require(playing != null && playing.loopMode == entry.getValue(), "enum reaches actual controller playback mode: " + entry.getKey());
        }
    }

    private static void testTimeQueryContracts() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        first.context.anim_time = 1.25f;
        first.context.life_time = 7.5f;
        first.context.delta_time = 0.05f;
        first.context.time_of_day = 0.75f;
        first.context.time_stamp = 18000f;
        first.context.moon_phase = 6;
        first.context.frame_count = 123;
        Map<String, Float> expected = Map.of(
                "anim_time", 1.25f, "life_time", 7.5f, "delta_time", 0.05f,
                "time_of_day", 0.75f, "time_stamp", 18000f, "moon_phase", 6f, "frame_count", 123f);
        var queries = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                first.context.owner.getAvatarBindings().getProperty("query");
        for (var entry : expected.entrySet()) {
            String name = entry.getKey();
            require(queries.getProperty(name) instanceof Variable && !(queries.getProperty(name) instanceof Function),
                    "time query is a scalar Variable: " + name);
            Object actual = first.evalValue("query." + name + ";");
            require(actual instanceof Float && actual.equals(entry.getValue()), "time query raw Float value: " + name);
            number(entry.getValue(), first.evalValue("q." + name + ";"), "time query alias value: " + name);
            number(0f, second.evalValue("q." + name + ";"), "time query reads its owning context: " + name);
        }
        first.context.anim_time = 2.5f;
        first.context.frame_count = 124;
        number(2.5f, first.evalValue("q.anim_time;"), "time query observes changes without rebuilding bindings");
        number(124f, first.evalValue("q.frame_count;"), "frame query observes changes without rebuilding bindings");
        first.context.reset();
        for (String name : expected.keySet()) {
            number(0f, first.evalValue("q." + name + ";"), "time query observes context reset: " + name);
            number(0f, second.evalValue("query." + name + ";"), "reset remains isolated from second context: " + name);
        }
    }

    private static void testPerlinEntityContract() throws Exception {
        Fixture fixture = new Fixture();
        require(fixture.evalValue("ysm.perlin_noise(v.noise_side_effect=1,0.25);") == null
                        && fixture.context.variables.getPath("noise_side_effect") == null,
                "Perlin requires Entity before evaluating seed or coordinates");
        // Texture/camera fixtures bootstrap native registry classes later in this run.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        fixture.context.entity = (RemotePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(RemotePlayer.class);
        Object value = fixture.evalValue("ysm.perlin_noise(0,0.25,0,0,v.noise_side_effect=99);");
        require(value instanceof Float && Math.abs(((Float) value) - 0.07763672f) < 0.000002f,
                "formal Entity binding preserves Float native oracle value");
        require(fixture.context.variables.getPath("noise_side_effect") == null,
                "Perlin ignores and does not evaluate arguments after z");
        Fixture other = new Fixture();
        other.context.entity = fixture.context.entity;
        other.context.is_first_person = 1f;
        number((Float) value, other.evalValue("ysm.perlin_noise(0,0.25);"),
                "pure noise does not depend on another Avatar's domain or mutable state");
    }

    private static void testNamespaceAliases(Fixture first, Fixture second) throws Exception {
        var firstBindings = first.context.owner.getAvatarBindings();
        var secondBindings = second.context.owner.getAvatarBindings();
        for (String[] pair : List.of(new String[]{"v", "variable"}, new String[]{"t", "temp"},
                new String[]{"c", "context"}, new String[]{"q", "query"})) {
            require(firstBindings.getProperty(pair[0]) == firstBindings.getProperty(pair[1]),
                    "namespace aliases share the same registry/storage: " + pair[0]);
            require(firstBindings.getProperty(pair[0]) != secondBindings.getProperty(pair[0]),
                    "namespace registry/storage is owned by its Avatar: " + pair[0]);
        }
        first.eval("variable.alias_string='模型字符串'; context.alias_number=17; temp.alias_bool=(1==1);");
        Object storedString = first.evalValue("v.alias_string;");
        require(storedString instanceof top.aihmc.omnimorph.molang.parser.ast.StringExpression
                        && "模型字符串".equals(ValueConversions.asString(storedString)),
                "variable alias preserves the evaluator's typed string literal representation");
        number(17f, first.evalValue("c.alias_number;"), "context alias retains raw numeric value");
        require(Boolean.TRUE.equals(first.evalValue("t.alias_bool;")), "temp alias retains raw Boolean");
        require(second.context.variables.getPath("alias_string") == null
                        && second.context.controller.getPath("alias_number") == null,
                "alias writes do not modify a second Avatar's variables or controller storage");
        first.context.anim_time = 3f;
        second.context.anim_time = 9f;
        number(3f, first.evalValue("query.anim_time;"), "long query alias reads first Avatar context");
        number(9f, second.evalValue("q.anim_time;"), "short query alias reads second Avatar context");
        first.context.anim_time = second.context.anim_time = 0f;
    }

    private static void testFilterPreviewDomain() throws Exception {
        Fixture fixture = new Fixture();
        for (String name : List.of("first_order", "second_order")) {
            require(fixture.evalValue("ysm." + name + "('gate',v.filter_side_effect=99);") == null,
                    "filter rejects missing entity before evaluating arguments: " + name);
        }
        require(fixture.context.variables.getPath("filter_side_effect") == null, "missing entity does not evaluate filter arguments");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        fixture.context.entity = (RemotePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(RemotePlayer.class);
        for (String name : List.of("first_order", "second_order")) {
            require(Integer.valueOf(0).equals(fixture.evalValue("ysm." + name + "('',v.filter_side_effect=99);")),
                    "empty filter name preserves reference integer zero: " + name);
        }
        require(fixture.context.variables.getPath("filter_side_effect") == null, "empty name does not evaluate input");
        Object first = fixture.evalValue("ysm.first_order('extra-first',7,1,v.filter_side_effect=99);");
        Object second = fixture.evalValue("ysm.second_order('extra-second',8,1,1,1,v.filter_side_effect=99);");
        require(first instanceof Float && first.equals(7f) && second instanceof Float && second.equals(8f),
                "nonempty filters return raw Float inputs at creation");
        require(fixture.context.variables.getPath("filter_side_effect") == null, "unused extra arguments are not evaluated");
        Avatar owner = fixture.context.owner;
        owner.renderMode = EntityRenderMode.RENDER;
        fixture.context.filters.update(YsmFilterRuntime.Domain.BODY, 100f, 1L, null);
        number(10f, fixture.evalValue("ysm.first_order('view',10);"), "body creates independent filter");
        fixture.context.is_first_person = 1f;
        fixture.context.filters.update(YsmFilterRuntime.Domain.FIRST_PERSON, 100f, 1L, null);
        number(40f, fixture.evalValue("ysm.first_order('view',40);"), "arm creates independent filter");
        owner.renderMode = EntityRenderMode.FIGURA_GUI;
        fixture.context.filters.update(YsmFilterRuntime.Domain.PREVIEW, 100f, 1L, null);
        number(90f, fixture.evalValue("ysm.first_order('view',90);"), "preview takes priority over stale arm flag");
        fixture.context.filters.update(YsmFilterRuntime.Domain.PREVIEW, 101f, 2L, null);
        number(4.5f, fixture.evalValue("ysm.first_order('view',90);"), "preview advances its own state");
        owner.renderMode = EntityRenderMode.RENDER;
        fixture.context.is_first_person = 0f;
        fixture.context.filters.update(YsmFilterRuntime.Domain.BODY, 101f, 2L, null);
        number(0.5f, fixture.evalValue("ysm.first_order('view',10);"), "world return retains body state");
        fixture.context.is_first_person = 1f;
        fixture.context.filters.update(YsmFilterRuntime.Domain.FIRST_PERSON, 101f, 2L, null);
        number(2f, fixture.evalValue("ysm.first_order('view',40);"), "world return retains arm state");
    }

    private static void testDebugOutputContracts() throws Exception {
        Fixture plain = new Fixture(false);
        Fixture encrypted = new Fixture(true);
        boolean previousDebug = org.figuramc.figura.config.Configs.DEBUG_MODE.value;
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager
                .getLogger(org.figuramc.figura.FiguraMod.LOGGER.getName());
        var previousLevel = logger.getLevel();
        List<String> messages = new ArrayList<>();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender(
                "ysm-binding-debug-output", null, null, true, null) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(org.apache.logging.log4j.Level.WARN);
        try {
            var queries = (top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding)
                    plain.context.owner.getAvatarBindings().getProperty("query");
            Function function = (Function) queries.getProperty("debug_output");
            require(!function.validateArgumentSize(0) && function.validateArgumentSize(1)
                            && function.validateArgumentSize(4), "reference debug output requires at least one argument");
            try {
                plain.engine.parse("q.debug_output();");
                throw new AssertionError("debug output accepted empty arguments");
            } catch (top.aihmc.omnimorph.molang.parser.ParseException expected) {
                // Production parser enforces the function's reference signature.
            }
            org.figuramc.figura.config.Configs.DEBUG_MODE.value = false;
            require(plain.evalValue("q.debug_output(v.debug_disabled=1);") == null
                            && plain.context.variables.getPath("debug_disabled") == null && messages.isEmpty(),
                    "disabled debugging returns null before evaluating arguments or logging");
            org.figuramc.figura.config.Configs.DEBUG_MODE.value = true;
            require(plain.evalValue("q.debug_output('prefix',v.debug_value=3,'模型',v.unset);") == null,
                    "enabled debug output returns raw null");
            require(messages.equals(List.of("[Molang Debug] prefix3.0模型null"))
                            && Float.valueOf(3f).equals(plain.context.variables.getPath("debug_value")),
                    "debug output evaluates and concatenates every raw argument in order: messages=" + messages
                            + " value=" + plain.context.variables.getPath("debug_value"));
            messages.clear();
            require(encrypted.evalValue("q.debug_output(v.private_debug=1,'PRIVATE_SOURCE');") == null
                            && encrypted.context.variables.getPath("private_debug") == null && messages.isEmpty(),
                    "encrypted runtime suppresses argument evaluation and detailed logging even with no owner NBT");
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            encrypted.context.entity = (net.minecraft.world.entity.Entity) ((Unsafe) unsafeField.get(null))
                    .allocateInstance(net.minecraft.world.entity.decoration.ArmorStand.class);
            int modReads = top.aihmc.omnimorph.utils.fabric.PlatformUtilsImpl.loadedModReads;
            for (String name : List.of("dump_mods", "dump_effects", "dump_biome")) {
                require(encrypted.evalValue("ysm." + name + ";") == null && messages.isEmpty(),
                        "encrypted runtime metadata suppresses dump diagnostics before native state access: " + name);
            }
            require(top.aihmc.omnimorph.utils.fabric.PlatformUtilsImpl.loadedModReads == modReads,
                    "encrypted runtime diagnostic does not enumerate the platform");
            require(ExpressionEvaluator.evaluator(encrypted.context.owner)
                            .eval(encrypted.engine.parse("q.debug_output('PRIVATE_DIRECT_OWNER');").getFirst()) == null
                            && messages.isEmpty(), "legacy direct Avatar context also suppresses encrypted debug output");
            CompoundTag payload = new CompoundTag();
            payload.putInt("crypto_version", 3);
            plain.context.owner.nbt = new CompoundTag();
            plain.context.owner.nbt.put("ysm", payload);
            require(plain.evalValue("q.debug_output(v.private_nbt=1,'PRIVATE_SOURCE');") == null
                            && plain.context.variables.getPath("private_nbt") == null && messages.isEmpty(),
                    "encrypted source payload also suppresses logging independently of runtime metadata");
        } finally {
            org.figuramc.figura.config.Configs.DEBUG_MODE.value = previousDebug;
            logger.removeAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    private static void testOptionalModDefaults() throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Map<String, Object> expected = Map.ofEntries(
                Map.entry("im_delta", 0L), Map.entry("im_pitch", 0f), Map.entry("im_time", 0L),
                Map.entry("bcombat_attack_animation", ""), Map.entry("carryon_type", ""),
                Map.entry("carryon_is_princess", false), Map.entry("parcool_state", ""),
                Map.entry("slashblade_animation", ""), Map.entry("has_sophisticated_backpack", false),
                Map.entry("tac_hold_gun", false), Map.entry("tac_gun_type", ""), Map.entry("tac_gun_id", ""),
                Map.entry("tac_is_fire", false), Map.entry("tac_is_aim", false), Map.entry("tac_is_reload", false),
                Map.entry("tac_is_melee", false), Map.entry("tac_is_draw", false), Map.entry("tac_fire_mode", "")
        );
        MolangBindings.CtrlBinding registry = new MolangBindings.CtrlBinding();
        for (String name : expected.keySet()) {
            require(registry.getProperty(name) instanceof Variable
                            && !(registry.getProperty(name) instanceof Function), "optional variable signature: " + name);
            require(first.evalValue("ctrl." + name + ";") == null, "optional missing entity gate: " + name);
            require(MolangBindings.CtrlBinding.optionalBindingUnavailableReason(name).startsWith("CAPABILITY_UNAVAILABLE:"),
                    "optional capability has an explicit unavailable reason: " + name);
        }
        require(MolangBindings.CtrlBinding.optionalBindingUnavailableReason("walk") == null
                        && MolangBindings.CtrlBinding.optionalBindingUnavailableReason("unknown_mod_binding") == null,
                "optional capability reason does not classify core or unknown bindings");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        RemotePlayer player = (RemotePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(RemotePlayer.class);
        first.context.entity = player;
        first.context.controller.setPath("tac_gun_type", "invented-gun");
        first.context.controller.setPath("tac_hold_gun", true);
        for (var entry : expected.entrySet()) {
            Object actual = first.evalValue("ctrl." + entry.getKey() + ";");
            require(entry.getValue().equals(actual) && actual.getClass() == entry.getValue().getClass(),
                    "reference Fabric raw type/default: " + entry.getKey());
            require(second.evalValue("ctrl." + entry.getKey() + ";") == null,
                    "optional default gate remains per Avatar: " + entry.getKey());
        }
        require(Boolean.TRUE.equals(first.evalValue("ctrl.tac_gun_type=='';"))
                        && Boolean.TRUE.equals(first.evalValue("''==ctrl.parcool_state;")),
                "optional strings compare as raw strings in both operand orders");
    }

    private static void testFlyingQuery() throws Exception {
        Fixture fixture = new Fixture();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FixturePlayer player = (FixturePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(FixturePlayer.class);
        player.abilities = new Abilities();
        fixture.context.entity = player;
        require(Boolean.FALSE.equals(fixture.evalValue("ctrl.fly;")), "flight requires native flying ability");
        player.abilities.flying = true;
        require(Boolean.TRUE.equals(fixture.evalValue("ctrl.fly;")), "native flying ability selects flight");
        for (String name : List.of("idle", "walk", "run", "jump", "attacked", "swim_stand")) {
            require(Boolean.FALSE.equals(fixture.evalValue("ctrl." + name + ";")), "flight suppresses " + name);
        }
        player.pose = Pose.SWIMMING;
        require(Boolean.FALSE.equals(fixture.evalValue("ctrl.fly;"))
                        && Boolean.TRUE.equals(fixture.evalValue("ctrl.climbing;")),
                "crawling has priority over flying ability");
        player.pose = Pose.FALL_FLYING;
        player.elytraFlying = true;
        require(Boolean.FALSE.equals(fixture.evalValue("ctrl.fly;"))
                        && Boolean.TRUE.equals(fixture.evalValue("ctrl.elytra_fly;")),
                "elytra has priority over flying ability");
    }

    private static void testPlayerFoodQuery() throws Exception {
        Fixture fixture = new Fixture();
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        RemotePlayer player = (RemotePlayer) ((Unsafe) unsafeField.get(null)).allocateInstance(RemotePlayer.class);
        var food = new net.minecraft.world.food.FoodData();
        Field foodData = net.minecraft.world.entity.player.Player.class.getDeclaredField("foodData");
        foodData.setAccessible(true);
        foodData.set(player, food);
        fixture.context.entity = player;
        for (int amount : new int[]{20, 6, 0, 17}) {
            food.setFoodLevel(amount);
            require(Integer.valueOf(amount).equals(fixture.evalValue("ysm.food_level;")),
                    "player food query reads real FoodData as raw Integer: " + amount);
        }
        fixture.context.reset();
        require(fixture.evalValue("ysm.food_level;") == null, "player food query clears with its entity scope");
    }

    private static void testJumpingQuery() throws Exception {
        Fixture fixture = new Fixture();
        require(fixture.evalValue("q.is_jumping;") == null, "jump query requires a Player");
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        fixture.context.entity = (net.minecraft.world.entity.decoration.ArmorStand)
                unsafe.allocateInstance(net.minecraft.world.entity.decoration.ArmorStand.class);
        require(fixture.evalValue("q.is_jumping;") == null, "non-player LivingEntity cannot answer player jumping");
        FixturePlayer player = (FixturePlayer) unsafe.allocateInstance(FixturePlayer.class);
        player.abilities = new Abilities();
        fixture.context.entity = player;
        player.airborne = true;
        fixture.context.is_jumping = 0f;
        require(Boolean.TRUE.equals(fixture.evalValue("q.is_jumping;")),
                "airborne nonflying nonriding dry player is jumping without a velocity threshold");
        for (int blocker = 0; blocker < 4; blocker++) {
            player.abilities.flying = blocker == 0;
            player.fixtureVehicle = blocker == 1 ? player : null;
            player.airborne = blocker != 2;
            player.fixtureWater = blocker == 3;
            fixture.context.is_jumping = 1f;
            require(Boolean.FALSE.equals(fixture.evalValue("q.is_jumping;")),
                    "jump query applies reference exclusion: " + blocker);
        }
        player.abilities.flying = false;
        player.fixtureVehicle = null;
        player.airborne = true;
        player.fixtureWater = false;
        require(Boolean.TRUE.equals(fixture.evalValue("q.is_jumping;")), "jump query immediately recovers after exclusions clear");
        Fixture second = new Fixture();
        require(second.evalValue("q.is_jumping;") == null, "jumping scope stays in its Avatar");
        fixture.context.reset();
        require(fixture.evalValue("q.is_jumping;") == null, "reset removes player jumping scope");
    }

    private static final class FixturePlayer extends RemotePlayer {
        private boolean blocking;
        private Abilities abilities;
        private Pose pose;
        private boolean elytraFlying;
        private boolean airborne;
        private boolean fixtureWater;
        private net.minecraft.world.entity.Entity fixtureVehicle;

        private FixturePlayer() {
            super(null, null);
        }

        @Override
        public boolean isBlocking() {
            return blocking;
        }

        @Override
        public Abilities getAbilities() {
            return abilities;
        }

        @Override
        public Pose getPose() {
            return pose == null ? Pose.STANDING : pose;
        }

        @Override
        public boolean isDeadOrDying() {
            return false;
        }

        @Override
        public boolean isAutoSpinAttack() {
            return false;
        }

        @Override
        public boolean isSwimming() {
            return false;
        }

        @Override
        public boolean onClimbable() {
            return false;
        }

        @Override
        public boolean isFallFlying() {
            return elytraFlying;
        }

        @Override
        public boolean isInWater() {
            return fixtureWater;
        }

        @Override
        public boolean onGround() {
            return !airborne;
        }

        @Override
        public net.minecraft.world.entity.Entity getVehicle() {
            return fixtureVehicle;
        }

        @Override
        public boolean isSprinting() {
            return false;
        }
    }

    private static void testControllerExecutionDiagnostics() throws Exception {
        String source = """
                {"animation_controllers":{"player_ctrl_main":{"states":{"default":{
                  "on_entry":["ysm.missing_entry();","v.after_controller_error=7;"],
                  "animations":[{"missing_clip":"ysm.missing_weight()"}],
                  "transitions":[{"default":"ysm.missing_condition()"}]
                }}}}}
                """;
        for (Fixture fixture : List.of(new Fixture(false), new Fixture(true))) {
            CompoundTag controller = new CompoundTag();
            controller.putString("path", "controllers/runtime.json");
            controller.putByteArray("data", source.getBytes(StandardCharsets.UTF_8));
            ListTag files = new ListTag();
            files.add(controller);
            CompoundTag model = new CompoundTag();
            model.put("animation_controllers", files);
            Field sources = YsmModelRuntime.class.getDeclaredField("controllerSources");
            sources.setAccessible(true);
            var reader = YsmModelRuntime.class.getDeclaredMethod("readAnimationControllers", CompoundTag.class,
                    MolangEngine.class, BiConsumer.class, Map.class);
            reader.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, YsmAnimationController> controllers =
                    (Map<String, YsmAnimationController>) reader.invoke(
                            null, model, fixture.engine, null, sources.get(fixture.runtime));
            fixture.runtime.animations().registerControllers(controllers);
            for (int frame = 0; frame < 3; frame++) {
                fixture.runtime.animations().controllerRuntime().update(ExpressionEvaluator.evaluator(fixture.context), 0.05f);
            }
            number(7f, fixture.eval("v.after_controller_error;"), "controller executes following statement after binding error");
            List<String> diagnostics = fixture.runtime.controllerDiagnosticMessages();
            if (fixture.runtime.encryptedSource()) {
                require(diagnostics.equals(List.of("Encrypted YSM operation failed: runtime/controller-execution")),
                        "encrypted controller execution failures are private and deduplicated");
            } else {
                require(diagnostics.size() == 3, "entry/condition/weight failures each report once across repeated frames");
                for (String diagnostic : diagnostics) {
                    require(diagnostic.contains("resource=controllers/runtime.json")
                                    && diagnostic.contains("controller=player_ctrl_main")
                                    && diagnostic.contains("state=default") && diagnostic.contains("ysm.missing_"),
                            "real controller execution has resource/state/binding location");
                }
                for (int index = 0; index < 25; index++) {
                    fixture.runtime.reportControllerExecutionFailure(new IllegalStateException("bounded-error-" + index));
                }
                require(fixture.runtime.controllerDiagnosticMessages().size() == 16, "per-model diagnostics have bounded storage/output");
            }
        }
    }

    private static final class Fixture {
        private final Avatar.MolangContext context;
        private final YsmModelRuntime runtime;
        private final MolangEngine engine;

        private Fixture() throws Exception {
            this(true);
        }

        private Fixture(boolean encrypted) throws Exception {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            // CPU owner shell: the binding registry is real, game/GPU bootstrap is outside this fixture.
            Avatar owner = (Avatar) ((Unsafe) unsafeField.get(null)).allocateInstance(Avatar.class);
            context = new Avatar.MolangContext(owner);
            install(owner, "molangContext", context);
            install(owner, "controls", new AvatarControlRuntime());
            Constructor<YsmModelRuntime> constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                    YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                    String.class, String.class, Map.class, float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            runtime = constructor.newInstance(owner, new YsmGeometry(), null, null,
                    "fixture", false, "NEW", "function-contract-fixture", Map.of(), 1f, 1f, encrypted);
            install(owner, "ysmRuntime", runtime);
            var bindings = owner.getAvatarBindings();
            bindings.register("inspect_binding_context", (Function) (evaluation, arguments) ->
                    runtime.functions().bindingDiagnosticContext());
            engine = MolangEngine.fromCustomBinding(bindings);
            ListTag functions = new ListTag();
            add(functions, "sum", "return args[0]+args[1];");
            add(functions, "nested", "t.saved=args[0]; t.result=fn.sum(args[0],args[1]); return t.saved*10+t.result;");
            add(functions, "child", "t.saved=99; return args[0]*2;");
            add(functions, "parent", "t.saved=args[0]; t.result=fn.child(args[0]); return t.saved*10+t.result;");
            add(functions, "early", "v.before_return=args[0]; return args[0]+1; v.after_return=99;");
            add(functions, "identity", "return args[0];");
            add(functions, "second", "return args[1];");
            add(functions, "string_equal", "return args[0]=='模型文字';");
            add(functions, "count", "v.counter=v.counter+1; return v.counter;");
            add(functions, "fail", "t.saved=100; ysm.bone_param('root');");
            add(functions, "recover", "t.saved=7; t.result=fn.fail(); return t.saved;");
            add(functions, "context", "return q.anim_time+c.weight;");
            add(functions, "endless", "t.saved=100; loop(1024,{loop(1024,{});});");
            add(functions, "diagnostic", "return inspect_binding_context();");
            add(functions, "@sync", "v.sync_count=v.sync_count+1; v.horn=args[1];");
            add(functions, "@defer", "v.order=v.order*10+args[0]; v.payload=args[1];");
            runtime.functions().load(functions);
            require(runtime.functions().functionCount() == 14, "all fixture files load through the formal parser/registry");
            require(runtime.functions().eventCount() == 2, "both event files load through the formal parser/registry");
        }

        private Object eval(String source) throws Exception {
            return ExpressionEvaluator.evaluator(context).evalAll(engine.parse(source), true);
        }

        private Object evalValue(String source) throws Exception {
            return ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst());
        }
    }

    private static void add(ListTag functions, String name, String source) {
        CompoundTag tag = new CompoundTag();
        tag.putString("path", "functions/" + name + ".molang");
        tag.putByteArray("data", source.getBytes(StandardCharsets.UTF_8));
        functions.add(tag);
    }

    private static void install(Avatar owner, String name, Object value) throws ReflectiveOperationException {
        Field field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static void number(float expected, Object actual, String message) {
        require(actual instanceof Number value && value.floatValue() == expected, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
