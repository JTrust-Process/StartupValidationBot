package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.assertThat;
import static com.startupvalidationbot.diligence.DiligenceRefreshSelector.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiligenceRefreshSelectorTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 8, 0);

    @Test void reservesTurnsAndSpillsUnusedCapacityWithoutDuplicates() {
        List<Candidate> rows = new ArrayList<>();
        for (int i = 1; i <= 100; i++) rows.add(row(i, i <= 25 ? null : NOW.minusDays(i <= 75 ? 20 : 2),
                i > 50 && i <= 75 ? "AMBIGUOUS" : "CONFIRMED"));
        var result = select(rows, 25, NOW);
        assertThat(result.ids()).hasSize(25).doesNotHaveDuplicates();
        assertThat(result.counts().values()).allMatch(count -> count > 0);
        assertThat(select(rows.reversed(), 25, NOW)).isEqualTo(result);
        assertThat(select(rows.subList(0, 25), 25, NOW).ids()).hasSize(25);
    }

    @Test void repeatedDailyRunsCoverOldUnresolvedAndNeverProcessedDespiteNewArrivals() {
        List<Candidate> rows = new ArrayList<>();
        for (int i = 1; i <= 100; i++) rows.add(row(i, i <= 50 ? null : NOW.minusDays(40), "LIKELY"));
        var seen = new HashSet<Long>();
        for (int day = 0; day < 12; day++) {
            var now = NOW.plusDays(day);
            var result = select(rows, 25, now);
            seen.addAll(result.ids());
            rows.replaceAll(c -> result.ids().contains(c.id()) ? new Candidate(c.id(), c.matchStatus(),
                    "NEEDS_REVIEW", c.createdAt(), now, now, now) : c);
            for (int j = 0; j < 30; j++) rows.add(new Candidate(1000 + day * 30 + j, "CONFIRMED", null,
                    now, now, null, null));
        }
        for (long id = 1; id <= 100; id++) assertThat(seen).contains(id);
    }

    @Test void retryAgePreventsFailedRowsFromHoggingQuotaAndEnforcesDailyCooldown() {
        var failed = new Candidate(1, "LIKELY", null, NOW.minusDays(50), NOW, NOW, null);
        assertThat(select(List.of(failed), 25, NOW).ids()).isEmpty();
        assertThat(select(List.of(failed, row(2, NOW.minusDays(20), "LIKELY")), 25, NOW).ids()).containsExactly(2L);
        assertThat(select(List.of(failed), 25, NOW.plusDays(1)).ids()).containsExactly(1L);
    }

    @Test void subFourBoundStillRotatesReservedClassesAcrossDays() {
        var unresolved = row(1, NOW.minusDays(40), "AMBIGUOUS");
        var seen = new HashSet<Long>();
        for (int day = 0; day < 4; day++) seen.addAll(select(List.of(unresolved,
                row(100 + day, null, "CONFIRMED")), 1, NOW.plusDays(day)).ids());
        assertThat(seen).contains(1L);
    }

    @Test void firstAttemptIsEligibleDespiteDatabaseTimestampRounding() {
        var inserted = new Candidate(1, "CONFIRMED", null, NOW.plusNanos(1000), NOW, null, null);
        assertThat(select(List.of(inserted), 25, NOW).ids()).containsExactly(1L);
    }

    private static Candidate row(long id, LocalDateTime success, String match) {
        return new Candidate(id, match, success == null ? null : "PARTIAL", NOW.minusDays(100).plusMinutes(id),
                NOW, success, success);
    }
}
