package org.figuramc.figura.model.ysm.sync;

import org.figuramc.figura.model.ysm.YsmModelRuntime;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

/** Client-thread state transfer; the websocket thread may only enqueue bounded packets. */
public final class YsmSyncConnection {
    public static final int MAX_QUEUED_PACKETS = 128;
    public static final int MAX_PEERS = 64;
    public static final int SNAPSHOT_TICKS = 40;
    private final ArrayDeque<Inbound> queued = new ArrayDeque<>();
    private final YsmSyncPackets packets = new YsmSyncPackets();
    private final Map<UUID, Peer> peers = new HashMap<>();
    private final LinkedHashMap<UUID, History> histories = new LinkedHashMap<>();
    private volatile Object connection;
    private volatile long currentTick;
    private volatile boolean snapshotRequested;
    private int queuedBytes;
    private UUID session = UUID.randomUUID();
    private YsmModelRuntime host;
    private YsmModelRuntime failedHost;
    private YsmSyncModel hostModel;
    private long generation = -1;
    private long sequence = -1;
    private long messageId;
    private long lastBuild = Long.MIN_VALUE;
    private long lastSnapshot = Long.MIN_VALUE;
    private long lastWatermark;
    private YsmSyncMessage.State lastState;
    private List<byte[]> outgoing = List.of();
    private int outgoingIndex;
    private long outgoingStarted;
    private YsmSyncMessage pendingMessage;
    private YsmSyncMessage.State pendingState;
    private final AtomicLong rejected = new AtomicLong();

    private record Inbound(Object connection, UUID owner, byte[] bytes, YsmSyncMessage message, long receivedTick) {
    }

    private record Peer(YsmModelRuntime runtime, YsmSyncModel model, YsmSyncReceiver receiver) {
    }

    private record History(String content, String schema, YsmSyncReceiver receiver, long tick) {
    }

    /** No model/Avatar access from the callback thread. False means it was not a YSM packet. */
    public boolean enqueue(Object source, UUID owner, int pingId, byte[] data) {
        if (!YsmSyncPackets.recognizes(pingId, data)) {
            return false;
        }
        synchronized (queued) {
            if (source != connection || source == null || data.length > YsmSyncPackets.MAX_PACKET_BYTES
                    || queued.size() >= MAX_QUEUED_PACKETS || queuedBytes + data.length > 262144) {
                rejected.incrementAndGet();
                return true;
            }
            queued.addLast(new Inbound(source, owner, data.clone(), null, currentTick));
            queuedBytes += data.length;
        }
        return true;
    }

    /** Formal protocol adapters enqueue typed state, without manufacturing M7 packets. */
    public boolean enqueueTyped(Object source, YsmSyncMessage message) {
        int size;
        try {
            size = YsmSyncCodec.encode(message).length;
        } catch (IOException exception) {
            rejected.incrementAndGet();
            return false;
        }
        synchronized (queued) {
            if (source == null || source != connection || queued.size() >= MAX_QUEUED_PACKETS
                    || queuedBytes + size > 262144) {
                rejected.incrementAndGet();
                return false;
            }
            queued.addLast(new Inbound(source, message.owner(), null, message, currentTick));
            queuedBytes += size;
        }
        return true;
    }

    public void requestSnapshot() {
        snapshotRequested = true;
    }

    /** A different socket or world identity starts a new isolated epoch. */
    public void tick(Object epoch, long tick, YsmModelRuntime local,
                     Function<UUID, YsmModelRuntime> models, Predicate<byte[]> send) {
        tickInternal(epoch, tick, local, models, send, null);
    }

    public void tickTyped(Object epoch, long tick, YsmModelRuntime local,
                          Function<UUID, YsmModelRuntime> models, Predicate<YsmSyncMessage> send) {
        tickInternal(epoch, tick, local, models, null, send);
    }

    private void tickInternal(Object epoch, long tick, YsmModelRuntime local,
                              Function<UUID, YsmModelRuntime> models, Predicate<byte[]> send,
                              Predicate<YsmSyncMessage> typedSend) {
        currentTick = tick;
        if (connection != epoch) {
            reset(epoch);
        }
        if (epoch == null) {
            return;
        }
        if (snapshotRequested) {
            snapshotRequested = false;
            generation++;
            resetOutgoing();
        }
        packets.expire(tick);
        retirePeers(tick, models);
        drainInbound(tick, models);
        if (local != host) {
            host = local;
            failedHost = null;
            hostModel = null;
            generation++;
            resetOutgoing();
        }
        if (local == null || local.isClosed() || !local.owner().isHost || local == failedHost) {
            return;
        }
        try {
            if (hostModel == null) {
                hostModel = new YsmSyncModel(local);
            }
            if (!outgoing.isEmpty() || pendingMessage != null) {
                if (tick < outgoingStarted || tick - outgoingStarted >= YsmSyncPackets.EXPIRY_TICKS) {
                    generation++;
                    resetOutgoing();
                } else {
                    flush(tick, send, typedSend);
                    return;
                }
            }
            boolean freshEvents = local.functions().syncWatermark() != lastWatermark;
            if (lastBuild != Long.MIN_VALUE && tick >= lastBuild && tick - lastBuild < 4 && !freshEvents) {
                return;
            }
            lastBuild = tick;
            YsmSyncMessage.State state = hostModel.capture();
            List<YsmSyncMessage.Event> events = local.functions().drainSyncEvents();
            long watermark = local.functions().syncWatermark();
            boolean snapshot = lastState == null || lastSnapshot == Long.MIN_VALUE || tick - lastSnapshot >= SNAPSHOT_TICKS;
            if (lastState == null) {
                // First baseline after equip/reconnect never replays historical one-shots.
                events = List.of();
            }
            if (!snapshot && events.isEmpty() && state.equals(lastState)) {
                return;
            }
            YsmSyncMessage.State body = snapshot ? state : delta(lastState, state);
            pendingMessage = new YsmSyncMessage(local.owner().owner, session, hostModel.contentHash(), hostModel.schemaHash(),
                    generation, sequence + 1, snapshot ? -1 : sequence, tick, snapshot, watermark, body, events);
            outgoing = typedSend == null ? YsmSyncPackets.fragment(++messageId, YsmSyncCodec.encode(pendingMessage)) : List.of();
            pendingState = state;
            outgoingIndex = 0;
            outgoingStarted = tick;
            lastWatermark = watermark;
            flush(tick, send, typedSend);
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            // A model exceeding this protocol's budget still remains usable in single player.
            failedHost = local;
            rejected.incrementAndGet();
            resetOutgoing();
        }
    }

    private void drainInbound(long tick, Function<UUID, YsmModelRuntime> models) {
        List<Inbound> batch;
        synchronized (queued) {
            batch = List.copyOf(queued);
            queued.clear();
            queuedBytes = 0;
        }
        for (Inbound input : batch) {
            if (input.connection() != connection) {
                continue;
            }
            try {
                YsmSyncMessage decoded = input.message();
                if (decoded == null) {
                    byte[] message = packets.accept(input.owner(), input.bytes(), tick);
                    if (message == null) {
                        continue;
                    }
                    decoded = YsmSyncCodec.decode(message);
                }
                YsmModelRuntime runtime = models.apply(input.owner());
                if (runtime == null && tick >= input.receivedTick() && tick - input.receivedTick() < 600) {
                    int size = YsmSyncCodec.encode(decoded).length;
                    synchronized (queued) {
                        if (queued.size() < MAX_QUEUED_PACKETS && queuedBytes + size <= 262144) {
                            queued.addLast(new Inbound(input.connection(), input.owner(), null, decoded, input.receivedTick()));
                            queuedBytes += size;
                        } else {
                            rejected.incrementAndGet();
                        }
                    }
                    continue;
                }
                if (runtime == null || runtime.isClosed() || runtime.owner().isHost
                        || !input.owner().equals(decoded.owner()) || !input.owner().equals(runtime.owner().owner)) {
                    continue;
                }
                Peer peer = peers.get(input.owner());
                if (peer == null) {
                    if (peers.size() >= MAX_PEERS) {
                        continue;
                    }
                    YsmSyncModel model = new YsmSyncModel(runtime);
                    History history = histories.remove(input.owner());
                    YsmSyncReceiver receiver = history != null && history.content().equals(model.contentHash())
                            && history.schema().equals(model.schemaHash()) ? history.receiver()
                            : new YsmSyncReceiver(input.owner(), model.contentHash(), model.schemaHash());
                    peer = new Peer(runtime, model, receiver);
                    peers.put(input.owner(), peer);
                    if (receiver.state() != null) {
                        model.apply(receiver.state());
                    }
                }
                decoded.events().forEach(peer.model()::validateEvent);
                YsmSyncMessage.State before = peer.receiver().state();
                List<YsmSyncMessage.Event> events = peer.receiver().accept(input.owner(), decoded);
                if (peer.receiver().state() != null && peer.receiver().state() != before) {
                    peer.model().apply(peer.receiver().state(), events);
                }
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                rejected.incrementAndGet();
            }
        }
    }

    private void retirePeers(long tick, Function<UUID, YsmModelRuntime> models) {
        histories.values().removeIf(value -> tick < value.tick() || tick - value.tick() >= YsmSyncPackets.EXPIRY_TICKS);
        var iterator = peers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Peer peer = entry.getValue();
            if (peer.runtime().isClosed() || models.apply(entry.getKey()) != peer.runtime()) {
                // Retain only indices/clocks for replay rejection, never the retired model or its resources.
                histories.put(entry.getKey(), new History(peer.model().contentHash(), peer.model().schemaHash(), peer.receiver(), tick));
                iterator.remove();
            }
        }
        while (histories.size() > MAX_PEERS) {
            histories.remove(histories.keySet().iterator().next());
        }
    }

    private void flush(long tick, Predicate<byte[]> send, Predicate<YsmSyncMessage> typedSend) {
        if (typedSend != null && !typedSend.test(pendingMessage)) {
            return;
        }
        while (outgoingIndex < outgoing.size()) {
            if (!send.test(outgoing.get(outgoingIndex))) {
                return;
            }
            outgoingIndex++;
        }
        sequence = pendingMessage.sequence();
        lastState = pendingState;
        if (pendingMessage.snapshot()) {
            lastSnapshot = tick;
        }
        outgoing = List.of();
        pendingMessage = null;
        pendingState = null;
    }

    private void reset(Object epoch) {
        synchronized (queued) {
            connection = epoch;
            queued.clear();
            queuedBytes = 0;
        }
        packets.clear();
        peers.clear();
        histories.clear();
        session = UUID.randomUUID();
        host = null;
        failedHost = null;
        hostModel = null;
        generation = -1;
        resetOutgoing();
    }

    private void resetOutgoing() {
        outgoing = List.of();
        outgoingIndex = 0;
        pendingMessage = null;
        pendingState = null;
        lastState = null;
        sequence = -1;
        lastBuild = Long.MIN_VALUE;
        lastSnapshot = Long.MIN_VALUE;
        lastWatermark = 0;
    }

    private static YsmSyncMessage.State delta(YsmSyncMessage.State previous, YsmSyncMessage.State next) {
        return new YsmSyncMessage.State(Objects.equals(previous.texture(), next.texture()) ? null : next.texture(),
                previous.controls().equals(next.controls()) ? null : next.controls(),
                previous.actions().equals(next.actions()) ? null : next.actions(),
                previous.controllers().equals(next.controllers()) ? null : next.controllers(),
                previous.playbacks().equals(next.playbacks()) ? null : next.playbacks());
    }

    public long rejectedMessages() {
        return rejected.get();
    }

    public int peerCount() {
        return peers.size();
    }

    public int queuedPackets() {
        synchronized (queued) {
            return queued.size();
        }
    }
}
