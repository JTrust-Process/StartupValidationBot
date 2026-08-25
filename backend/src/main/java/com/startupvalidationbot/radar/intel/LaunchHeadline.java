package com.startupvalidationbot.radar.intel;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Hacker News launch posts.
 *
 * These follow a stable, well-known convention, which makes them a rare case where a headline yields
 * a company name, an accelerator and a batch with no guessing:
 *   "Launch HN: Acme (YC S26) - Unified terminal for global markets"
 *   "Show HN: Beta - Open-source workflow engine"
 */
public final class LaunchHeadline {

    private static final Pattern LAUNCH = Pattern.compile(
            "^\\s*(?:launch|show)\\s+hn\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern BATCH = Pattern.compile(
            "\\(\\s*(YC)\\s+([WSXF]\\d{2})\\s*\\)", Pattern.CASE_INSENSITIVE);

    /** Separators HN posts use between the name and the tagline. */
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[\\-\\u2013\\u2014:]\\s+");

    private LaunchHeadline() {
    }

    public record LaunchPost(String companyName, String accelerator, String batch, String description) {
        public boolean usable() {
            return !companyName.isBlank();
        }

        static LaunchPost none() {
            return new LaunchPost("", "", "", "");
        }
    }

    public static LaunchPost parse(String title) {
        if (title == null || title.isBlank()) return LaunchPost.none();
        Matcher launch = LAUNCH.matcher(title.replaceAll("\\s+", " ").trim());
        if (!launch.find()) return LaunchPost.none();

        String remainder = launch.group(1).trim();
        String accelerator = "";
        String batch = "";

        Matcher batchMatcher = BATCH.matcher(remainder);
        if (batchMatcher.find()) {
            accelerator = "Y Combinator";
            batch = batchMatcher.group(2).toUpperCase(Locale.ROOT);
            remainder = batchMatcher.replaceAll(" ").replaceAll("\\s+", " ").trim();
        }

        String namePart = remainder;
        String description = "";
        Matcher separator = SEPARATOR.matcher(remainder);
        if (separator.find()) {
            namePart = remainder.substring(0, separator.start()).trim();
            description = remainder.substring(separator.end()).trim();
        }

        namePart = namePart.replaceAll("[,;]+$", "").trim();
        if (namePart.isEmpty() || namePart.length() > 60 || namePart.split("\\s+").length > 5) {
            return LaunchPost.none();
        }
        return new LaunchPost(namePart, accelerator, batch, description);
    }
}
