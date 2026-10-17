package org.figuramc.figura.model.ysm;

import org.figuramc.figura.backend2.websocket.C2SMessageHandler;
import org.figuramc.figura.backend2.protocol.YsmProtobuf;
import org.figuramc.figura.model.ysm.sync.YsmSyncMessage;
import org.figuramc.figura.model.ysm.sync.YsmSyncConnection;
import org.figuramc.figura.model.ysm.sync.YsmSyncPackets;
import org.figuramc.figura.model.ysm.sync.YsmSyncPingBudget;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Production connection state machine and real runtime objects; no live websocket or game-window claim. */
final class YsmSyncConnectionSmokeTest {
    private YsmSyncConnectionSmokeTest() {
    }

    static void run(YsmModelRuntime source, YsmModelRuntime observer, YsmModelRuntime replacement,
                    YsmModelRuntime newSource) {
        Link link = new Link(source, observer);
        source.owner().controls.setValue(source.owner(), "ysm.width_scale", 0.9);
        source.setTexture("c");
        source.actions().trigger("gui");
        source.actions().stop("gui");
        link.pump(0, 5, source);
        require(Math.abs(observer.widthScale() - 0.9f) < 0.0001 && observer.textureId().equals("c"),
                "production sender packets apply real observer controls/material");
        require(observer.owner().getMolangContext().variables.getPath("preview_events") == null,
                "first connection baseline skips historical short action");
        int beforeShortAction = link.sent.size();
        source.actions().trigger("gui");
        source.actions().stop("gui");
        link.pump(6, 12, source);
        require(counter(observer) == 1 && !observer.actions().isActive("gui"),
                "short action start/effects/stop survive a single final inactive state");
        List<byte[]> shortPackets = List.copyOf(link.sent.subList(beforeShortAction, link.sent.size()));
        link.deliver(shortPackets);
        link.pump(13, 14, source);
        require(counter(observer) == 1, "duplicate complete messages do not reapply one-shot action effects");

        int beforeSnapshot = link.sent.size();
        source.actions().trigger("gui");
        source.actions().stop("gui");
        link.pump(40, 45, source);
        List<byte[]> snapshotPackets = List.copyOf(link.sent.subList(beforeSnapshot, link.sent.size()));
        require(counter(observer) == 2, "periodic snapshot tail delivers live events once");
        var late = new YsmSyncConnection();
        Object lateEpoch = new Object();
        late.tick(lateEpoch, 45, null, uuid -> replacement, bytes -> false);
        for (byte[] bytes : snapshotPackets) {
            late.enqueue(lateEpoch, source.owner().owner, YsmSyncPackets.PING_ID, bytes);
        }
        late.tick(lateEpoch, 46, null, uuid -> replacement, bytes -> false);
        require(Math.abs(replacement.widthScale() - 0.9f) < 0.0001 && replacement.textureId().equals("c")
                        && replacement.owner().getMolangContext().variables.getPath("preview_events") == null,
                "late join applies snapshot state while suppressing its historical event tail");
        late.tick(null, 47, null, uuid -> null, bytes -> false);

        link.observed.set(replacement);
        link.pump(48, 48, source);
        link.deliver(shortPackets);
        link.pump(49, 49, source);
        require(replacement.owner().getMolangContext().variables.getPath("preview_events") == null,
                "same-content observer reload retains replay rejection without retaining old model resources");
        source.owner().controls.setValue(source.owner(), "ysm.width_scale", 1.1);
        link.pump(52, 56, source);
        require(Math.abs(replacement.widthScale() - 1.1f) < 0.0001, "reloaded observer receives later delta");

        List<byte[]> oldGeneration = List.copyOf(link.sent);
        newSource.owner().controls.setValue(newSource.owner(), "ysm.width_scale", 1.2);
        link.pump(60, 65, newSource);
        require(Math.abs(replacement.widthScale() - 1.2f) < 0.0001, "owner model replacement publishes new generation baseline");
        link.deliver(oldGeneration);
        link.pump(66, 67, newSource);
        require(Math.abs(replacement.widthScale() - 1.2f) < 0.0001 && counter(replacement) == 0,
                "old generation cannot restore old controls or replay old effects");

        List<byte[]> oldSession = List.copyOf(link.sent);
        link.senderEpoch = new Object();
        newSource.owner().controls.setValue(newSource.owner(), "ysm.width_scale", 1.3);
        link.pump(80, 85, newSource);
        require(Math.abs(replacement.widthScale() - 1.3f) < 0.0001, "owner reconnect begins new session snapshot");
        link.deliver(oldSession);
        link.pump(86, 87, newSource);
        require(Math.abs(replacement.widthScale() - 1.3f) < 0.0001 && counter(replacement) == 0,
                "retired owner session cannot mutate current runtime");

        Object staleReceiverEpoch = link.receiverEpoch;
        link.receiverEpoch = new Object();
        link.pump(100, 100, newSource);
        byte[] packet = link.sent.getFirst();
        long beforeRejected = link.receiver.rejectedMessages();
        link.receiver.enqueue(staleReceiverEpoch, source.owner().owner, YsmSyncPackets.PING_ID, packet);
        require(link.receiver.queuedPackets() == 0 && link.receiver.rejectedMessages() > beforeRejected,
                "old socket callback cannot queue work in reconnected observer epoch");
        link.pump(120, 125, newSource);
        require(link.receiver.peerCount() == 1 && Math.abs(replacement.widthScale() - 1.3f) < 0.0001,
                "observer reconnect converges from periodic owner baseline");
        for (int index = 0; index <= YsmSyncConnection.MAX_QUEUED_PACKETS; index++) {
            link.receiver.enqueue(link.receiverEpoch, source.owner().owner, YsmSyncPackets.PING_ID, packet);
        }
        require(link.receiver.queuedPackets() == YsmSyncConnection.MAX_QUEUED_PACKETS, "callback queue bounded before main thread consumption");
        link.receiver.tick(null, 126, null, uuid -> null, bytes -> false);
        require(link.receiver.queuedPackets() == 0 && link.receiver.peerCount() == 0 && !replacement.isClosed(),
                "world exit clears queues/peers without closing borrowed model");
        require(link.sender.rejectedMessages() == 0, "normal owner generation/reconnect transfer has no rejected application messages");
        typedReplay(newSource, replacement);
        System.out.println("M7 production connection CPU replay: Ping framing/budget, baseline/delta, short actions, late join, model reload, generation and reconnect PASS");
    }

    private static void typedReplay(YsmModelRuntime source, YsmModelRuntime observer) {
        var sender = new YsmSyncConnection();
        var receiver = new YsmSyncConnection();
        var publisher = new YsmProtobuf.Publisher();
        Object senderEpoch = new Object();
        Object receiverEpoch = new Object();
        receiver.tickTyped(receiverEpoch, 200, null, uuid -> null, message -> false);
        java.util.function.Predicate<YsmSyncMessage> transfer = message -> {
            try {
                for (var body : publisher.encode(message, 1)) {
                    require(receiver.enqueueTyped(receiverEpoch, YsmProtobuf.decode(body.setMajor(1).build())),
                            "formal message enters bounded model queue");
                }
                return true;
            } catch (IOException exception) {
                throw new AssertionError("Cannot map formal state", exception);
            }
        };
        source.owner().controls.setValue(source.owner(), "ysm.width_scale", 1.35);
        sender.tickTyped(senderEpoch, 200, source, uuid -> null, transfer);
        receiver.tickTyped(receiverEpoch, 201, null, uuid -> null, message -> false);
        require(receiver.queuedPackets() == 1, "baseline waits within budget while observer model is loading");
        receiver.tickTyped(receiverEpoch, 202, null, uuid -> observer, message -> false);
        require(Math.abs(observer.widthScale() - 1.35f) < 0.0001 && receiver.queuedPackets() == 0,
                "formal baseline applies after loader completes");
        float before = counter(observer);
        source.actions().trigger("gui");
        source.actions().stop("gui");
        sender.tickTyped(senderEpoch, 204, source, uuid -> null, transfer);
        receiver.tickTyped(receiverEpoch, 205, null, uuid -> observer, message -> false);
        require(counter(observer) == before + 1 && !observer.actions().isActive("gui"),
                "formal delta/event mapping replays real short action exactly once");
        sender.requestSnapshot();
        source.owner().controls.setValue(source.owner(), "ysm.width_scale", 1.4);
        sender.tickTyped(senderEpoch, 208, source, uuid -> null, transfer);
        receiver.tickTyped(receiverEpoch, 209, null, uuid -> observer, message -> false);
        require(Math.abs(observer.widthScale() - 1.4f) < 0.0001 && counter(observer) == before + 1,
                "requested full snapshot starts new generation without replaying historical effects");
        receiver.tickTyped(null, 210, null, uuid -> null, message -> false);
        require(receiver.queuedPackets() == 0 && receiver.peerCount() == 0 && !observer.isClosed(),
                "formal world exit preserves borrowed runtime");
        sender.tickTyped(null, 210, null, uuid -> null, message -> false);
        System.out.println("B5 formal runtime CPU replay: loader delay, snapshot/delta, short action, refresh generation and world cleanup PASS");
    }

    private static float counter(YsmModelRuntime runtime) {
        Object value = runtime.owner().getMolangContext().variables.getPath("preview_events");
        return value instanceof Number number ? number.floatValue() : 0;
    }

    private static final class Link {
        private final YsmSyncConnection sender = new YsmSyncConnection();
        private final YsmSyncConnection receiver = new YsmSyncConnection();
        private final YsmSyncPingBudget budget = new YsmSyncPingBudget();
        private final AtomicReference<YsmModelRuntime> observed;
        private final List<byte[]> sent = new ArrayList<>();
        private final YsmModelRuntime original;
        private Object senderEpoch = new Object();
        private Object receiverEpoch = new Object();

        private Link(YsmModelRuntime source, YsmModelRuntime observer) {
            original = source;
            observed = new AtomicReference<>(observer);
        }

        private void deliver(List<byte[]> packets) {
            for (byte[] packet : packets) {
                receiver.enqueue(receiverEpoch, original.owner().owner, YsmSyncPackets.PING_ID, packet);
            }
        }

        private void pump(int first, int last, YsmModelRuntime source) {
            for (int tick = first; tick <= last; tick++) {
                long now = tick;
                receiver.tick(receiverEpoch, now, null, uuid -> observed.get(), bytes -> false);
                sender.tick(senderEpoch, now, source, uuid -> null, bytes -> {
                    if (!budget.canSend(now, bytes.length)) {
                        return false;
                    }
                    ByteBuffer envelope;
                    try {
                        envelope = C2SMessageHandler.ping(YsmSyncPackets.PING_ID, false, bytes);
                    } catch (IOException exception) {
                        throw new AssertionError("Cannot encode existing Ping envelope", exception);
                    }
                    require(envelope.get() == 1 && envelope.getInt() == YsmSyncPackets.PING_ID && envelope.get() == 0,
                            "real Figura C2S Ping envelope is reused");
                    byte[] data = new byte[envelope.remaining()];
                    envelope.get(data);
                    budget.recordSent(now, data.length);
                    sent.add(data.clone());
                    receiver.enqueue(receiverEpoch, original.owner().owner, YsmSyncPackets.PING_ID, data);
                    return true;
                });
                receiver.tick(receiverEpoch, now, null, uuid -> observed.get(), bytes -> false);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
