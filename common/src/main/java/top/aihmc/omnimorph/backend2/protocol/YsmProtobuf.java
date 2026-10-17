package top.aihmc.omnimorph.backend2.protocol;

import com.google.protobuf.ByteString;
import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Maps model state objects to generated protobuf fields; no M7 bytes enter the new wire. */
public final class YsmProtobuf {
    private static final HexFormat HEX = HexFormat.of();

    private YsmProtobuf() {}

    public static Cloud.YsmState state(YsmSyncMessage.State state) {
        var result = Cloud.YsmState.newBuilder();
        if (state.texture() != null) {
            result.setTexture(state.texture());
        }
        if (state.controls() != null) {
            var controls = Cloud.Controls.newBuilder();
            for (var value : state.controls()) {
                controls.addValues(Cloud.Control.newBuilder().setIndex(value.index())
                        .setTypeValue(value.type().ordinal() + 1).setValue(value.value()));
            }
            result.setControls(controls);
        }
        if (state.actions() != null) {
            var actions = Cloud.Actions.newBuilder();
            for (var value : state.actions()) {
                actions.addValues(Cloud.Action.newBuilder().setIndex(value.index()).setActive(value.active())
                        .setTime(value.time()).setSpeed(value.speed()).setLoop(value.loop()));
            }
            result.setActions(actions);
        }
        if (state.controllers() != null) {
            var controllers = Cloud.Controllers.newBuilder();
            for (var value : state.controllers()) {
                controllers.addValues(Cloud.Controller.newBuilder().setIndex(value.index())
                        .setState(value.state()).setTime(value.time()));
            }
            result.setControllers(controllers);
        }
        if (state.playbacks() != null) {
            var playbacks = Cloud.Playbacks.newBuilder();
            for (var value : state.playbacks()) {
                playbacks.addValues(Cloud.Playback.newBuilder().setSlot(value.slot()).setClip(value.clip())
                        .setController(value.controller()).setTime(value.time()).setSpeed(value.speed())
                        .setWeight(value.weight()).setTargetWeight(value.targetWeight()).setLoopMode(value.loopMode())
                        .setFadeSpeed(value.fadeSpeed()).setFlags(value.flags()));
            }
            result.setPlaybacks(playbacks);
        }
        return result.build();
    }

    public static YsmSyncMessage.State state(Cloud.YsmState state) throws IOException {
        if (state.getControls().getValuesCount() > 512 || state.getActions().getValuesCount() > 512
                || state.getControllers().getValuesCount() > 512 || state.getPlaybacks().getValuesCount() > 512) {
            throw new IOException("State exceeds entry budget");
        }
        var controls = new ArrayList<YsmSyncMessage.Control>();
        for (var value : state.getControls().getValuesList()) {
            if (value.getTypeValue() < 1 || value.getTypeValue() > 4) {
                throw new IOException("Unknown control type");
            }
            controls.add(new YsmSyncMessage.Control(value.getIndex(),
                    YsmSyncMessage.ControlType.values()[value.getTypeValue() - 1], value.getValue()));
        }
        var actions = state.getActions().getValuesList().stream().map(value ->
                new YsmSyncMessage.Action(value.getIndex(), value.getActive(), value.getTime(), value.getSpeed(), value.getLoop())).toList();
        var controllers = state.getControllers().getValuesList().stream().map(value ->
                new YsmSyncMessage.Controller(value.getIndex(), value.getState(), value.getTime())).toList();
        var playbacks = state.getPlaybacks().getValuesList().stream().map(value ->
                new YsmSyncMessage.Playback(value.getSlot(), value.getClip(), value.getController(), value.getTime(),
                        value.getSpeed(), value.getWeight(), value.getTargetWeight(), value.getLoopMode(),
                        value.getFadeSpeed(), value.getFlags())).toList();
        return new YsmSyncMessage.State(state.hasTexture() ? state.getTexture() : null,
                state.hasControls() ? controls : null, state.hasActions() ? actions : null,
                state.hasControllers() ? controllers : null, state.hasPlaybacks() ? playbacks : null);
    }

    private static Cloud.YsmIdentity.Builder identity(YsmSyncMessage message, long revision, long sequence,
                                                       long base, long watermark) {
        return Cloud.YsmIdentity.newBuilder().setOwner(message.owner().toString()).setAssetRevision(revision)
                .setPublisherSession(message.session().toString()).setGeneration(message.generation())
                .setSequence(sequence).setBaseSequence(base).setOwnerTick(message.ownerTick())
                .setContentSha256(ByteString.copyFrom(HEX.parseHex(message.contentHash())))
                .setSchemaSha256(ByteString.copyFrom(HEX.parseHex(message.schemaHash())))
                .setEventWatermark(watermark);
    }

    public static YsmSyncMessage decode(Cloud.Envelope envelope) throws IOException {
        Cloud.YsmIdentity identity;
        Cloud.YsmState state;
        boolean snapshot;
        List<Cloud.Event> events;
        switch (envelope.getBodyCase()) {
            case YSM_SNAPSHOT -> {
                identity = envelope.getYsmSnapshot().getIdentity();
                state = envelope.getYsmSnapshot().getState();
                snapshot = true;
                events = List.of();
            }
            case YSM_DELTA -> {
                identity = envelope.getYsmDelta().getIdentity();
                state = envelope.getYsmDelta().getState();
                snapshot = false;
                events = List.of();
            }
            case YSM_EVENT -> {
                identity = envelope.getYsmEvent().getIdentity();
                state = Cloud.YsmState.getDefaultInstance();
                snapshot = false;
                events = envelope.getYsmEvent().getEventsList();
            }
            default -> throw new IOException("Not a YSM message");
        }
        if (envelope.getSerializedSize() > CloudHttp.CONTROL_BYTES || identity.getAssetRevision() <= 0
                || identity.getContentSha256().size() != 32 || identity.getSchemaSha256().size() != 32
                || events.size() > 64) {
            throw new IOException("Invalid YSM identity or budget");
        }
        var decodedEvents = new ArrayList<YsmSyncMessage.Event>();
        for (var event : events) {
            if (event.getKindValue() < 1 || event.getKindValue() > 4 || event.getArgumentsCount() > 16) {
                throw new IOException("Invalid YSM event");
            }
            decodedEvents.add(new YsmSyncMessage.Event(event.getSequence(),
                    YsmSyncMessage.EventKind.values()[event.getKindValue() - 1], event.getArgumentsList()));
        }
        try {
            return new YsmSyncMessage(UUID.fromString(identity.getOwner()), UUID.fromString(identity.getPublisherSession()),
                    HEX.formatHex(identity.getContentSha256().toByteArray()), HEX.formatHex(identity.getSchemaSha256().toByteArray()),
                    identity.getGeneration(), identity.getSequence(), identity.getBaseSequence(), identity.getOwnerTick(),
                    snapshot, identity.getEventWatermark(), state(state), decodedEvents);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid YSM state", exception);
        }
    }

    /** Canonical sequence advances separately for state and one-shot events. */
    public static final class Publisher {
        private YsmSyncMessage previous;
        private long revision;
        private long sequence = -1;
        private long watermark;
        private List<Cloud.Envelope> lastOutput = List.of();

        public List<Cloud.Envelope.Builder> encode(YsmSyncMessage message, long assetRevision) throws IOException {
            boolean same = previous != null && revision == assetRevision
                    && previous.session().equals(message.session()) && previous.generation() == message.generation();
            if (same && message.equals(previous)) {
                return lastOutput.stream().map(Cloud.Envelope::toBuilder).toList();
            }
            if (!same && !message.snapshot()) {
                throw new IOException("Publisher needs full snapshot");
            }
            if (same && (message.sequence() <= previous.sequence()
                    || !message.snapshot() && message.baseSequence() != previous.sequence()
                    || !message.contentHash().equals(previous.contentHash())
                    || !message.schemaHash().equals(previous.schemaHash()))) {
                throw new IOException("Conflicting publisher source cursor");
            }
            long next = same ? sequence : -1;
            long previousWatermark = same ? watermark : message.eventWatermark();
            if (message.eventWatermark() < previousWatermark) {
                throw new IOException("Regressing event watermark");
            }
            var fresh = message.events().stream().filter(value -> value.sequence() > previousWatermark).toList();
            long expected = previousWatermark;
            for (var event : fresh) {
                if (event.sequence() != ++expected) {
                    throw new IOException("YSM event gap");
                }
            }
            if (expected != message.eventWatermark()) {
                throw new IOException("YSM watermark gap");
            }
            var result = new ArrayList<Cloud.Envelope>();
            long base = next;
            var identity = identity(message, assetRevision, ++next, message.snapshot() ? -1 : base, previousWatermark);
            Cloud.YsmState state = state(message.state());
            if (message.snapshot()) {
                result.add(Cloud.Envelope.newBuilder().setYsmSnapshot(Cloud.YsmSnapshot.newBuilder()
                        .setIdentity(identity).setState(state)).build());
            } else {
                result.add(Cloud.Envelope.newBuilder().setYsmDelta(Cloud.YsmDelta.newBuilder()
                        .setIdentity(identity).setState(state)).build());
            }
            if (!fresh.isEmpty()) {
                base = next;
                var eventBody = Cloud.YsmEvent.newBuilder().setIdentity(identity(message, assetRevision,
                        ++next, base, message.eventWatermark()));
                for (var value : fresh) {
                    eventBody.addEvents(Cloud.Event.newBuilder().setSequence(value.sequence())
                            .setKindValue(value.kind().ordinal() + 1).addAllArguments(value.arguments()));
                }
                result.add(Cloud.Envelope.newBuilder().setYsmEvent(eventBody).build());
            }
            for (var body : result) {
                int size = switch (body.getBodyCase()) {
                    case YSM_SNAPSHOT -> body.getYsmSnapshot().getSerializedSize();
                    case YSM_DELTA -> body.getYsmDelta().getSerializedSize();
                    default -> body.getYsmEvent().getSerializedSize();
                };
                if (size > 32768) {
                    throw new IOException("YSM body exceeds byte budget");
                }
            }
            previous = message;
            revision = assetRevision;
            sequence = next;
            watermark = expected;
            lastOutput = List.copyOf(result);
            return result.stream().map(Cloud.Envelope::toBuilder).toList();
        }
    }
}
