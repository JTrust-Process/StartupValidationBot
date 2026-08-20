package com.startupvalidationbot.radar.intel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;
import com.startupvalidationbot.radar.intel.SnapshotChangeDetector.DetectedChange;

class SnapshotChangeDetectorTest {

    private static Map<String, String> snapshot(String description) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("description", description);
        values.put("websiteUrl", "https://arbital.test");
        values.put("sector", "Fintech");
        values.put("categories", "Fintech, Trading");
        return values;
    }

    @Test
    void detectsFundingTractionAndInvestorChangesWithTiers() {
        List<DetectedChange> changes = SnapshotChangeDetector.detect(
                snapshot("Arbital is a trading terminal. 2,700 traders and $435M monthly volume."),
                snapshot("Arbital is a trading terminal. 4,100 traders and $610M monthly volume. "
                        + "Series A led by Sequoia Capital."));

        assertThat(changes).extracting(DetectedChange::changeType)
                .contains("TRACTION_GROWTH", "FUNDING_ROUND", "NEW_INVESTOR");
        assertThat(changes).filteredOn(change -> change.changeType().equals("FUNDING_ROUND"))
                .allMatch(change -> change.significance() == Tier.MAJOR);
        assertThat(changes).filteredOn(change -> change.changeType().equals("NEW_INVESTOR"))
                .allMatch(change -> change.significance() == Tier.MAJOR);
        assertThat(changes).filteredOn(change -> change.changeType().equals("TRACTION_GROWTH"))
                .allMatch(change -> change.significance() == Tier.IMPORTANT);
        assertThat(SnapshotChangeDetector.hasMeaningfulChange(changes)).isTrue();
        assertThat(changes).allMatch(change -> !change.whyItMatters().isBlank());
    }

    @Test
    void suppressesTrivialRewording() {
        List<DetectedChange> changes = SnapshotChangeDetector.detect(
                snapshot("We build fast developer tooling for teams."),
                snapshot("We build fast developer tooling for engineering teams."));

        assertThat(changes).isEmpty();
    }

    @Test
    void reportsAGenuineRewriteAsMinorOnly() {
        List<DetectedChange> changes = SnapshotChangeDetector.detect(
                snapshot("We build fast developer tooling for teams."),
                snapshot("Completely different company doing satellite imagery analysis for insurers."));

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).changeType()).isEqualTo("DESCRIPTION_WORDING");
        assertThat(changes.get(0).significance()).isEqualTo(Tier.MINOR);
        assertThat(SnapshotChangeDetector.hasMeaningfulChange(changes)).isFalse();
    }

    @Test
    void returnsNothingWhenThereIsNoPriorSnapshot() {
        assertThat(SnapshotChangeDetector.detect(Map.of(), snapshot("Anything at all."))).isEmpty();
    }

    @Test
    void extractsExplicitEventsFromAnInitialPublicNewsItem() {
        List<DetectedChange> changes = SnapshotChangeDetector.detectInitialPublicEvent(
                "Acme raises $20M Series A led by Sequoia Capital and launches its enterprise product.");

        assertThat(changes).extracting(DetectedChange::changeType)
                .contains("FUNDING_ROUND", "NEW_INVESTOR", "PRODUCT_LAUNCH");
        assertThat(changes).filteredOn(change -> change.changeType().equals("FUNDING_ROUND"))
                .allMatch(change -> change.significance() == Tier.MAJOR);
        assertThat(changes).filteredOn(change -> change.changeType().equals("NEW_INVESTOR"))
                .allMatch(change -> change.significance() == Tier.MAJOR);
    }

    @Test
    void treatsNewlyCapturedFieldsAsEnrichmentRatherThanChange() {
        Map<String, String> before = snapshot("Steady description.");
        before.put("headquarters", "");
        Map<String, String> after = snapshot("Steady description.");
        after.put("headquarters", "New York");

        assertThat(SnapshotChangeDetector.detect(before, after)).isEmpty();
    }

    @Test
    void extractsComparableMetrics() {
        var metrics = SnapshotChangeDetector.metrics("4,100 traders and $610M monthly volume");
        assertThat(metrics).containsEntry("trader", 4100d).containsEntry("volume", 610_000_000d);
    }

    @Test
    void prefersTheLongestInvestorNameSoOneFirmIsNotCountedTwice() {
        assertThat(SnapshotChangeDetector.investors("Round led by Sequoia Capital"))
                .containsExactly("Sequoia Capital");
    }

    @Test
    void doesNotTreatOrdinaryRaisedLedOrAccelerationLanguageAsFunding() {
        List<DetectedChange> changes = SnapshotChangeDetector.detect(
                snapshot("The founder runs a product team."),
                snapshot("The founder raised concerns about acceleration and led by example."));

        assertThat(changes).extracting(DetectedChange::changeType)
                .doesNotContain("FUNDING_ROUND", "NEW_INVESTOR");
        assertThat(SnapshotChangeDetector.investors("Our acceleration engine is faster.")).isEmpty();
    }
}
