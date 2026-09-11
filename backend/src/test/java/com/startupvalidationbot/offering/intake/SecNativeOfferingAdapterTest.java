package com.startupvalidationbot.offering.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingSourceAdapter;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status;

class SecNativeOfferingAdapterTest {
    @Test
    void normalizesSupportedIntermediariesAndCurrentLifecycle() {
        Candidate candidate = candidate("OpenDeal Portal LLC", "C-U/A", LocalDate.now().plusDays(30));
        var nativeCandidate = SecNativeOfferingAdapter.from(candidate, LocalDateTime.now());
        assertThat(nativeCandidate.platform()).isEqualTo("Republic");
        assertThat(nativeCandidate.status()).isEqualTo(Status.ACTIVE);
        assertThat(nativeCandidate.actionableRegCf()).isTrue();

        assertThat(SecNativeOfferingAdapter.from(candidate("Wefunder Portal LLC", "C", LocalDate.now().plusDays(30)),
                LocalDateTime.now()).platform()).isEqualTo("Wefunder");
        assertThat(SecNativeOfferingAdapter.from(candidate("StartEngine Primary LLC", "C", LocalDate.now().plusDays(30)),
                LocalDateTime.now()).platform()).isEqualTo("StartEngine");
    }

    @Test
    void withdrawalTerminationAndPastDeadlineAreNotActionable() {
        assertThat(SecNativeOfferingAdapter.status(candidate("Republic", "C-W", null))).isEqualTo(Status.WITHDRAWN);
        assertThat(SecNativeOfferingAdapter.status(candidate("Republic", "C-TR", null))).isEqualTo(Status.TERMINATED);
        assertThat(SecNativeOfferingAdapter.status(candidate("Republic", "C", LocalDate.now().minusDays(1))))
                .isEqualTo(Status.CLOSED);
    }

    @Test
    void onlyRecentInitialOrAmendedSecFilingsWithoutDeadlinesAreReviewable() {
        var recent = SecNativeOfferingAdapter.from(candidate("Republic", "C", null), LocalDateTime.now());
        var update = SecNativeOfferingAdapter.from(candidate("Republic", "C-U", null), LocalDateTime.now());
        var old = SecNativeOfferingAdapter.from(
                candidate("Republic", "C", null, LocalDate.now().minusDays(31)), LocalDateTime.now());

        assertThat(recent.status()).isEqualTo(Status.UNKNOWN);
        assertThat(recent.reviewableRecentSecRegCf()).isTrue();
        assertThat(update.reviewableRecentSecRegCf()).isFalse();
        assertThat(old.reviewableRecentSecRegCf()).isFalse();
    }

    @Test
    void failedDetailRequestsStillCountTowardTheConfiguredBound() {
        Candidate candidate = candidate("Republic", "C", null);
        OfferingSourceAdapter failingSource = new OfferingSourceAdapter() {
            @Override public List<Candidate> fetchRecent() { return java.util.Collections.nCopies(25, candidate); }
            @Override public List<Candidate> fetchBaseline() { return List.of(); }
            @Override public Candidate enrich(Candidate value) { throw new IllegalStateException("not found"); }
        };

        var result = new SecNativeOfferingAdapter(failingSource).discover(25, 10);

        assertThat(result.requests()).isEqualTo(11);
        assertThat(result.detailRequests()).isEqualTo(10);
        assertThat(result.candidates()).hasSize(25);
        assertThat(result.errors()).hasSize(10);
    }

    private static Candidate candidate(String intermediary, String form, LocalDate deadline) {
        return candidate(intermediary, form, deadline, LocalDate.now());
    }

    private static Candidate candidate(String intermediary, String form, LocalDate deadline, LocalDate filingDate) {
        return new Candidate("GridCool Systems, Inc.", "0001234567", "https://gridcool.example",
                "UNKNOWN", intermediary, null, "https://republic.com/gridcool",
                "https://www.sec.gov/Archives/example", "0001234567-26-000001", "020-12345",
                form, filingDate, "Crowd SAFE", null, null, null, null, deadline, null,
                "TEST", Map.of());
    }
}
