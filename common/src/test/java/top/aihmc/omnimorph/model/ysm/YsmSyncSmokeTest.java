package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.sync.YsmSyncCodec;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncPackets;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncPingBudget;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncReceiver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Pure protocol tests; does not claim a real backend or two running game clients. */
public final class YsmSyncSmokeTest {
    private static final UUID OWNER = new UUID(1, 2);
    private static final UUID SESSION = new UUID(3, 4);
    private static final String CONTENT = "01".repeat(32);
    private static final String SCHEMA = "ab".repeat(32);

    private YsmSyncSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        var initial = new YsmSyncMessage.State(2,
                List.of(new YsmSyncMessage.Control(0, YsmSyncMessage.ControlType.BOOLEAN, 1),
                        new YsmSyncMessage.Control(1, YsmSyncMessage.ControlType.NUMBER, 0.375),
                        new YsmSyncMessage.Control(2, YsmSyncMessage.ControlType.COLOR, 0xffffffffL),
                        new YsmSyncMessage.Control(3, YsmSyncMessage.ControlType.OPTION, 7)),
                List.of(new YsmSyncMessage.Action(0, true, 1.5f, -0.5f, true)),
                List.of(new YsmSyncMessage.Controller(0, 3, 0.25f)),
                List.of(new YsmSyncMessage.Playback(0, 1, -1, 0.5f, 1, 0.75f, 1, 2)));
        var snapshot = message(SESSION, 0, 0, -1, true, 8, initial, List.of());
        byte[] encoded = YsmSyncCodec.encode(snapshot);
        require(snapshot.equals(YsmSyncCodec.decode(encoded)), "complete state round trip");
        for (int i = 0; i < encoded.length; i++) {
            expectDecodeFailure(Arrays.copyOf(encoded, i));
        }
        expectDecodeFailure(Arrays.copyOf(encoded, encoded.length + 1));
        expectDecodeFailure(new byte[YsmSyncCodec.MAX_BYTES + 1]);
        byte[] malformed = encoded.clone();
        malformed[4] = 99;
        expectDecodeFailure(malformed);
        malformed = encoded.clone();
        malformed[133] = 2; // canonical boolean after four long clocks
        expectDecodeFailure(malformed);
        malformed = encoded.clone();
        malformed[142] = 32; // unknown section flag
        expectDecodeFailure(malformed);
        malformed = encoded.clone();
        ByteBuffer.wrap(malformed).putShort(147, (short) (YsmSyncMessage.MAX_ENTRIES + 1));
        expectDecodeFailure(malformed);
        malformed = encoded.clone();
        malformed[151] = 99; // unknown first control type
        expectDecodeFailure(malformed);
        malformed = encoded.clone();
        ByteBuffer.wrap(malformed).putDouble(152, Double.NaN);
        expectDecodeFailure(malformed);
        malformed = encoded.clone();
        ByteBuffer.wrap(malformed).putShort(160, (short) 0); // duplicate control index
        expectDecodeFailure(malformed);
        expectInvalid(() -> new YsmSyncMessage.Control(0, YsmSyncMessage.ControlType.NUMBER, Double.NaN));
        expectInvalid(() -> new YsmSyncMessage.Control(0, YsmSyncMessage.ControlType.BOOLEAN, 2));
        expectInvalid(() -> new YsmSyncMessage.Control(0, YsmSyncMessage.ControlType.COLOR, -1));
        expectInvalid(() -> new YsmSyncMessage.Control(0, YsmSyncMessage.ControlType.OPTION, 1.5));
        expectInvalid(() -> new YsmSyncMessage.Action(0, true, -1, 1, false));
        expectInvalid(() -> new YsmSyncMessage.Playback(0, 0, -1, 0, 1, Float.POSITIVE_INFINITY, 1, 0));
        expectInvalid(() -> new YsmSyncMessage.State(0, List.of(initial.controls().getFirst(), initial.controls().getFirst()),
                List.of(), List.of(), List.of()));
        expectInvalid(() -> new YsmSyncMessage.Event(1, java.util.Collections.nCopies(17, 1f)));
        expectInvalid(() -> new YsmSyncMessage.Event(0, List.of()));
        expectInvalid(() -> message(SESSION, 0, 1, -1, true, 8,
                new YsmSyncMessage.State(null, null, null, null, null), List.of()));
        expectInvalid(() -> message(SESSION, 0, 1, 1, false, 8, initial, List.of()));
        List<YsmSyncMessage.Playback> oversizedPlaybacks = new ArrayList<>();
        List<YsmSyncMessage.Control> oversizedControls = new ArrayList<>();
        List<YsmSyncMessage.Action> oversizedActions = new ArrayList<>();
        List<YsmSyncMessage.Controller> oversizedControllers = new ArrayList<>();
        for (int i = 0; i < YsmSyncMessage.MAX_ENTRIES; i++) {
            oversizedPlaybacks.add(new YsmSyncMessage.Playback(i, 0, -1, 0, 1, 1, 1, 0));
            oversizedControls.add(new YsmSyncMessage.Control(i, YsmSyncMessage.ControlType.NUMBER, 1));
            oversizedActions.add(new YsmSyncMessage.Action(i, true, 0, 1, false));
            oversizedControllers.add(new YsmSyncMessage.Controller(i, 0, 0));
        }
        List<YsmSyncMessage.Event> maximumEvents = new ArrayList<>();
        for (int i = 1; i <= YsmSyncMessage.MAX_EVENTS; i++) {
            maximumEvents.add(new YsmSyncMessage.Event(i, java.util.Collections.nCopies(16, 1f)));
        }
        var oversized = message(SESSION, 0, 1, 0, false, YsmSyncMessage.MAX_EVENTS,
                new YsmSyncMessage.State(0, oversizedControls, oversizedActions, oversizedControllers, oversizedPlaybacks),
                maximumEvents);
        try {
            YsmSyncCodec.encode(oversized);
            throw new AssertionError("oversized local message accepted");
        } catch (IOException expected) {
            // Individually legal sections must still fit the total message budget.
        }

        List<YsmSyncMessage.Control> mutable = new ArrayList<>(initial.controls());
        var copied = new YsmSyncMessage.State(0, mutable, List.of(), List.of(), List.of());
        mutable.clear();
        require(copied.controls().size() == 4, "DTO owns immutable list snapshots");
        var changed = new YsmSyncMessage.State(4, null, List.of(),
                List.of(new YsmSyncMessage.Controller(0, 5, 2)), null);
        var event1 = new YsmSyncMessage.Event(9, List.of(12f, -3f));
        var event2 = new YsmSyncMessage.Event(10, List.of(8f));
        var first = message(SESSION, 0, 1, 0, false, 9, changed, List.of(event1));
        var second = message(SESSION, 0, 2, 1, false, 10,
                new YsmSyncMessage.State(null, List.of(), null, null, List.of()), List.of(event2));
        var ordered = receiver();
        var reordered = receiver();
        ordered.accept(OWNER, roundTrip(snapshot));
        reordered.accept(OWNER, roundTrip(snapshot));
        List<YsmSyncMessage.Event> trace = new ArrayList<>(ordered.accept(OWNER, roundTrip(first)));
        trace.addAll(ordered.accept(OWNER, roundTrip(second)));
        require(reordered.accept(OWNER, roundTrip(second)).isEmpty() && reordered.pendingCount() == 1,
                "out of order event waits for its baseline");
        require(trace.equals(reordered.accept(OWNER, roundTrip(first))), "two endpoints replay identical ordered event trace");
        require(ordered.state().equals(reordered.state()) && reordered.sequence() == 2, "semantic state converges");
        require(reordered.state().actions().isEmpty() && reordered.state().controls().isEmpty()
                && reordered.state().texture() == 4, "present empty clears, null retains");
        require(reordered.accept(OWNER, second).isEmpty(), "duplicate does not repeat one-shot event");
        require(reordered.accept(new UUID(9, 9), message(SESSION, 0, 3, 2, false, 10, changed, List.of())).isEmpty()
                && reordered.sequence() == 2, "authenticated owner is authoritative");

        var late = receiver();
        var latest = message(SESSION, 0, 4, -1, true, 10, ordered.state(), List.of());
        require(late.accept(OWNER, roundTrip(latest)).isEmpty() && late.state().equals(ordered.state()),
                "late join snapshot converges without historical one-shots");
        var replaced = message(SESSION, 1, 0, -1, true, 0, initial, List.of());
        reordered.accept(OWNER, replaced);
        reordered.accept(OWNER, latest);
        require(reordered.sequence() == 0 && reordered.state().equals(initial), "old generation rejected");
        var reconnect = message(new UUID(5, 6), 0, 0, -1, true, 0, changed.apply(initial), List.of());
        reordered.accept(OWNER, reconnect);
        reordered.accept(OWNER, replaced);
        require(reordered.sequence() == 0 && reordered.state().equals(initial), "retired connection session rejected");
        for (int i = 2; i < 100; i++) {
            late.accept(OWNER, message(SESSION, 0, i + 10, i + 9, false, 10, changed, List.of()));
        }
        require(late.pendingCount() == YsmSyncReceiver.MAX_PENDING, "reorder buffer bounded");
        late.accept(OWNER, message(SESSION, 0, 200, -1, true, 10, initial, List.of()));
        require(late.pendingCount() == 0 && late.state().equals(initial), "periodic snapshot recovers missing deltas");
        var wrongContent = new YsmSyncMessage(OWNER, SESSION, "ff".repeat(32), SCHEMA, 0, 201, -1, 201,
                true, 10, changed.apply(initial), List.of());
        late.accept(OWNER, wrongContent);
        require(late.sequence() == 200, "wrong model digest cannot mutate observer");
        testPackets(encoded);
        testPingBudget();
        System.out.println("YSM sync codec/replay PASS: bounded DTO, truncation, ordered/late/reconnect state and event deduplication");
    }

    private static void testPingBudget() {
        var budget = new YsmSyncPingBudget();
        require(budget.canSend(0, 480), "initial packet allowed");
        budget.recordSent(0, 480);
        budget.recordSent(1, 480);
        require(!budget.canSend(1, 480) && !budget.canSend(19, 480), "rolling total includes headers and previous tick");
        require(budget.canSend(20, 480), "expired bytes release quota");
        budget.recordSent(20, 480);
        require(!budget.canSend(20, 480), "window boundary cannot burst past byte budget");
        budget.clear();
        budget.recordSent(0, 900); // Ordinary Figura Ping, not fragmented YSM traffic.
        require(!budget.canSend(1, 480), "ordinary Ping traffic consumes shared budget");
        budget.recordSent(1, 900);
        require(!budget.canSend(20, 480) && budget.canSend(21, 480), "saturated ordinary traffic remains blocked for full window");
        budget.clear();
        for (int i = 0; i < 32; i++) {
            budget.recordSent(1, 0);
        }
        require(!budget.canSend(1, 0) && budget.canSend(21, 0), "message count capped independently of bytes");
        budget.recordSent(22, 480);
        require(budget.canSend(0, 480), "clock reset drops old connection quota");
    }

    private static void testPackets(byte[] encoded) throws IOException {
        var packets = YsmSyncPackets.fragment(7, encoded);
        require(YsmSyncPackets.recognizes(YsmSyncPackets.PING_ID, packets.getFirst()), "reserved ID and magic recognized together");
        require(!YsmSyncPackets.recognizes(7, packets.getFirst())
                && !YsmSyncPackets.recognizes(YsmSyncPackets.PING_ID, encoded), "ordinary Figura Ping payloads are not captured");
        var incoming = new YsmSyncPackets();
        require(Arrays.equals(encoded, incoming.accept(OWNER, packets.getFirst(), 0)), "single fragment payload exact");
        byte[] large = new byte[YsmSyncCodec.MAX_BYTES];
        new java.util.Random(17).nextBytes(large);
        var fragmented = YsmSyncPackets.fragment(8, large);
        for (byte[] packet : fragmented) {
            require(packet.length <= YsmSyncPackets.MAX_PACKET_BYTES, "every payload fits packet budget");
        }
        require(incoming.accept(OWNER, fragmented.getLast(), 1) == null, "last fragment may arrive first");
        require(incoming.accept(OWNER, fragmented.getLast(), 2) == null, "exact duplicate does not complete message early");
        for (int i = fragmented.size() - 2; i > 0; i--) {
            require(incoming.accept(OWNER, fragmented.get(i), 3) == null, "reverse assembly waits for first fragment");
        }
        require(Arrays.equals(large, incoming.accept(OWNER, fragmented.getFirst(), 4)) && incoming.pendingCount() == 0,
                "maximum-sized reverse assembly exact and released");
        incoming.accept(OWNER, fragmented.getFirst(), 5);
        UUID other = new UUID(7, 8);
        for (int i = 1; i < fragmented.size(); i++) {
            require(incoming.accept(other, fragmented.get(i), 6) == null, "different authenticated owner cannot finish assembly");
        }
        require(incoming.pendingCount() == 2, "assemblies keyed by authenticated owner");
        incoming.clear();
        require(incoming.pendingCount() == 0, "disconnect clears partial messages");
        incoming.accept(OWNER, fragmented.getFirst(), 10);
        incoming.accept(OWNER, YsmSyncPackets.fragment(9, large).getFirst(), 10);
        incoming.accept(OWNER, YsmSyncPackets.fragment(10, large).getFirst(), 10);
        require(incoming.pendingCount() == 2, "per-owner assembly limit");
        incoming.expire(10 + YsmSyncPackets.EXPIRY_TICKS);
        require(incoming.pendingCount() == 0, "missing fragments expire");
        for (int i = 0; i < 100; i++) {
            incoming.accept(new UUID(100, i), fragmented.getFirst(), 2000);
        }
        require(incoming.pendingCount() == YsmSyncPackets.MAX_IN_FLIGHT, "global memory bound");
        incoming.clear();
        incoming.accept(OWNER, fragmented.getFirst(), 2001);
        byte[] conflicting = fragmented.getFirst().clone();
        conflicting[conflicting.length - 1] ^= 1;
        expectPacketFailure(incoming, conflicting, 2002);
        require(incoming.pendingCount() == 0, "conflicting duplicate drops assembly");
        byte[] badChecksum = packets.getFirst().clone();
        badChecksum[badChecksum.length - 1] ^= 1;
        expectPacketFailure(incoming, badChecksum, 2003);
        for (int i = 0; i < packets.getFirst().length; i++) {
            expectPacketFailure(incoming, Arrays.copyOf(packets.getFirst(), i), 2004);
        }
        byte[] invalidLength = fragmented.getFirst().clone();
        ByteBuffer.wrap(invalidLength).putInt(13, Integer.MAX_VALUE);
        expectPacketFailure(incoming, invalidLength, 2005);
        byte[] invalidCount = fragmented.getFirst().clone();
        ByteBuffer.wrap(invalidCount).putShort(19, (short) 65535);
        expectPacketFailure(incoming, invalidCount, 2006);
        expectPacketFailure(incoming, new byte[YsmSyncPackets.MAX_PACKET_BYTES + 1], 2007);
    }

    private static void expectPacketFailure(YsmSyncPackets incoming, byte[] packet, long tick) throws IOException {
        try {
            incoming.accept(OWNER, packet, tick);
            throw new AssertionError("malformed fragment accepted");
        } catch (IOException expected) {
            // Malformed remote fragments fail without unbounded allocation or unchecked exceptions.
        }
    }

    private static YsmSyncReceiver receiver() {
        return new YsmSyncReceiver(OWNER, CONTENT, SCHEMA);
    }

    private static YsmSyncMessage message(UUID session, long generation, long sequence, long base, boolean snapshot,
                                          long watermark, YsmSyncMessage.State state, List<YsmSyncMessage.Event> events) {
        return new YsmSyncMessage(OWNER, session, CONTENT, SCHEMA, generation, sequence, base, sequence,
                snapshot, watermark, state, events);
    }

    private static YsmSyncMessage roundTrip(YsmSyncMessage message) throws IOException {
        var decoded = YsmSyncCodec.decode(YsmSyncCodec.encode(message));
        require(decoded.equals(message), "wire round trip");
        return decoded;
    }

    private static void expectDecodeFailure(byte[] bytes) throws IOException {
        try {
            YsmSyncCodec.decode(bytes);
            throw new AssertionError("malformed packet accepted");
        } catch (IOException expected) {
            // Expected protocol rejection; runtime errors must not masquerade as a controlled failure.
        }
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("invalid DTO accepted");
        } catch (IllegalArgumentException expected) {
            // Constructor invariants apply to locally produced and decoded messages alike.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
