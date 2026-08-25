package com.startupvalidationbot.radar.intel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic momentum and "why it matters" signals for feed cards.
 *
 * Momentum here means recent, corroborated attention - several independent sources, recent activity
 * and meaningful detected changes - not an AI judgement. Keeping it a pure function means the home
 * page can rank dozens of companies without a single model call or per-card database round trip.
 */
public final class MomentumRanker {

    private MomentumRanker() {
    }

    /**
     * @param sourceCount        distinct sources that have surfaced this company
     * @param daysSinceLastSeen  how stale the newest discovery is
     * @param meaningfulChanges  IMPORTANT or MAJOR changes detected recently
     * @param radarScore         the general importance score, used only as a mild tie-breaker
     */
    public static int momentumScore(int sourceCount, long daysSinceLastSeen, int meaningfulChanges,
            int radarScore) {
        int sources = Math.min(30, Math.max(0, sourceCount - 1) * 15);
        int freshness = daysSinceLastSeen <= 2 ? 25
                : daysSinceLastSeen <= 7 ? 18
                : daysSinceLastSeen <= 21 ? 10
                : daysSinceLastSeen <= 45 ? 4
                : 0;
        int changes = Math.min(30, Math.max(0, meaningfulChanges) * 12);
        int importance = Math.max(0, Math.min(100, radarScore)) / 6;
        return Math.max(0, Math.min(100, sources + freshness + changes + importance));
    }

    /**
     * Short, evidence-backed bullets for a feed card. Each line corresponds to something actually
     * recorded in the database, so nothing here is speculative.
     */
    public static List<String> whyItMatters(String companyName, int sourceCount, long daysSinceFirstSeen,
            int meaningfulChanges, boolean recentlyFunded, String accelerator, String acceleratorBatch,
            int radarScore) {
        List<String> reasons = new ArrayList<>();
        if (recentlyFunded) {
            reasons.add("A funding or investor change was detected recently.");
        }
        if (sourceCount >= 3) {
            reasons.add("Independently surfaced by " + sourceCount + " different sources.");
        } else if (sourceCount == 2) {
            reasons.add("Corroborated by a second independent source.");
        }
        if (meaningfulChanges > 0) {
            reasons.add(meaningfulChanges + " meaningful change" + (meaningfulChanges == 1 ? "" : "s")
                    + " detected since discovery.");
        }
        if (accelerator != null && !accelerator.isBlank()) {
            reasons.add(accelerator + (acceleratorBatch == null || acceleratorBatch.isBlank()
                    ? "" : " " + acceleratorBatch) + " company.");
        }
        if (daysSinceFirstSeen <= 1) {
            reasons.add("First seen in the last 24 hours.");
        }
        if (reasons.isEmpty()) {
            reasons.add(radarScore >= 60
                    ? "Scores well on general importance signals but has only one source so far."
                    : "Newly captured with limited corroboration; treat as a lead, not a finding.");
        }
        return List.copyOf(reasons);
    }

    /** A one-line badge for the card, describing why it appears in this particular section. */
    public static String highlight(String sectionKey, long daysSinceFirstSeen, int momentum,
            int personalScore, boolean recentlyFunded) {
        return switch (sectionKey == null ? "" : sectionKey.toLowerCase(Locale.ROOT)) {
            case "new-today" -> daysSinceFirstSeen <= 1 ? "Discovered today" : "Discovered this week";
            case "best-matches" -> "Personal relevance " + personalScore;
            case "high-momentum" -> "Momentum " + momentum;
            case "recently-funded" -> recentlyFunded ? "New funding signal" : "Funding activity";
            default -> "";
        };
    }
}
