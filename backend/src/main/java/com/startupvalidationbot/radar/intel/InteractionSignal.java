package com.startupvalidationbot.radar.intel;

import java.util.Locale;

/**
 * User interactions worth remembering. Persisted now so later personalisation has real history;
 * today they only make small, explainable adjustments to personal relevance.
 */
public enum InteractionSignal {
    WATCH,
    IGNORE,
    DEEP_DIVE,
    VISIT;

    public static InteractionSignal parse(String value) {
        if (value == null) throw new IllegalArgumentException("signal type is required");
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "WATCH" -> WATCH;
            case "IGNORE" -> IGNORE;
            case "DEEP_DIVE", "DEEPDIVE" -> DEEP_DIVE;
            case "VISIT" -> VISIT;
            default -> throw new IllegalArgumentException("Unsupported interaction signal: " + value);
        };
    }

    /** Counts of each signal recorded for one company. */
    public record Summary(int watch, int ignore, int deepDive, int visit) {
        public static Summary empty() {
            return new Summary(0, 0, 0, 0);
        }

        public boolean ignored() {
            return ignore > 0;
        }

        public int engagementCount() {
            return watch + deepDive + visit;
        }
    }
}
