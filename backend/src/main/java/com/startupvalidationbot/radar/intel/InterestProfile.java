package com.startupvalidationbot.radar.intel;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The user's configurable interests.
 *
 * Personal relevance must be explainable, so an interest is just a label, a weight and the keywords
 * that count as evidence for it. Nothing here is learned or hidden.
 */
public record InterestProfile(List<Interest> interests) {

    public record Interest(String label, int weight, List<String> keywords) {
        public Interest {
            label = label == null ? "" : label.trim();
            weight = Math.max(1, Math.min(weight, 25));
            keywords = keywords == null ? List.of() : keywords.stream()
                    .map(keyword -> keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT))
                    .filter(keyword -> !keyword.isEmpty())
                    .distinct()
                    .toList();
        }

        /**
         * True when the label or any keyword appears in the corpus as a whole word.
         *
         * Substring matching is not safe here: "erp" occurs inside "perpetuals" and "ai" inside
         * "retail" and "email", which would silently inflate personal relevance for companies that
         * have nothing to do with the interest.
         */
        public boolean matches(String lowercaseCorpus) {
            if (lowercaseCorpus == null || lowercaseCorpus.isBlank()) return false;
            if (!label.isBlank() && containsWord(lowercaseCorpus, label.toLowerCase(Locale.ROOT))) return true;
            return keywords.stream().anyMatch(keyword -> containsWord(lowercaseCorpus, keyword));
        }

        private static boolean containsWord(String corpus, String term) {
            if (term.isBlank()) return false;
            return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])")
                    .matcher(corpus).find();
        }
    }

    public InterestProfile {
        interests = interests == null ? List.of() : interests.stream()
                .filter(interest -> interest != null && !interest.label().isBlank())
                .limit(40)
                .toList();
    }

    public boolean isEmpty() {
        return interests.isEmpty();
    }

    /** The starting profile. Editable at runtime; this is only the seed. */
    public static InterestProfile defaultProfile() {
        return new InterestProfile(List.of(
                new Interest("Software", 10, List.of("software", "saas", "application", "platform")),
                new Interest("AI", 16, List.of("ai", "artificial intelligence", "machine learning", "llm", "llms",
                        "foundation model", "agent", "agents", "agentic", "inference")),
                new Interest("Automation", 14, List.of("automation", "automate", "automated", "automating",
                        "workflow", "workflows", "orchestration", "no-code", "rpa")),
                new Interest("Fintech", 15, List.of("fintech", "payments", "banking", "lending", "treasury",
                        "payroll", "underwriting", "equities", "capital markets")),
                new Interest("Investing infrastructure", 15, List.of("investing", "brokerage", "custody",
                        "portfolio", "asset management", "clearing", "settlement")),
                new Interest("Trading infrastructure", 16, List.of("trading", "trader", "traders", "exchange",
                        "exchanges", "market making", "market-making", "order book", "derivatives",
                        "perpetuals", "execution", "liquidity", "brokerage")),
                new Interest("Developer tools", 15, List.of("developer", "developers", "devtools", "sdk", "api",
                        "apis", "ci/cd", "observability", "debugging", "tooling")),
                new Interest("Cybersecurity", 13, List.of("security", "cybersecurity", "threat", "zero trust",
                        "identity", "authentication", "vulnerability")),
                new Interest("Enterprise software", 12, List.of("enterprise", "b2b", "erp", "crm", "compliance",
                        "back office", "procurement")),
                new Interest("Robotics", 12, List.of("robot", "robots", "robotics", "autonomy", "autonomous",
                        "humanoid", "drone", "drones", "actuator")),
                new Interest("Emerging technology", 10, List.of("quantum", "biotech", "space", "semiconductor",
                        "neurotech", "novel", "breakthrough")),
                new Interest("Unusual business models", 11, List.of("marketplace", "usage-based", "revenue share",
                        "vertically integrated", "consumption pricing", "new business model"))));
    }
}
