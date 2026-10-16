package org.figuramc.figura.model.ysm.sync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** One model's ordered observer state. Call only after transport authentication. */
public final class YsmSyncReceiver {
    public static final int MAX_PENDING = 32;
    private static final int MAX_RETIRED_SESSIONS = 64;
    private final UUID owner;
    private final String contentHash;
    private final String schemaHash;
    private final Set<UUID> retiredSessions = new HashSet<>();
    private final TreeMap<Long, YsmSyncMessage> pending = new TreeMap<>();
    private UUID session;
    private long generation = -1;
    private long sequence = -1;
    private long ownerTick = -1;
    private long eventWatermark;
    private YsmSyncMessage.State state;

    public YsmSyncReceiver(UUID owner, String contentHash, String schemaHash) {
        this.owner = owner;
        this.contentHash = contentHash;
        this.schemaHash = schemaHash;
    }

    /** Events are returned only when their delta becomes contiguous with the baseline. */
    public List<YsmSyncMessage.Event> accept(UUID authenticatedOwner, YsmSyncMessage message) {
        if (!owner.equals(authenticatedOwner) || !owner.equals(message.owner())
                || !contentHash.equals(message.contentHash()) || !schemaHash.equals(message.schemaHash())
                || retiredSessions.contains(message.session())) {
            return List.of();
        }
        boolean newSession = !message.session().equals(session);
        boolean newGeneration = newSession || message.generation() > generation;
        if (newGeneration) {
            if (!message.snapshot() || newSession && session != null && retiredSessions.size() >= MAX_RETIRED_SESSIONS) {
                return List.of();
            }
            if (newSession && session != null) {
                retiredSessions.add(session);
            }
            session = message.session();
            generation = message.generation();
            sequence = -1;
            ownerTick = -1;
            eventWatermark = 0;
            state = null;
            pending.clear();
        }
        if (message.generation() != generation || message.sequence() <= sequence || message.ownerTick() < ownerTick
                || message.eventWatermark() < eventWatermark) {
            return List.of();
        }
        List<YsmSyncMessage.Event> events = new ArrayList<>();
        if (message.snapshot()) {
            apply(message, events);
            pending.headMap(sequence, true).clear();
            pending.values().removeIf(value -> value.baseSequence() < sequence);
        } else {
            if (state == null || message.baseSequence() < sequence || pending.size() >= MAX_PENDING) {
                return List.of();
            }
            pending.putIfAbsent(message.sequence(), message);
        }
        while (!pending.isEmpty()) {
            YsmSyncMessage next = pending.firstEntry().getValue();
            if (next.baseSequence() != sequence) {
                break;
            }
            pending.pollFirstEntry();
            if (next.ownerTick() < ownerTick || next.eventWatermark() < eventWatermark) {
                continue;
            }
            apply(next, events);
            pending.values().removeIf(value -> value.baseSequence() < sequence);
        }
        return List.copyOf(events);
    }

    private void apply(YsmSyncMessage message, List<YsmSyncMessage.Event> events) {
        if (state != null) {
            for (var event : message.events()) {
                if (event.sequence() > eventWatermark) {
                    events.add(event);
                }
            }
        }
        state = message.snapshot() ? message.state() : state.apply(message.state());
        sequence = message.sequence();
        ownerTick = message.ownerTick();
        eventWatermark = message.eventWatermark();
    }

    public YsmSyncMessage.State state() {
        return state;
    }

    public long sequence() {
        return sequence;
    }

    public int pendingCount() {
        return pending.size();
    }
}
