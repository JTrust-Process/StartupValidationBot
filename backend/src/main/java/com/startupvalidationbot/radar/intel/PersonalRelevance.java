package com.startupvalidationbot.radar.intel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.startupvalidationbot.radar.intel.InterestProfile.Interest;
import com.startupvalidationbot.radar.intel.InteractionSignal.Summary;

/**
 * Personal relevance: how much this specific user is likely to care, independent of whether the
 * company is a good investment (that stays in Deal Scout) and independent of the Radar Score.
 *
 * Every point is attributable to a stated reason. There is no learned model and no hidden weighting.
 */
public final class PersonalRelevance {

    private static final int BASE = 18;
    private static final int MAX_INTEREST_POINTS = 62;
    private static final int MAX_SIGNAL_POINTS = 12;
    /** Matching several distinct interests is stronger evidence than matching one of them twice. */
    private static final int MULTI_INTEREST_BONUS = 6;
    private static final int MAX_MULTI_INTEREST_BONUS = 18;
    /** An ignored company can never float back to the top of a feed. */
    private static final int IGNORED_CEILING = 12;

    private PersonalRelevance() {
    }

    public record Result(int score, List<String> matchedInterests, List<String> reasons) {
    }

    public static Result score(String name, String description, String sector, List<String> categories,
            InterestProfile profile, Summary signals) {
        String corpus = corpus(name, description, sector, categories);
        Summary safeSignals = signals == null ? Summary.empty() : signals;
        InterestProfile safeProfile = profile == null || profile.isEmpty()
                ? InterestProfile.defaultProfile()
                : profile;

        List<String> matched = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int interestPoints = 0;

        for (Interest interest : safeProfile.interests()) {
            if (!interest.matches(corpus)) continue;
            matched.add(interest.label());
            interestPoints += interest.weight();
        }
        int breadthBonus = Math.min(MAX_MULTI_INTEREST_BONUS,
                Math.max(0, matched.size() - 1) * MULTI_INTEREST_BONUS);
        boolean interestCapped = interestPoints > MAX_INTEREST_POINTS;
        interestPoints = Math.min(interestPoints, MAX_INTEREST_POINTS) + breadthBonus;

        if (matched.isEmpty()) {
            reasons.add("No configured interest matched this company's description or categories.");
        } else {
            reasons.add("Matches your interests: " + String.join(", ", matched) + ".");
            if (breadthBonus > 0) {
                reasons.add("Sits across " + matched.size() + " of your interests, not just one.");
            }
            if (interestCapped) {
                reasons.add("Interest contribution is capped so a keyword-dense description cannot dominate.");
            }
        }

        int signalPoints = 0;
        if (safeSignals.watch() > 0) {
            signalPoints += 7;
            reasons.add("You added this company to your watchlist.");
        }
        if (safeSignals.deepDive() > 0) {
            signalPoints += 4;
            reasons.add("You ran a Deep Dive on this company.");
        }
        if (safeSignals.visit() > 0) {
            signalPoints += 2;
            reasons.add("You opened this company's website from Radar.");
        }
        signalPoints = Math.min(signalPoints, MAX_SIGNAL_POINTS);

        int score = clamp(BASE + interestPoints + signalPoints);

        if (safeSignals.ignored()) {
            score = Math.min(score, IGNORED_CEILING);
            reasons.add("You ignored this company, so its relevance is held down deliberately.");
        }

        reasons.add("Personal relevance measures why you might care. It is not an investment signal.");
        return new Result(score, List.copyOf(matched), List.copyOf(reasons));
    }

    private static String corpus(String name, String description, String sector, List<String> categories) {
        StringBuilder builder = new StringBuilder();
        append(builder, name);
        append(builder, description);
        append(builder, sector);
        if (categories != null) categories.forEach(category -> append(builder, category));
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) return;
        builder.append(' ').append(value);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
