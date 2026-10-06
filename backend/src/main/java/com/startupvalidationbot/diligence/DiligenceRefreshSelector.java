package com.startupvalidationbot.diligence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Oldest attempt first within each reserved class, independently of offering update churn. */
public final class DiligenceRefreshSelector {
    public static final int REFRESH_DAYS = 7;
    public enum Bucket { NEVER_PROCESSED, OVERDUE, UNRESOLVED, CURRENT }
    private static final List<Bucket> ROUND = List.of(Bucket.NEVER_PROCESSED, Bucket.OVERDUE,
            Bucket.UNRESOLVED, Bucket.CURRENT, Bucket.OVERDUE, Bucket.NEVER_PROCESSED,
            Bucket.UNRESOLVED, Bucket.OVERDUE);
    private DiligenceRefreshSelector() { }

    public record Candidate(long id, String matchStatus, String packetStatus, LocalDateTime createdAt,
            LocalDateTime updatedAt, LocalDateTime lastAttemptAt, LocalDateTime lastSuccessAt) {
        public boolean neverProcessed() { return lastAttemptAt == null && lastSuccessAt == null; }
        public boolean overdue(LocalDateTime now) {
            return !neverProcessed() && (lastSuccessAt == null
                    || !lastSuccessAt.isAfter(now.minusDays(REFRESH_DAYS)));
        }
        public boolean unresolved() {
            return List.of("LIKELY", "AMBIGUOUS").contains(matchStatus) || "NEEDS_REVIEW".equals(packetStatus);
        }
        public LocalDateTime nextEligibleAt() {
            return lastAttemptAt == null ? createdAt : lastAttemptAt.plusDays(1);
        }
    }
    public record Selection(List<Long> ids, Map<Bucket, Integer> counts) { }

    public static Selection select(List<Candidate> candidates, int bound, LocalDateTime now) {
        Comparator<Candidate> oldest = Comparator.comparing(
                (Candidate c) -> c.lastAttemptAt() == null ? c.createdAt() : c.lastAttemptAt())
                .thenComparingLong(Candidate::id);
        List<Candidate> eligible = candidates.stream().filter(c -> c.lastAttemptAt() == null
                || !c.nextEligibleAt().isAfter(now))
                .sorted(oldest).toList();
        Map<Bucket, List<Candidate>> pools = new EnumMap<>(Bucket.class);
        pools.put(Bucket.NEVER_PROCESSED, eligible.stream().filter(Candidate::neverProcessed).toList());
        pools.put(Bucket.OVERDUE, eligible.stream().filter(c -> c.overdue(now)).toList());
        pools.put(Bucket.UNRESOLVED, eligible.stream().filter(Candidate::unresolved).toList());
        pools.put(Bucket.CURRENT, eligible.stream().filter(c -> !c.neverProcessed() && !c.overdue(now)).toList());
        Map<Bucket, Integer> counts = new EnumMap<>(Bucket.class);
        for (Bucket bucket : Bucket.values()) counts.put(bucket, 0);
        List<Long> ids = new ArrayList<>();
        Set<Long> selected = new HashSet<>();
        int limit = Math.max(1, Math.min(bound, 100));
        int start = limit < 4 ? Math.floorMod((int) now.toLocalDate().toEpochDay(), 4) : 0;
        while (ids.size() < limit) {
            int before = ids.size();
            for (int slot = 0; slot < ROUND.size(); slot++) {
                Bucket bucket = ROUND.get((start + slot) % ROUND.size());
                Candidate next = pools.get(bucket).stream().filter(c -> !selected.contains(c.id())).findFirst().orElse(null);
                if (next != null) {
                    selected.add(next.id()); ids.add(next.id()); counts.merge(bucket, 1, Integer::sum);
                }
                if (ids.size() == limit) break;
            }
            if (ids.size() == before) break;
        }
        return new Selection(List.copyOf(ids), Map.copyOf(counts));
    }
}
