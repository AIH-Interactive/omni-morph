package top.aihmc.omnimorph.model.ysm.sync;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

/** Ping payload framing. These limits do not replace the connection's shared Ping quota. */
public final class YsmSyncPackets {
    public static final int PING_ID = Integer.MIN_VALUE;
    public static final int MAX_PACKET_BYTES = 480;
    public static final int EXPIRY_TICKS = 1200;
    public static final int MAX_IN_FLIGHT = 16;
    private static final int MAGIC = 0x59534d50;
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 25;
    private static final int FRAGMENT_BYTES = MAX_PACKET_BYTES - HEADER_BYTES;
    private static final int MAX_PER_OWNER = 2;
    private final Map<Key, Assembly> incoming = new HashMap<>();

    private record Key(UUID owner, long id) {
    }

    private static final class Assembly {
        private final byte[] bytes;
        private final boolean[] received;
        private final int checksum;
        private final long started;
        private int remaining;

        private Assembly(int length, int count, int checksum, long tick) {
            bytes = new byte[length];
            received = new boolean[count];
            remaining = count;
            this.checksum = checksum;
            started = tick;
        }
    }

    public static boolean recognizes(int pingId, byte[] packet) {
        return recognizes(pingId, ByteBuffer.wrap(packet));
    }

    public static boolean recognizes(int pingId, ByteBuffer packet) {
        return pingId == PING_ID && packet.remaining() >= 4 && packet.getInt(packet.position()) == MAGIC;
    }

    public static List<byte[]> fragment(long id, byte[] message) throws IOException {
        if (message.length == 0 || message.length > YsmSyncCodec.MAX_BYTES) {
            throw new IOException("Invalid sync message size");
        }
        int count = fragmentCount(message.length);
        int checksum = checksum(message);
        List<byte[]> packets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * FRAGMENT_BYTES;
            int length = Math.min(FRAGMENT_BYTES, message.length - offset);
            ByteBuffer packet = ByteBuffer.allocate(HEADER_BYTES + length);
            packet.putInt(MAGIC).put((byte) VERSION).putLong(id).putInt(message.length)
                    .putShort((short) index).putShort((short) count).putInt(checksum);
            packet.put(message, offset, length);
            packets.add(packet.array());
        }
        return List.copyOf(packets);
    }

    /** The outer websocket's authenticated UUID is part of the assembly key. */
    public byte[] accept(UUID owner, byte[] packet, long tick) throws IOException {
        expire(tick);
        if (owner == null || tick < 0 || packet.length < HEADER_BYTES || packet.length > MAX_PACKET_BYTES) {
            throw new IOException("Invalid sync packet size or owner");
        }
        ByteBuffer input = ByteBuffer.wrap(packet);
        if (input.getInt() != MAGIC || Byte.toUnsignedInt(input.get()) != VERSION) {
            throw new IOException("Unsupported sync packet protocol");
        }
        long id = input.getLong();
        int length = input.getInt();
        int index = Short.toUnsignedInt(input.getShort());
        int count = Short.toUnsignedInt(input.getShort());
        int checksum = input.getInt();
        if (length <= 0 || length > YsmSyncCodec.MAX_BYTES || count != fragmentCount(length) || index >= count) {
            throw new IOException("Invalid sync fragment range");
        }
        int offset = index * FRAGMENT_BYTES;
        int fragmentLength = Math.min(FRAGMENT_BYTES, length - offset);
        if (input.remaining() != fragmentLength) {
            throw new IOException("Invalid sync fragment length");
        }
        Key key = new Key(owner, id);
        Assembly assembly = incoming.get(key);
        if (assembly == null) {
            long owned = incoming.keySet().stream().filter(value -> value.owner().equals(owner)).count();
            if (incoming.size() >= MAX_IN_FLIGHT || owned >= MAX_PER_OWNER) {
                return null;
            }
            assembly = new Assembly(length, count, checksum, tick);
            incoming.put(key, assembly);
        } else if (assembly.bytes.length != length || assembly.checksum != checksum) {
            incoming.remove(key);
            throw new IOException("Conflicting sync fragments");
        }
        if (assembly.received[index]) {
            for (int i = 0; i < fragmentLength; i++) {
                if (assembly.bytes[offset + i] != input.get()) {
                    incoming.remove(key);
                    throw new IOException("Conflicting duplicate sync fragment");
                }
            }
            return null;
        }
        input.get(assembly.bytes, offset, fragmentLength);
        assembly.received[index] = true;
        assembly.remaining--;
        if (assembly.remaining != 0) {
            return null;
        }
        incoming.remove(key);
        if (checksum(assembly.bytes) != assembly.checksum) {
            throw new IOException("Invalid sync message checksum");
        }
        return assembly.bytes;
    }

    public void expire(long tick) {
        incoming.values().removeIf(value -> tick < value.started || tick - value.started >= EXPIRY_TICKS);
    }

    public void clear() {
        incoming.values().forEach(value -> Arrays.fill(value.bytes, (byte) 0));
        incoming.clear();
    }

    public int pendingCount() {
        return incoming.size();
    }

    private static int fragmentCount(int length) {
        return (length + FRAGMENT_BYTES - 1) / FRAGMENT_BYTES;
    }

    private static int checksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }
}
