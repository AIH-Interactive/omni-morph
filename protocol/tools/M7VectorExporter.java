import top.aihmc.omnimorph.model.ysm.sync.YsmSyncCodec;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncPackets;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Synthetic interoperability vectors produced by the actual client M7 codecs. */
public final class M7VectorExporter {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID SESSION = new UUID(0, 2);
    private static final String CONTENT = "11".repeat(32);
    private static final String SCHEMA = "22".repeat(32);

    private static YsmSyncMessage message(long sequence, long base, long tick, boolean snapshot,
                                          long watermark, YsmSyncMessage.State state,
                                          List<YsmSyncMessage.Event> events) {
        return new YsmSyncMessage(OWNER, SESSION, CONTENT, SCHEMA, 3, sequence, base, tick,
                snapshot, watermark, state, events);
    }

    private static void append(StringBuilder output, String name, byte[] bytes) {
        output.append(name).append('\t').append(HexFormat.of().formatHex(bytes)).append('\n');
    }

    public static void main(String[] args) throws Exception {
        boolean verify = args.length == 2 && args[0].equals("--verify");
        Path target = Path.of(args[verify ? 1 : 0]);
        var state = new YsmSyncMessage.State(-1,
                List.of(new YsmSyncMessage.Control(0, YsmSyncMessage.ControlType.BOOLEAN, 1),
                        new YsmSyncMessage.Control(1, YsmSyncMessage.ControlType.NUMBER, 1.25),
                        new YsmSyncMessage.Control(2, YsmSyncMessage.ControlType.COLOR, 0xffffffffL)),
                List.of(new YsmSyncMessage.Action(3, true, 2.5f, -1, true)),
                List.of(new YsmSyncMessage.Controller(4, 5, 6.5f)),
                List.of(new YsmSyncMessage.Playback(6, 7, 4, 1.5f, 0.5f, 0.25f, 1, 2, 0.75f, 7)));
        var snapshot = message(10, -1, 20, true, 2, state,
                List.of(new YsmSyncMessage.Event(1, YsmSyncMessage.EventKind.SYNC, List.of(0.5f, -1.25f)),
                        new YsmSyncMessage.Event(2, YsmSyncMessage.EventKind.PLAY, List.of(7f, 2f, 0.5f, 0.25f))));
        var delta = message(11, 10, 21, false, 2,
                new YsmSyncMessage.State(null, List.of(), null, null, null), List.of());
        var event = message(12, 11, 22, false, 3,
                new YsmSyncMessage.State(null, null, null, null, null),
                List.of(new YsmSyncMessage.Event(3, YsmSyncMessage.EventKind.ACTION, List.of(3f))));
        StringBuilder output = new StringBuilder("# Synthetic M7 vectors from production Java codec; no model resources.\n");
        for (var entry : List.of(java.util.Map.entry("snapshot", snapshot),
                java.util.Map.entry("delta", delta), java.util.Map.entry("event_delta", event))) {
            byte[] bytes = YsmSyncCodec.encode(entry.getValue());
            if (!YsmSyncCodec.decode(bytes).equals(entry.getValue())) {
                throw new AssertionError("Java M7 round trip: " + entry.getKey());
            }
            append(output, entry.getKey(), bytes);
        }
        byte[] encoded = YsmSyncCodec.encode(snapshot);
        var packets = YsmSyncPackets.fragment(0x0102030405060708L, encoded);
        var reassembly = new YsmSyncPackets();
        for (int index = 0; index < packets.size(); index++) {
            append(output, "snapshot_packet_" + index, packets.get(index));
            byte[] assembled = reassembly.accept(OWNER, packets.get(index), 0);
            if (assembled != null && !java.util.Arrays.equals(assembled, encoded)) {
                throw new AssertionError("Java M7 fragment round trip");
            }
        }
        String result = output.toString();
        if (verify) {
            if (!Files.readString(target, StandardCharsets.UTF_8).equals(result)) {
                throw new AssertionError("M7 golden data differs from production Java output");
            }
        } else {
            Files.writeString(target, result, StandardCharsets.UTF_8);
        }
        System.out.println("PASS: three M7 message vectors and " + packets.size() + " fragment vector(s)");
    }
}
