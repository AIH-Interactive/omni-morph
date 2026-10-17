package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncCodec;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncReceiver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Actual independent runtime state application inside the native material CPU harness. */
final class YsmSyncModelSmokeTest {
    private YsmSyncModelSmokeTest() {
    }

    static void run(YsmModelRuntime source, YsmModelRuntime observer) throws Exception {
        String controllerJson = """
                {"animation_controllers":{"Player.Main":{"initial_state":"a","states":{
                  "a":{"transitions":[{"b":"v.go>0"}]},
                  "b":{"animations":["idle"],"transitions":[{"a":"v.go==0"}],"on_entry":["v.remote_entries=v.remote_entries+1;"]}
                }}}}
                """;
        for (var runtime : List.of(source, observer)) {
            var engine = MolangEngine.fromCustomBinding(runtime.owner().getAvatarBindings());
            runtime.animations().registerControllers(
                    YsmAnimationControllerParser.parse(controllerJson, engine));
            runtime.owner().controls.register(new AvatarControlDefinition(
                    "sync.choice", AvatarControlType.ENUM)
                    .addOptionCommand("A", "v.choice=1;")
                    .addOptionCommand("B", "v.choice=3; v.choice_calls=v.choice_calls+1; ysm.sync(8);")
                    .setDefault("A"));
            var syncFunction = new CompoundTag();
            syncFunction.putString("path", "functions/@sync.molang");
            syncFunction.putByteArray("data", ("v.sync_count=v.sync_count+1; v.payload=args[1]; "
                    + "args[0]==99 ? ysm.sync(100) : 0;").getBytes(StandardCharsets.UTF_8));
            var functions = new ListTag();
            functions.add(syncFunction);
            runtime.functions().load(functions);
        }
        var owner = source.owner();
        owner.controls.setValue(owner, "ysm.width_scale", 0.8);
        owner.controls.setValue(owner, "ysm.native_state_machine", false);
        owner.controls.setValue(owner, "sync.choice", "B");
        owner.getMolangContext().variables.setPath("go", 1f);
        source.animations().controllerRuntime().update(
                ExpressionEvaluator.evaluator(owner.getMolangContext()), 0.25f);
        require(source.actions().trigger("gui"), "owner starts a real action");
        source.animations().getActiveAnimation("gui").time = 0.5f;
        source.setTexture("c");
        var sourceModel = new YsmSyncModel(source);
        var observerModel = new YsmSyncModel(observer);
        require(sourceModel.contentHash().equals(observerModel.contentHash())
                        && sourceModel.schemaHash().equals(observerModel.schemaHash()),
                "independent runtime assemblies have identical content and inventory hashes");
        var captured = sourceModel.capture();
        source.functions().drainSyncEvents();
        var packet = new YsmSyncMessage(owner.owner, new UUID(1, 2),
                sourceModel.contentHash(), sourceModel.schemaHash(), 0, 0, -1, 10, true,
                source.functions().syncWatermark(), captured, List.of());
        var decoded = YsmSyncCodec.decode(
                YsmSyncCodec.encode(packet));
        observerModel.apply(decoded.state());
        var remote = observer;
        require(remote.textureId().equals("c") && Math.abs(remote.widthScale() - 0.8f) < 0.0001,
                "observer actually applies selected material and model scale");
        require(remote.actions().isActive("gui") && remote.actions().time("gui") == 0.5f,
                "observer action API sees restored active clip and owner time");
        require(remote.animations().controllerRuntime().currentState("Player.Main").equals("b")
                        && remote.animations().controllerRuntime().stateTime("Player.Main")
                        == source.animations().controllerRuntime().stateTime("Player.Main"),
                "observer restores controller choice and time with case-normalized playback ownership");
        require(remote.owner().getMolangContext().variables.getPath("remote_entries") == null
                        && remote.owner().getMolangContext().variables.getPath("preview_events") == null,
                "snapshot does not replay historical controller entry or zero-time animation events");
        require(((Number) remote.owner().getMolangContext().variables.getPath("choice")).floatValue() == 3,
                "imported radio option executes its persistent state command");
        observerModel.apply(decoded.state());
        require(((Number) remote.owner().getMolangContext().variables.getPath("choice_calls")).floatValue() == 1,
                "repeated same control value does not replay its command");
        require(!remote.actions().trigger("gui"), "observer input cannot trigger owner action");
        require(!remote.owner().controls.setValue(remote.owner(), "ysm.width_scale", 1.5)
                        && Math.abs(remote.widthScale() - 0.8f) < 0.0001,
                "observer local control input cannot override authoritative owner state");
        var engine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
        try (var effects = source.functions().beginClientEffects(null)) {
            ExpressionEvaluator.evaluator(owner.getMolangContext())
                    .evalAll(engine.parse("ysm.sync(99,7);"), true);
        }
        var outgoingEvents = source.functions().drainSyncEvents();
        require(outgoingEvents.size() == 2, "owner captures authored sync and its nested local event in order");
        var receiver = new YsmSyncReceiver(owner.owner,
                sourceModel.contentHash(), sourceModel.schemaHash());
        receiver.accept(owner.owner, decoded);
        var delta = new YsmSyncMessage(owner.owner, packet.session(),
                packet.contentHash(), packet.schemaHash(), 0, 1, 0, 11, false,
                source.functions().syncWatermark(),
                new YsmSyncMessage.State(null, null, null, null, null), outgoingEvents);
        receiver.accept(owner.owner, delta).forEach(remote.functions()::replaySync);
        receiver.accept(owner.owner, delta).forEach(remote.functions()::replaySync);
        require(((Number) remote.owner().getMolangContext().variables.getPath("sync_count")).floatValue() == 2,
                "runtime executes each received sync once without recursing into nested recapture");
        require(remote.functions().drainSyncEvents().isEmpty() && remote.functions().syncWatermark() == 0,
                "observer replay never becomes outgoing owner traffic");
        var sample = YsmAnimationPlayer.class.getDeclaredMethod(
                "sampleAnimations", ExpressionEvaluator.class, boolean.class);
        sample.setAccessible(true);
        for (var runtime : List.of(source, remote)) {
            sample.invoke(runtime.animations(), ExpressionEvaluator.evaluator(
                    runtime.owner().getMolangContext()), false);
        }
        var ownerPose = source.captureRenderPose(false).part("root").animPosRaw();
        var remotePose = remote.captureRenderPose(false).part("root").animPosRaw();
        require(Math.abs(ownerPose.x - remotePose.x) < 0.0001 && Math.abs(ownerPose.y - remotePose.y) < 0.0001
                && Math.abs(ownerPose.z - remotePose.z) < 0.0001, "actual sampled owner and observer bone poses match");
        var state = new LivingEntityRenderState();
        state.ageInTicks = 20;
        YsmRenderFrameClock.beginFrame();
        remote.updateAnimations(state, null);
        state.ageInTicks = 22;
        YsmRenderFrameClock.beginFrame();
        remote.updateAnimations(state, null);
        require(remote.animations().controllerRuntime().currentState("Player.Main").equals("b")
                        && remote.owner().getMolangContext().variables.getPath("remote_entries") == null,
                "observer update samples owner controller without local transition callbacks");
        require(remote.animations().controllerRuntime().stateTime("Player.Main")
                        > source.animations().controllerRuntime().stateTime("Player.Main")
                        && remote.actions().time("gui") > 0.5f,
                "observer interpolation advances controller and action clocks");
        require(remote.owner().getMolangContext().variables.getPath("preview_events") == null,
                "observer interpolation does not re-emit timeline one-shots");
        var invalid = new YsmSyncMessage.State(captured.texture(),
                List.of(new YsmSyncMessage.Control(65535,
                        YsmSyncMessage.ControlType.NUMBER, 1)),
                captured.actions(), captured.controllers(), captured.playbacks());
        try {
            observerModel.apply(invalid);
            throw new AssertionError("model-invalid state accepted");
        } catch (IllegalArgumentException expected) {
            require(remote.textureId().equals("c") && Math.abs(remote.widthScale() - 0.8f) < 0.0001,
                    "invalid model-local reference rejected before visible mutation");
        }
        try (var effects = source.functions().beginClientEffects(null)) {
            ExpressionEvaluator.evaluator(owner.getMolangContext()).evalAll(engine.parse("loop(70,{ysm.sync(1);});"), true);
        }
        require(source.functions().drainSyncEvents().size() == YsmSyncMessage.MAX_EVENTS
                        && source.functions().droppedSyncEvents() == 6,
                "owner event backlog is bounded and overflow is counted");
        System.out.println("M7 independent native model sync: content/schema identity, control/texture/action/controller/pose and observer authority PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
