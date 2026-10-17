package org.figuramc.figura.backend2.protocol;

import com.google.protobuf.ByteString;
import org.figuramc.figura.backend2.protocol.generated.Cloud;
import org.figuramc.figura.model.ysm.sync.YsmSyncCodec;
import org.figuramc.figura.model.ysm.sync.YsmSyncMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class CloudAdapterSmokeTest {
    public static void main(String[] args) throws Exception {
        Path protocol = Path.of(args[0]);
        for (String line : Files.readAllLines(protocol.resolve("m7-vectors.tsv"))) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("snapshot_packet")) {
                continue;
            }
            String[] parts = line.split("\t");
            YsmSyncMessage message = YsmSyncCodec.decode(HexFormat.of().parseHex(parts[1]));
            require(YsmProtobuf.state(YsmProtobuf.state(message.state())).equals(message.state()),
                    "All state fields/presence preserve M7 semantics: " + parts[0]);
        }
        byte[] snapshotWire = Files.readAllLines(protocol.resolve("m7-vectors.tsv")).stream()
                .filter(line -> line.startsWith("snapshot\t")).map(line -> HexFormat.of().parseHex(line.split("\t")[1]))
                .findFirst().orElseThrow();
        YsmSyncMessage original = YsmSyncCodec.decode(snapshotWire);
        var publisher = new YsmProtobuf.Publisher();
        var snapshot = publisher.encode(original, 7).getFirst().setMajor(1).build();
        var decoded = YsmProtobuf.decode(CloudWire.decode(snapshot.toByteArray()));
        require(decoded.state().equals(original.state()) && decoded.events().isEmpty()
                && decoded.eventWatermark() == 2 && decoded.sequence() == 0 && decoded.baseSequence() == -1,
                "Snapshot watermarks historical events without replay");
        var event = new YsmSyncMessage.Event(3, YsmSyncMessage.EventKind.ACTION, List.of(3f));
        var delta = new YsmSyncMessage(original.owner(), original.session(), original.contentHash(), original.schemaHash(),
                original.generation(), 11, 10, 21, false, 3,
                new YsmSyncMessage.State(null, List.of(), null, null, null), List.of(event));
        var output = publisher.encode(delta, 7);
        require(output.size() == 2, "State/events use distinct typed bodies");
        var deltaDecoded = YsmProtobuf.decode(output.get(0).setMajor(1).build());
        var eventDecoded = YsmProtobuf.decode(output.get(1).setMajor(1).build());
        require(deltaDecoded.sequence() == 1 && deltaDecoded.baseSequence() == 0 && deltaDecoded.eventWatermark() == 2
                && deltaDecoded.state().controls().isEmpty() && deltaDecoded.state().actions() == null,
                "Delta clock, watermark and explicit empty partition");
        require(eventDecoded.sequence() == 2 && eventDecoded.baseSequence() == 1 && eventDecoded.events().equals(List.of(event)),
                "Instant event follows the canonical state sequence");

        var controls = Cloud.Controls.newBuilder();
        for (int index = 0; index < 513; index++) {
            controls.addValues(Cloud.Control.newBuilder().setIndex(index).setType(Cloud.ControlType.NUMBER));
        }
        rejected(Cloud.Envelope.newBuilder().setMajor(1).setYsmDelta(Cloud.YsmDelta.newBuilder()
                .setState(Cloud.YsmState.newBuilder().setControls(controls))).build().toByteArray());
        rejected(new byte[65537]);
        rejected(new byte[] {8, 1, 82, 5, 1});
        var unknownControl = snapshot.toBuilder().setYsmSnapshot(snapshot.getYsmSnapshot().toBuilder()
                .setState(snapshot.getYsmSnapshot().getState().toBuilder().setControls(Cloud.Controls.newBuilder()
                        .addValues(Cloud.Control.newBuilder().setTypeValue(999))))).build();
        try {
            YsmProtobuf.decode(unknownControl);
            throw new AssertionError("Unknown control accepted");
        } catch (IOException expected) {
            // Formal enums must not silently map to model indices.
        }

        CloudBudget lower = new CloudBudget();
        lower.configure(1, 128);
        require(lower.take(128) && !lower.take(1), "Negotiated lower message/byte budgets win");
        CloudBudget higher = new CloudBudget();
        higher.configure(1000, 1048576);
        for (int index = 0; index < 24; index++) {
            require(higher.take(1), "Local message allowance");
        }
        require(!higher.take(1), "Server cannot raise local control ceiling");

        AtomicInteger applied = new AtomicInteger();
        var old = new CloudConnection(1, new FiguraCloudProtocol(URI.create("https://old.invalid"), "synthetic", HttpClient.newHttpClient()));
        require(old.post(128, applied::incrementAndGet), "Old callback queued");
        old.close();
        require(!old.post(128, applied::incrementAndGet), "Retired HTTP/WS callback rejected");
        old.drain();
        require(applied.get() == 0, "Old queue cleared on switch");
        var current = new CloudConnection(2, new FiguraCloudProtocol(URI.create("https://new.invalid"), "synthetic", HttpClient.newHttpClient()));
        require(current.post(128, applied::incrementAndGet), "New callback accepted");
        current.drain();
        require(applied.get() == 1, "Current epoch applies once");
        require(!current.post(262145, applied::incrementAndGet) && !current.active(), "Receive byte overflow requires recovery");
        require(CloudHttp.relative(URI.create("https://one.invalid"), "/api/protobuf/v1/resources/a").getHost().equals("one.invalid"),
                "Authorized resource stays on selected origin");
        try {
            CloudHttp.relative(URI.create("https://one.invalid"), "//two.invalid/file");
            throw new AssertionError("Foreign resource origin accepted");
        } catch (IOException expected) {
            // Transfer paths cannot turn into an authority supplied by the server.
        }
        System.out.println("PASS cloud adapter mapping: production M7 vectors, all partitions, canonical event clocks, budgets and epoch retirement");
    }

    private static void rejected(byte[] bytes) throws Exception {
        try {
            CloudWire.decode(bytes);
            throw new AssertionError("Invalid control wire accepted");
        } catch (IOException expected) {
            // Bounds precede generated decoding.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
