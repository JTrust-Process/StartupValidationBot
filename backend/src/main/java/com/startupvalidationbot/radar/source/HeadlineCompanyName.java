package com.startupvalidationbot.radar.source;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic extraction of a startup name from a news or launch headline.
 *
 * RSS feeds give us headlines, not company records. Storing the headline as the company name
 * produces one junk identity per article, so this extractor is deliberately conservative: it either
 * returns a name it is confident about, or it returns {@link Confidence#NONE} and the caller skips
 * company creation rather than inventing an identity.
 *
 * No AI is involved. Routine discovery must not incur model cost.
 */
public final class HeadlineCompanyName {

    public enum Confidence { HIGH, MEDIUM, NONE }

    /** Verbs that mark the boundary between the company name and the rest of the headline. */
    private static final Pattern ACTION = Pattern.compile(
            "\\b(raises|raised|raising|secures|secured|lands|landed|closes|closed|nabs|nabbed|bags|bagged|"
                    + "launches|launched|unveils|unveiled|debuts|debuted|introduces|introduced|"
                    + "emerges from stealth|emerged from stealth|exits stealth|exited stealth|comes out of stealth|"
                    + "acquires|acquired by|announces|announced|picks up|pulls in|snaps up|rolls out)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Leading editorial prefixes such as "Exclusive:" that precede the company name. */
    private static final Pattern LEAD_LABEL = Pattern.compile(
            "^\\s*(exclusive|breaking|scoop|report|update|opinion|analysis|first look)\\s*[:\\-]\\s*",
            Pattern.CASE_INSENSITIVE);

    /**
     * Descriptor nouns. Anything up to and including the last one is scene-setting, and the company
     * name follows: "Fintech startup Acme" -> "Acme".
     */
    private static final Pattern DESCRIPTOR = Pattern.compile(
            "\\b(startups?|compan(?:y|ies)|firms?|makers?|providers?|platforms?|unicorns?|vendors?|"
                    + "developers?|manufacturers?|builders?|specialists?)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Trailing publisher attribution: " - TechCrunch", " | VentureBeat". */
    private static final Pattern PUBLISHER_SUFFIX = Pattern.compile("\\s+[\\-|\\u2013\\u2014]\\s+[^\\-|\\u2013\\u2014]{1,40}$");

    /** Headlines that are commentary rather than company announcements. */
    private static final Set<String> COMMENTARY_OPENERS = Set.of(
            "why", "how", "what", "when", "where", "who", "meet", "inside", "here", "these", "this",
            "the", "a", "an", "it", "we", "they", "you", "our", "my", "after", "before", "as", "is",
            "are", "can", "could", "should", "will", "does", "do", "did", "top", "best", "everything");

    /** Words that can never stand alone as a company name. */
    private static final Set<String> GENERIC_NAMES = Set.of(
            "startup", "startups", "company", "companies", "firm", "founder", "founders", "investors",
            "investor", "vc", "vcs", "team", "teams", "report", "market", "industry", "sector");

    private static final int MAX_NAME_WORDS = 4;

    private HeadlineCompanyName() {
    }

    public record Extraction(String name, Confidence confidence) {
        public boolean usable() {
            return confidence != Confidence.NONE && !name.isBlank();
        }

        static Extraction none() {
            return new Extraction("", Confidence.NONE);
        }
    }

    public static Extraction extract(String headline) {
        String cleaned = clean(headline);
        if (cleaned.isEmpty()) return Extraction.none();

        cleaned = PUBLISHER_SUFFIX.matcher(cleaned).replaceAll("");
        cleaned = LEAD_LABEL.matcher(cleaned).replaceAll("");
        if (cleaned.isEmpty()) return Extraction.none();

        Matcher action = ACTION.matcher(cleaned);
        if (!action.find() || action.start() == 0) return Extraction.none();

        String prefix = cleaned.substring(0, action.start()).trim();
        prefix = stripTrailingPunctuation(prefix);
        if (prefix.isEmpty()) return Extraction.none();

        boolean descriptorRemoved = false;
        Matcher descriptor = DESCRIPTOR.matcher(prefix);
        int descriptorEnd = -1;
        while (descriptor.find()) {
            descriptorEnd = descriptor.end();
        }
        if (descriptorEnd >= 0) {
            String remainder = prefix.substring(descriptorEnd).trim();
            // "Acme, a fintech startup, raises..." leaves nothing useful after the descriptor.
            if (remainder.isEmpty()) return Extraction.none();
            prefix = stripTrailingPunctuation(remainder);
            descriptorRemoved = true;
        }

        if (!isPlausibleName(prefix, descriptorRemoved)) return Extraction.none();

        List<String> words = List.of(prefix.split("\\s+"));
        Confidence confidence = words.size() <= 2 && !descriptorRemoved ? Confidence.HIGH
                : words.size() <= MAX_NAME_WORDS ? Confidence.MEDIUM : Confidence.NONE;
        return confidence == Confidence.NONE ? Extraction.none() : new Extraction(prefix, confidence);
    }

    private static boolean isPlausibleName(String candidate, boolean descriptorRemoved) {
        if (candidate.length() < 2 || candidate.length() > 60) return false;
        // A name should not carry sentence structure.
        if (candidate.contains(":") || candidate.contains(";") || candidate.contains("?")
                || candidate.contains("!") || candidate.contains("\"")) {
            return false;
        }

        String[] words = candidate.split("\\s+");
        if (words.length == 0 || words.length > MAX_NAME_WORDS) return false;

        String firstLower = words[0].toLowerCase(Locale.ROOT);
        if (!descriptorRemoved && COMMENTARY_OPENERS.contains(firstLower)) return false;
        if (words.length == 1 && GENERIC_NAMES.contains(firstLower)) return false;

        // Headlines capitalise company names. Requiring an initial capital (or a digit, for names such
        // as "1Password") filters out sentence fragments that survive the patterns above.
        char first = candidate.charAt(0);
        if (!Character.isUpperCase(first) && !Character.isDigit(first)) return false;

        // Every word must look like part of a proper noun rather than prose.
        for (String word : words) {
            String bare = word.replaceAll("[^\\p{L}\\p{N}]", "");
            if (bare.isEmpty()) return false;
            char lead = bare.charAt(0);
            boolean properLike = Character.isUpperCase(lead) || Character.isDigit(lead)
                    || bare.length() <= 3; // allow connectors such as "of", "AI", "de"
            if (!properLike) return false;
        }
        return true;
    }

    private static String stripTrailingPunctuation(String value) {
        return value.replaceAll("[\\s,;:\\-\\u2013\\u2014]+$", "").trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
