package org.figuramc.figura.model.ysm.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Bounded application message codec; transport framing and authentication are separate. */
public final class YsmSyncCodec {
    public static final int MAX_BYTES = 32768;
    private static final int MAGIC = 0x59534d53;
    private static final int VERSION = 1;

    private YsmSyncCodec() {
    }

    public static byte[] encode(YsmSyncMessage message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            writeUuid(out, message.owner());
            writeUuid(out, message.session());
            out.write(HexFormat.of().parseHex(message.contentHash()));
            out.write(HexFormat.of().parseHex(message.schemaHash()));
            out.writeLong(message.generation());
            out.writeLong(message.sequence());
            out.writeLong(message.baseSequence());
            out.writeLong(message.ownerTick());
            out.writeBoolean(message.snapshot());
            out.writeLong(message.eventWatermark());
            var state = message.state();
            int mask = (state.texture() != null ? 1 : 0) | (state.controls() != null ? 2 : 0)
                    | (state.actions() != null ? 4 : 0) | (state.controllers() != null ? 8 : 0)
                    | (state.playbacks() != null ? 16 : 0);
            out.writeByte(mask);
            if (state.texture() != null) {
                out.writeInt(state.texture());
            }
            writeSection(out, state.controls(), (stream, value) -> {
                stream.writeShort(value.index());
                stream.writeByte(value.type().ordinal());
                stream.writeDouble(value.value());
            });
            writeSection(out, state.actions(), (stream, value) -> {
                stream.writeShort(value.index());
                stream.writeBoolean(value.active());
                stream.writeFloat(value.time());
                stream.writeFloat(value.speed());
                stream.writeBoolean(value.loop());
            });
            writeSection(out, state.controllers(), (stream, value) -> {
                stream.writeShort(value.index());
                stream.writeShort(value.state());
                stream.writeFloat(value.time());
            });
            writeSection(out, state.playbacks(), (stream, value) -> {
                stream.writeShort(value.slot());
                stream.writeShort(value.clip());
                stream.writeInt(value.controller());
                stream.writeFloat(value.time());
                stream.writeFloat(value.speed());
                stream.writeFloat(value.weight());
                stream.writeFloat(value.targetWeight());
                stream.writeByte(value.loopMode());
                stream.writeFloat(value.fadeSpeed());
                stream.writeByte(value.flags());
            });
            out.writeByte(message.events().size());
            for (var event : message.events()) {
                out.writeLong(event.sequence());
                out.writeByte(event.kind().ordinal());
                out.writeByte(event.arguments().size());
                for (float argument : event.arguments()) {
                    out.writeFloat(argument);
                }
            }
        }
        if (bytes.size() > MAX_BYTES) {
            throw new IOException("Sync message exceeds budget");
        }
        return bytes.toByteArray();
    }

    public static YsmSyncMessage decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Sync message exceeds budget");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) {
                throw new IOException("Unsupported sync protocol");
            }
            UUID owner = readUuid(in);
            UUID session = readUuid(in);
            byte[] content = new byte[32];
            byte[] schema = new byte[32];
            in.readFully(content);
            in.readFully(schema);
            long generation = in.readLong();
            long sequence = in.readLong();
            long base = in.readLong();
            long tick = in.readLong();
            boolean snapshot = readBoolean(in);
            long watermark = in.readLong();
            int mask = in.readUnsignedByte();
            if ((mask & ~31) != 0) {
                throw new IOException("Unknown sync section");
            }
            Integer texture = (mask & 1) != 0 ? in.readInt() : null;
            var controls = readSection(in, (mask & 2) != 0, 11, stream -> {
                int index = stream.readUnsignedShort();
                int type = stream.readUnsignedByte();
                if (type >= YsmSyncMessage.ControlType.values().length) {
                    throw new IOException("Unknown sync control type");
                }
                return new YsmSyncMessage.Control(index, YsmSyncMessage.ControlType.values()[type], stream.readDouble());
            });
            var actions = readSection(in, (mask & 4) != 0, 12, stream ->
                    new YsmSyncMessage.Action(stream.readUnsignedShort(), readBoolean(stream),
                            stream.readFloat(), stream.readFloat(), readBoolean(stream)));
            var controllers = readSection(in, (mask & 8) != 0, 8, stream ->
                    new YsmSyncMessage.Controller(stream.readUnsignedShort(), stream.readUnsignedShort(), stream.readFloat()));
            var playbacks = readSection(in, (mask & 16) != 0, 30, stream ->
                    new YsmSyncMessage.Playback(stream.readUnsignedShort(), stream.readUnsignedShort(), stream.readInt(),
                            stream.readFloat(), stream.readFloat(), stream.readFloat(), stream.readFloat(), stream.readUnsignedByte(),
                            stream.readFloat(), stream.readUnsignedByte()));
            int count = in.readUnsignedByte();
            if (count > YsmSyncMessage.MAX_EVENTS || count > in.available() / 10) {
                throw new IOException("Invalid sync event count");
            }
            List<YsmSyncMessage.Event> events = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long eventSequence = in.readLong();
                int eventKind = in.readUnsignedByte();
                if (eventKind >= YsmSyncMessage.EventKind.values().length) {
                    throw new IOException("Unknown sync event kind");
                }
                int arguments = in.readUnsignedByte();
                if (arguments > 16 || arguments > in.available() / 4) {
                    throw new IOException("Invalid sync argument count");
                }
                List<Float> values = new ArrayList<>(arguments);
                for (int j = 0; j < arguments; j++) {
                    values.add(in.readFloat());
                }
                events.add(new YsmSyncMessage.Event(eventSequence, YsmSyncMessage.EventKind.values()[eventKind], values));
            }
            if (in.available() != 0) {
                throw new IOException("Trailing sync bytes");
            }
            return new YsmSyncMessage(owner, session, HexFormat.of().formatHex(content), HexFormat.of().formatHex(schema),
                    generation, sequence, base, tick, snapshot, watermark,
                    new YsmSyncMessage.State(texture, controls, actions, controllers, playbacks), events);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid sync value", exception);
        }
    }

    private static boolean readBoolean(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) {
            throw new IOException("Invalid sync boolean");
        }
        return value == 1;
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static <T> void writeSection(DataOutputStream out, List<T> values, Writer<T> writer) throws IOException {
        if (values == null) {
            return;
        }
        out.writeShort(values.size());
        for (T value : values) {
            writer.write(out, value);
        }
    }

    private static <T> List<T> readSection(DataInputStream in, boolean present, int width, Reader<T> reader) throws IOException {
        if (!present) {
            return null;
        }
        int count = in.readUnsignedShort();
        if (count > YsmSyncMessage.MAX_ENTRIES || count > in.available() / width) {
            throw new IOException("Invalid sync section count");
        }
        List<T> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(reader.read(in));
        }
        return values;
    }

    private interface Writer<T> {
        void write(DataOutputStream out, T value) throws IOException;
    }

    private interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }
}
