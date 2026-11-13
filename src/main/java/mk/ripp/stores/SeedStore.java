package mk.ripp.stores;

import mk.ripp.model.SeederId;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SeedStore {
    public static final Duration TTL = Duration.ofHours(1);

    private final Map<SeederId, Registration> seeders = new HashMap<>();
    private final Map<UUID, Set<SeederId>> byRip = new HashMap<>();

    public synchronized void announce(SeederId id, List<UUID> rips) {
        if (rips == null) {
            throw new IllegalArgumentException("rips is required");
        }
        expire();
        Set<UUID> next = Set.copyOf(rips);
        Registration previous = seeders.get(id);
        if (previous != null) {
            for (UUID ripId : previous.rips()) {
                if (!next.contains(ripId)) {
                    detach(ripId, id);
                }
            }
        }
        seeders.put(id, new Registration(next, Instant.now().plus(TTL)));
        for (UUID ripId : next) {
            byRip.computeIfAbsent(ripId, ignored -> new HashSet<>()).add(id);
        }
    }

    public synchronized List<SeederId> seeders(UUID ripId) {
        expire();
        Set<SeederId> ids = byRip.get(ripId);
        if (ids == null) {
            return List.of();
        }
        return List.copyOf(ids);
    }

    public synchronized void remove(SeederId id) {
        Registration registration = seeders.remove(id);
        if (registration == null) {
            return;
        }
        for (UUID ripId : registration.rips()) {
            detach(ripId, id);
        }
    }

    private void expire() {
        Instant now = Instant.now();
        seeders.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt().isAfter(now)) {
                return false;
            }
            for (UUID ripId : entry.getValue().rips()) {
                detach(ripId, entry.getKey());
            }
            return true;
        });
    }

    private void detach(UUID ripId, SeederId id) {
        byRip.computeIfPresent(ripId, (ignored, ids) -> {
            ids.remove(id);
            return ids.isEmpty() ? null : ids;
        });
    }

    private record Registration(Set<UUID> rips, Instant expiresAt) {}
}