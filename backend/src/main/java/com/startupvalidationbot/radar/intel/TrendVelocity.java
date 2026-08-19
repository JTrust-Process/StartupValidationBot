package com.startupvalidationbot.radar.intel;

import java.util.Locale;

/**
 * Discovery velocity for a trend.
 *
 * A percentage computed from one or two prior data points is noise dressed as insight, so a
 * percentage is only produced once the earlier window holds enough companies to support it.
 * Otherwise the trend reports absolute counts and says so.
 */
public final class TrendVelocity {

    /** Below this many companies in the prior window, a percentage is not meaningful. */
    private static final int MIN_PRIOR_FOR_PERCENTAGE = 3;
    private static final double RISING_THRESHOLD = 0.25;
    private static final double COOLING_THRESHOLD = -0.25;

    public enum Direction { NEW, RISING, STEADY, COOLING, UNKNOWN }

    public enum Confidence { LOW, MEDIUM, HIGH }

    private TrendVelocity() {
    }

    public record Velocity(int recentCount, int priorCount, Direction direction, boolean sufficientHistory,
            String note) {
    }

    /**
     * @param recentCount    companies first discovered in the current window
     * @param priorCount     companies first discovered in the immediately preceding window
     * @param hasPriorWindow false when the database does not go back far enough to have a prior window
     * @param windowDays     length of each window, used only for the human-readable note
     */
    public static Velocity compute(int recentCount, int priorCount, boolean hasPriorWindow, int windowDays) {
        int recent = Math.max(0, recentCount);
        int prior = Math.max(0, priorCount);

        if (!hasPriorWindow) {
            return new Velocity(recent, prior, Direction.NEW, false, String.format(Locale.ROOT,
                    "%d compan%s discovered in the last %d days. No earlier window exists yet, so no trend "
                            + "direction is claimed.", recent, recent == 1 ? "y" : "ies", windowDays));
        }

        if (prior == 0) {
            return new Velocity(recent, prior, recent > 0 ? Direction.NEW : Direction.UNKNOWN, false,
                    String.format(Locale.ROOT,
                            "%d compan%s in the last %d days versus none in the previous %d. Too little history "
                                    + "for a growth rate.", recent, recent == 1 ? "y" : "ies", windowDays, windowDays));
        }

        if (prior < MIN_PRIOR_FOR_PERCENTAGE) {
            Direction direction = recent > prior ? Direction.RISING
                    : recent < prior ? Direction.COOLING : Direction.STEADY;
            return new Velocity(recent, prior, direction, false, String.format(Locale.ROOT,
                    "%d compan%s in the last %d days versus %d in the previous %d. Counts are too small for a "
                            + "percentage.", recent, recent == 1 ? "y" : "ies", windowDays, prior, windowDays));
        }

        double change = (recent - (double) prior) / prior;
        Direction direction = change >= RISING_THRESHOLD ? Direction.RISING
                : change <= COOLING_THRESHOLD ? Direction.COOLING : Direction.STEADY;
        return new Velocity(recent, prior, direction, true, String.format(Locale.ROOT,
                "%d compan%s in the last %d days versus %d in the previous %d (%+.0f%%).",
                recent, recent == 1 ? "y" : "ies", windowDays, prior, windowDays, change * 100));
    }

    /**
     * Confidence reflects how much evidence supports the trend at all, not how strong it looks.
     */
    public static Confidence confidence(int companyCount, boolean sufficientHistory, int distinctSources) {
        if (companyCount >= 6 && sufficientHistory && distinctSources >= 2) return Confidence.HIGH;
        if (companyCount >= 4 || (companyCount >= 3 && distinctSources >= 2)) return Confidence.MEDIUM;
        return Confidence.LOW;
    }
}
