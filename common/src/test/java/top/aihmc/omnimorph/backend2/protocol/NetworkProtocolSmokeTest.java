package top.aihmc.omnimorph.backend2.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;
import top.aihmc.omnimorph.backend2.websocket.C2SMessageHandler;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Executed by :common:test, independent of Minecraft bootstrap and live credentials. */
public final class NetworkProtocolSmokeTest {
    public static void main(String[] args) throws Exception {
        Map<String, byte[]> vectors = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(args[0], "vectors.tsv"))) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] fields = line.split("\t");
                vectors.put(fields[0], HexFormat.of().parseHex(fields[1]));
            }
        }

        var hello = Cloud.Envelope.newBuilder().setMajor(1).setRequestId(1)
                .setHello(Cloud.Hello.newBuilder().addSupportedMajors(1).setClientVersion("j")).build();
        verify(vectors, "hello", hello);
        var ping = Cloud.Envelope.newBuilder().setMajor(1).setRequestId(2)
                .setFiguraPing(Cloud.FiguraPing.newBuilder().setFunctionId(0x01020304)
                        .setEcho(true).setArguments(ByteString.copyFrom(new byte[]{0, -1, -128, 127}))).build();
        verify(vectors, "ping", ping);
        var clear = Cloud.Envelope.newBuilder().setMajor(1).setRequestId(3)
                .setYsmDelta(Cloud.YsmDelta.newBuilder().setState(Cloud.YsmState.newBuilder()
                        .setControls(Cloud.Controls.getDefaultInstance()))).build();
        verify(vectors, "clear_controls", clear);
        require(clear.getYsmDelta().getState().hasControls(), "Explicit empty controls lost");
        require(!clear.getYsmDelta().getState().hasActions(), "Omitted actions became present");
        require(!Cloud.YsmState.getDefaultInstance().hasTexture(), "Default texture became present");
        var snapshot = Cloud.Envelope.newBuilder().setMajor(1).setSessionId("publisher")
                .setConnectionEpoch(7).setYsmSnapshot(Cloud.YsmSnapshot.newBuilder()
                        .setIdentity(Cloud.YsmIdentity.newBuilder().setBaseSequence(-1))
                        .setState(Cloud.YsmState.newBuilder().setTexture(-1)
                                .setControls(Cloud.Controls.newBuilder().addValues(Cloud.Control.newBuilder()
                                        .setType(Cloud.ControlType.COLOR).setValue(0xffffffffL)))
                                .setActions(Cloud.Actions.getDefaultInstance())
                                .setControllers(Cloud.Controllers.getDefaultInstance())
                                .setPlaybacks(Cloud.Playbacks.getDefaultInstance()))).build();
        require(Cloud.Envelope.parseFrom(snapshot.toByteArray()).equals(snapshot), "M7 signed sentinel lost");
        var budget = ping.toBuilder().setFiguraPing(ping.getFiguraPing().toBuilder()
                .setArguments(ByteString.copyFrom(new byte[32768]))).build();
        require(budget.getSerializedSize() > 32768 && budget.getSerializedSize() < 65536,
                "Complete-envelope budget cannot carry the state budget");

        byte[] unknown = Arrays.copyOf(hello.toByteArray(), hello.getSerializedSize() + 3);
        unknown[unknown.length - 3] = (byte) 0xa0;
        unknown[unknown.length - 2] = 6;
        unknown[unknown.length - 1] = 7;
        require(Cloud.Envelope.parseFrom(unknown).getUnknownFields().hasField(100), "Unknown field lost");
        try {
            Cloud.Envelope.parseFrom(new byte[]{0x52, 5, 1});
            throw new AssertionError("Truncated body accepted");
        } catch (InvalidProtocolBufferException expected) {
            // Decoder must fail before business dispatch.
        }

        var owner = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        equal(vectors.get("legacy_auth"), C2SMessageHandler.auth("test-token").array());
        equal(vectors.get("legacy_ping"), C2SMessageHandler.ping(0x01020304, true,
                new byte[]{0, -1, -128, 127}).array());
        equal(vectors.get("legacy_sub"), C2SMessageHandler.sub(owner).array());
        equal(vectors.get("legacy_unsub"), C2SMessageHandler.unsub(owner).array());
        byte[] nbt = HexFormat.of().parseHex(Files.readString(Path.of(args[0], "figura-nbt.hex")).trim());
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(nbt))) {
            equal(new byte[]{10, 0, 0, 0}, gzip.readAllBytes());
        }
        var sha256 = MessageDigest.getInstance("SHA-256");
        require(HexFormat.of().formatHex(sha256.digest(nbt))
                .equals("51b2e69108970a9a8413720b7e3ebf94177acc8113e188b142c9cd7f0fcad695"),
                "Raw package identity changed");
        require(HexFormat.of().formatHex(sha256.digest(Base64.getEncoder().encode(nbt)))
                .equals("7d8e5d453e49f4875e7536c4f423af386fc1484d229dc904357da8832828cc2b"),
                "Legacy Figura identity changed");
        System.out.println("PASS network protocol: 3 shared Rust/Java vectors, presence, unknown fields, "
                + "truncation, 4 legacy Java frames");
    }

    private static void verify(Map<String, byte[]> vectors, String name, Cloud.Envelope expected)
            throws InvalidProtocolBufferException {
        byte[] wire = vectors.get(name);
        equal(wire, expected.toByteArray());
        require(Cloud.Envelope.parseFrom(wire).equals(expected), "Cross-language decode: " + name);
    }

    private static void equal(byte[] expected, byte[] actual) {
        require(Arrays.equals(expected, actual), "Wire bytes differ: " + HexFormat.of().formatHex(actual));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
