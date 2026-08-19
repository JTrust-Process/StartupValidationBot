package com.startupvalidationbot.radar.intel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.startupvalidationbot.radar.intel.ChangeSignificance.Assessment;
import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;

/**
 * Deterministic detection of what actually changed between two company snapshots.
 *
 * The previous implementation emitted "Company description changed" for any byte difference, which
 * meant a reworded sentence looked the same as a Series A. This compares meaning: numeric traction
 * metrics, funding language, investor names and structural fields are extracted and diffed, and a
 * description edit that carries no new facts is suppressed entirely.
 *
 * Pure data in, pure data out - no database, no HTTP, no model.
 */
public final class SnapshotChangeDetector {

    /** A description rewrite this similar carries no new information. */
    private static final double TRIVIAL_WORDING_SIMILARITY = 0.82;

    private static final Pattern COUNT_METRIC = Pattern.compile(
            "([0-9][0-9,.]*)\\s*([kmb])?\\s+(users?|customers?|traders?|developers?|teams?|companies|merchants?|"
                    + "subscribers?|installs?|downloads?|employees?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MONEY_METRIC = Pattern.compile(
            "\\$\\s*([0-9][0-9,.]*)\\s*([kmb])?\\s+(?:in\\s+)?(?:monthly\\s+|annual\\s+)?"
                    + "(volume|revenue|arr|mrr|gmv|bookings)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FUNDING_LANGUAGE = Pattern.compile(
            "\\b(?:(?:raised|raises)\\s+(?:\\$[0-9][0-9,.]*\\s*[kmb]?|(?:a\\s+)?"
                    + "(?:pre-seed|seed|series [a-j])(?:\\s+round)?)"
                    + "|closed\\s+(?:a\\s+)?(?:\\$[0-9][0-9,.]*\\s*[kmb]?(?:\\s+round)?"
                    + "|(?:pre-seed|seed|series [a-j])(?:\\s+round)?)"
                    + "|(?:pre-seed|seed|series [a-j])\\s+(?:funding\\s+)?round"
                    + "|funding\\s+round|(?:pre-seed|seed|series [a-j])\\s+led\\s+by)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ACQUISITION_LANGUAGE = Pattern.compile(
            "\\b(acquired by|has been acquired|acquisition of|agreed to acquire)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SHUTDOWN_LANGUAGE = Pattern.compile(
            "\\b(shutting down|shut down|ceased operations|winding down|wound down)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CUSTOMER_LANGUAGE = Pattern.compile(
            "\\b(signed|onboarded|now serves|selected by|deployed at)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRODUCT_LAUNCH_LANGUAGE = Pattern.compile(
            "\\b(launched|launches|introducing|now available|general availability|shipped)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PARTNERSHIP_LANGUAGE = Pattern.compile(
            "\\b(partnership|partnered with|integration with|teamed up with)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern HIRING_LANGUAGE = Pattern.compile(
            "\\b(we're hiring|now hiring|open roles|join our team|careers)\\b", Pattern.CASE_INSENSITIVE);

    private static final List<String> KNOWN_INVESTORS = List.of(
            "Sequoia Capital", "Sequoia", "Andreessen Horowitz", "a16z", "Accel", "Benchmark",
            "Founders Fund", "General Catalyst", "Lightspeed", "Bessemer", "Index Ventures", "Greylock",
            "Thrive Capital", "GV", "NEA", "Menlo Ventures", "Kleiner Perkins", "Khosla Ventures",
            "Insight Partners", "Tiger Global", "Coatue", "Y Combinator", "First Round Capital",
            "Bain Capital Ventures", "Redpoint", "Craft Ventures", "Ribbit Capital", "Spark Capital");

    private SnapshotChangeDetector() {
    }

    /** One detected, classified change. */
    public record DetectedChange(String changeType, Tier significance, String summary, String previousValue,
            String currentValue, String whyItMatters) {
    }

    /**
     * @param previous flat snapshot fields (name, websiteUrl, description, sector, categories, ...)
     * @param current  the same fields from the newer snapshot
     */
    public static List<DetectedChange> detect(Map<String, String> previous, Map<String, String> current) {
        if (previous == null || previous.isEmpty()) return List.of();
        Map<String, String> before = normalise(previous);
        Map<String, String> after = normalise(current);

        List<DetectedChange> changes = new ArrayList<>();
        String beforeText = before.getOrDefault("description", "");
        String afterText = after.getOrDefault("description", "");

        changes.addAll(tractionChanges(beforeText, afterText));
        addIfNewlyPresent(changes, FUNDING_LANGUAGE, beforeText, afterText, ChangeSignificance.FUNDING_ROUND,
                "New funding language appeared in the source");
        addIfNewlyPresent(changes, ACQUISITION_LANGUAGE, beforeText, afterText, ChangeSignificance.ACQUISITION,
                "The source now describes an acquisition");
        addIfNewlyPresent(changes, SHUTDOWN_LANGUAGE, beforeText, afterText, ChangeSignificance.SHUTDOWN,
                "The source now describes a shutdown");
        addIfNewlyPresent(changes, CUSTOMER_LANGUAGE, beforeText, afterText, ChangeSignificance.MAJOR_CUSTOMER,
                "The source now describes a new customer");
        addIfNewlyPresent(changes, PRODUCT_LAUNCH_LANGUAGE, beforeText, afterText, ChangeSignificance.PRODUCT_LAUNCH,
                "The source now describes a product launch");
        addIfNewlyPresent(changes, PARTNERSHIP_LANGUAGE, beforeText, afterText, ChangeSignificance.PARTNERSHIP,
                "The source now describes a partnership");
        addIfNewlyPresent(changes, HIRING_LANGUAGE, beforeText, afterText, ChangeSignificance.JOB_OPENINGS,
                "The source now advertises open roles");

        changes.addAll(investorChanges(beforeText, afterText));
        changes.addAll(structuralChanges(before, after));

        if (changes.isEmpty()) {
            // Nothing of substance moved. Only report wording if it is a genuine rewrite, and even then
            // as Minor so it never reaches an alert.
            if (!beforeText.equals(afterText)
                    && tokenSimilarity(beforeText, afterText) < TRIVIAL_WORDING_SIMILARITY) {
                changes.add(build(ChangeSignificance.DESCRIPTION_WORDING, Double.NaN, "",
                        "Source description was rewritten", truncate(beforeText), truncate(afterText)));
            }
        }
        return List.copyOf(changes);
    }

    /** Convenience for callers that only care whether anything worth surfacing happened. */
    public static boolean hasMeaningfulChange(List<DetectedChange> changes) {
        return changes.stream().anyMatch(change -> change.significance().atLeast(Tier.IMPORTANT));
    }

    private static List<DetectedChange> tractionChanges(String before, String after) {
        Map<String, Double> beforeMetrics = metrics(before);
        Map<String, Double> afterMetrics = metrics(after);
        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<String, Double> entry : afterMetrics.entrySet()) {
            Double previousValue = beforeMetrics.get(entry.getKey());
            if (previousValue == null || previousValue == 0d) continue;
            double magnitude = (entry.getValue() - previousValue) / previousValue;
            if (Math.abs(magnitude) < 0.05) continue;

            String summary = String.format(Locale.ROOT, "%s moved from %s to %s (%+.0f%%)",
                    entry.getKey(), format(previousValue), format(entry.getValue()), magnitude * 100);
            changes.add(build(ChangeSignificance.TRACTION_GROWTH, magnitude, entry.getKey(), summary,
                    format(previousValue) + " " + entry.getKey(), format(entry.getValue()) + " " + entry.getKey()));
        }
        return changes;
    }

    private static List<DetectedChange> investorChanges(String before, String after) {
        Set<String> beforeInvestors = investors(before);
        Set<String> afterInvestors = investors(after);
        Set<String> added = new LinkedHashSet<>(afterInvestors);
        added.removeAll(beforeInvestors);
        if (added.isEmpty()) return List.of();

        String joined = String.join(", ", added);
        return List.of(build(ChangeSignificance.NEW_INVESTOR, Double.NaN, joined,
                "New investor named in the source: " + joined,
                beforeInvestors.isEmpty() ? "None captured" : String.join(", ", beforeInvestors), joined));
    }

    private static List<DetectedChange> structuralChanges(Map<String, String> before, Map<String, String> after) {
        List<DetectedChange> changes = new ArrayList<>();
        compare(before, after, "websiteUrl", ChangeSignificance.WEBSITE, "Website changed", changes);
        compare(before, after, "sector", ChangeSignificance.SECTOR, "Sector classification changed", changes);
        compare(before, after, "categories", ChangeSignificance.CATEGORY, "Category tags changed", changes);
        compare(before, after, "acceleratorBatch", ChangeSignificance.ACCELERATOR, "Accelerator batch changed",
                changes);
        compare(before, after, "headquarters", ChangeSignificance.NEW_MARKET, "Headquarters or market changed",
                changes);
        return changes;
    }

    private static void compare(Map<String, String> before, Map<String, String> after, String field,
            String changeType, String label, List<DetectedChange> changes) {
        String previousValue = before.getOrDefault(field, "");
        String currentValue = after.getOrDefault(field, "");
        if (previousValue.equals(currentValue)) return;
        // Newly captured data is enrichment rather than change.
        if (previousValue.isBlank()) return;
        changes.add(build(changeType, Double.NaN, currentValue, label, previousValue, currentValue));
    }

    private static void addIfNewlyPresent(List<DetectedChange> changes, Pattern pattern, String before,
            String after, String changeType, String label) {
        if (pattern.matcher(after).find() && !pattern.matcher(before).find()) {
            changes.add(build(changeType, Double.NaN, after, label, "Not previously mentioned", truncate(after)));
        }
    }

    private static DetectedChange build(String changeType, double magnitude, String evidence, String summary,
            String previousValue, String currentValue) {
        Assessment assessment = ChangeSignificance.classify(changeType, magnitude, evidence);
        return new DetectedChange(changeType, assessment.tier(), summary, previousValue, currentValue,
                assessment.whyItMatters());
    }

    static Map<String, Double> metrics(String text) {
        Map<String, Double> found = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return found;

        Matcher counts = COUNT_METRIC.matcher(text);
        while (counts.find()) {
            double value = scale(counts.group(1), counts.group(2));
            if (value > 0) found.merge(singular(counts.group(3)), value, Math::max);
        }
        Matcher money = MONEY_METRIC.matcher(text);
        while (money.find()) {
            double value = scale(money.group(1), money.group(2));
            if (value > 0) found.merge(singular(money.group(3)), value, Math::max);
        }
        return found;
    }

    static Set<String> investors(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return found;
        String haystack = text.toLowerCase(Locale.ROOT);
        for (String investor : KNOWN_INVESTORS) {
            String phrase = investor.toLowerCase(Locale.ROOT);
            if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phrase) + "(?![a-z0-9])")
                    .matcher(haystack).find()) {
                found.add(investor);
            }
        }
        // "Sequoia Capital" already implies "Sequoia"; keep the longer form only.
        found.removeIf(name -> found.stream()
                .anyMatch(other -> !other.equals(name) && other.toLowerCase(Locale.ROOT)
                        .startsWith(name.toLowerCase(Locale.ROOT))));
        return found;
    }

    /** Jaccard similarity over word sets: 1.0 means identical wording. */
    static double tokenSimilarity(String left, String right) {
        Set<String> a = tokens(left);
        Set<String> b = tokens(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")));
    }

    private static double scale(String number, String unit) {
        try {
            double value = Double.parseDouble(number.replace(",", ""));
            if (unit == null) return value;
            return switch (unit.toLowerCase(Locale.ROOT)) {
                case "k" -> value * 1_000;
                case "m" -> value * 1_000_000;
                case "b" -> value * 1_000_000_000;
                default -> value;
            };
        } catch (NumberFormatException error) {
            return 0d;
        }
    }

    private static String singular(String noun) {
        String lower = noun.toLowerCase(Locale.ROOT);
        return lower.endsWith("s") && !lower.endsWith("ss") ? lower.substring(0, lower.length() - 1) : lower;
    }

    private static String format(double value) {
        if (value >= 1_000_000_000) return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000);
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fK", value / 1_000);
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static Map<String, String> normalise(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (source == null) return copy;
        source.forEach((key, value) -> copy.put(key, value == null ? "" : value.trim()));
        return copy;
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
