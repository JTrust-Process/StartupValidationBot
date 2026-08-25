package com.startupvalidationbot.radar.intel;

import java.util.Locale;

/**
 * Deterministic significance tiers for detected company changes.
 *
 * The database decides what matters, not the model. AI may later rewrite a summary into nicer prose,
 * but it never promotes or demotes a tier: a funding round is Major whether or not a model agrees.
 */
public final class ChangeSignificance {

    public enum Tier {
        MINOR(0), INTERESTING(1), IMPORTANT(2), MAJOR(3);

        private final int rank;

        Tier(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }

        public boolean atLeast(Tier other) {
            return rank >= other.rank;
        }
    }

    /** Change types the detector can emit. */
    public static final String FUNDING_ROUND = "FUNDING_ROUND";
    public static final String NEW_INVESTOR = "NEW_INVESTOR";
    public static final String ACQUISITION = "ACQUISITION";
    public static final String SHUTDOWN = "SHUTDOWN";
    public static final String MAJOR_CUSTOMER = "MAJOR_CUSTOMER";
    public static final String TRACTION_GROWTH = "TRACTION_GROWTH";
    public static final String PRODUCT_LAUNCH = "PRODUCT_LAUNCH";
    public static final String NEW_MARKET = "NEW_MARKET";
    public static final String PARTNERSHIP = "PARTNERSHIP";
    public static final String FOUNDER_CHANGE = "FOUNDER_CHANGE";
    public static final String ACCELERATOR = "ACCELERATOR";
    public static final String REGULATORY = "REGULATORY";
    public static final String HEADCOUNT = "HEADCOUNT";
    public static final String JOB_OPENINGS = "JOB_OPENINGS";
    public static final String WEBSITE = "WEBSITE";
    public static final String SECTOR = "SECTOR";
    public static final String CATEGORY = "CATEGORY";
    public static final String DESCRIPTION_WORDING = "DESCRIPTION_WORDING";

    private ChangeSignificance() {
    }

    public record Assessment(Tier tier, String whyItMatters) {
    }

    /**
     * @param changeType one of the constants above
     * @param magnitude  optional relative change (for example 0.51 for +51% traction). Pass
     *                   {@link Double#NaN} when the change has no numeric magnitude.
     * @param evidence   free text used only for conservative escalation (for example "major partnership")
     */
    public static Assessment classify(String changeType, double magnitude, String evidence) {
        String type = changeType == null ? "" : changeType.trim().toUpperCase(Locale.ROOT);
        String haystack = evidence == null ? "" : evidence.toLowerCase(Locale.ROOT);

        return switch (type) {
            case FUNDING_ROUND -> new Assessment(Tier.MAJOR,
                    "A new funding round changes runway, ambition and the quality of external validation.");
            case NEW_INVESTOR -> new Assessment(
                    mentionsTierOneInvestor(haystack) ? Tier.MAJOR : Tier.IMPORTANT,
                    "A new investor on the cap table is independent validation from someone with diligence access.");
            case ACQUISITION -> new Assessment(Tier.MAJOR,
                    "An acquisition ends or fundamentally changes the company's independent trajectory.");
            case SHUTDOWN -> new Assessment(Tier.MAJOR,
                    "A shutdown is terminal and should remove the company from active tracking.");
            case MAJOR_CUSTOMER -> new Assessment(
                    mentionsNamedEnterprise(haystack) ? Tier.MAJOR : Tier.IMPORTANT,
                    "A large named customer is the strongest available evidence that the product actually works.");
            case TRACTION_GROWTH -> tractionTier(magnitude);
            case FOUNDER_CHANGE -> new Assessment(Tier.IMPORTANT,
                    "A founder change alters execution risk more than almost any other single event.");
            case ACCELERATOR -> new Assessment(Tier.IMPORTANT,
                    "Joining a credible accelerator signals a new stage and brings new investor exposure.");
            case REGULATORY -> new Assessment(Tier.IMPORTANT,
                    "A regulatory development can open or close the company's market outright.");
            case NEW_MARKET -> new Assessment(containsMajor(haystack) ? Tier.IMPORTANT : Tier.INTERESTING,
                    "Entering a new market shows the team believes the core product is repeatable.");
            case PARTNERSHIP -> new Assessment(containsMajor(haystack) ? Tier.IMPORTANT : Tier.INTERESTING,
                    "A partnership can supply distribution the company could not build alone.");
            case PRODUCT_LAUNCH -> new Assessment(Tier.INTERESTING,
                    "A new product shows direction and pace, though not yet demand.");
            case SECTOR, CATEGORY -> new Assessment(Tier.INTERESTING,
                    "A repositioning changes which companies this one competes with.");
            case HEADCOUNT -> new Assessment(Tier.INTERESTING,
                    "Headcount growth is a rough proxy for funded conviction.");
            case JOB_OPENINGS -> new Assessment(Tier.MINOR,
                    "Hiring activity is weak evidence on its own but useful in aggregate.");
            case WEBSITE -> new Assessment(Tier.MINOR,
                    "A website change is usually presentation rather than substance.");
            case DESCRIPTION_WORDING -> new Assessment(Tier.MINOR,
                    "Wording changed without any change in the underlying facts.");
            default -> new Assessment(Tier.MINOR, "Source content changed.");
        };
    }

    private static Assessment tractionTier(double magnitude) {
        if (Double.isNaN(magnitude)) {
            return new Assessment(Tier.INTERESTING,
                    "Traction language changed but no comparable number was captured.");
        }
        if (magnitude >= 1.0) {
            return new Assessment(Tier.MAJOR,
                    "Traction more than doubled, which is hard to achieve without real demand.");
        }
        if (magnitude >= 0.25) {
            return new Assessment(Tier.IMPORTANT,
                    "Traction grew materially rather than drifting.");
        }
        if (magnitude <= -0.25) {
            return new Assessment(Tier.IMPORTANT,
                    "Traction fell materially, which is as informative as growth.");
        }
        return new Assessment(Tier.INTERESTING, "Traction moved, but within normal noise.");
    }

    private static boolean mentionsTierOneInvestor(String haystack) {
        String[] names = {"sequoia", "andreessen", "a16z", "benchmark", "founders fund", "accel",
                "general catalyst", "lightspeed", "bessemer", "index ventures", "greylock", "thrive",
                "khosla", "kleiner perkins", "insight partners", "tiger global", "y combinator"};
        for (String name : names) {
            if (haystack.contains(name)) return true;
        }
        return false;
    }

    private static boolean mentionsNamedEnterprise(String haystack) {
        return haystack.contains("fortune 500") || haystack.contains("enterprise agreement")
                || haystack.contains("multi-year") || haystack.contains("flagship customer");
    }

    private static boolean containsMajor(String haystack) {
        return haystack.contains("major") || haystack.contains("strategic") || haystack.contains("global");
    }
}
