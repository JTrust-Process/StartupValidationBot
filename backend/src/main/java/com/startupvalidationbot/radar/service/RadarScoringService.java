package com.startupvalidationbot.radar.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore.AnalysisPayload;
import com.startupvalidationbot.radar.intel.PersonalRelevance;
import com.startupvalidationbot.radar.intel.PersonalRelevanceInputs;

@Service
public class RadarScoringService {
    private final List<String> preferredThemes;
    private final PersonalRelevanceInputs relevanceInputs;

    // Two constructors exist, so the injection target must be explicit.
    @Autowired
    public RadarScoringService(@Value("${RADAR_PREFERRED_THEMES:AI infrastructure,developer tools,energy,fintech,automation}")
            String preferredThemes, PersonalRelevanceInputs relevanceInputs) {
        this.preferredThemes = List.of(preferredThemes.split(",")).stream()
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        this.relevanceInputs = relevanceInputs;
    }

    /** Scoring without persistence: default interest profile and no interaction history. */
    public RadarScoringService(String preferredThemes) {
        this(preferredThemes, PersonalRelevanceInputs.defaults());
    }

    public AnalysisPayload score(Company company) {
        String corpus = String.join(" ", company.name(), value(company.description()), value(company.sector()),
                String.join(" ", company.categories())).toLowerCase(Locale.ROOT);
        int completeness = points(company.description(), 12) + points(company.websiteUrl(), 8)
                + points(company.sector(), 6) + Math.min(10, company.categories().size() * 2);
        int momentum = clamp(35 + company.sourceCount() * 12 + keywordPoints(corpus,
                Map.of("launch", 8, "growth", 8, "funding", 10, "customers", 8, "revenue", 10)));
        int innovation = clamp(35 + keywordPoints(corpus,
                Map.of("new", 5, "novel", 12, "platform", 5, "infrastructure", 8, "automation", 8, "ai", 7)));
        int traction = clamp(25 + keywordPoints(corpus,
                Map.of("customer", 12, "revenue", 15, "users", 10, "growth", 10, "contract", 10)));
        int founderStrength = clamp(30 + keywordPoints(corpus,
                Map.of("founder", 8, "founded", 5, "former", 8, "team", 5)));
        int investorSignal = clamp(25 + company.sourceCount() * 12 + keywordPoints(corpus,
                Map.of("backed", 12, "funding", 10, "seed", 8, "series", 8, "accelerator", 7)));
        int marketPotential = clamp(40 + keywordPoints(corpus,
                Map.of("infrastructure", 10, "enterprise", 8, "health", 8, "energy", 8, "security", 8)));
        int differentiation = clamp(30 + completeness + keywordPoints(corpus,
                Map.of("proprietary", 12, "patent", 10, "technical", 8, "open source", 7)));
        int timing = clamp(40 + keywordPoints(corpus,
                Map.of("ai", 8, "climate", 8, "energy", 7, "automation", 7, "regulation", 5)));

        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("momentum", momentum);
        dimensions.put("innovation", innovation);
        dimensions.put("traction", traction);
        dimensions.put("founderStrength", founderStrength);
        dimensions.put("investorSignal", investorSignal);
        dimensions.put("marketPotential", marketPotential);
        dimensions.put("technicalDifferentiation", differentiation);
        dimensions.put("timing", timing);
        int radarScore = clamp((int) Math.round(dimensions.values().stream().mapToInt(Integer::intValue).average()
                .orElse(0)));

        // Whole-phrase matching only: "erp" must not match "perpetuals", nor "ai" match "retail".
        List<String> themeMatches = preferredThemes.stream()
                .filter(theme -> containsWholePhrase(corpus, theme))
                .toList();

        // Personal relevance comes from the user's configurable interest profile plus the interaction
        // signals they have generated. It is deliberately separate from the Radar Score above, which
        // measures general importance, and from any investment view, which stays in Deal Scout.
        PersonalRelevance.Result relevance = PersonalRelevance.score(company.name(), company.description(),
                company.sector(), company.categories(), relevanceInputs.loadProfile(),
                relevanceInputs.signalSummary(company.id() == null ? 0L : company.id()));
        int personalScore = relevance.score();

        List<String> facts = new ArrayList<>();
        facts.add("Discovered from " + company.sourceCount() + " configured source(s).");
        if (!"Unknown".equalsIgnoreCase(company.sector())) {
            facts.add("Source-classified sector: " + company.sector() + ".");
        }
        if (company.websiteUrl() != null) {
            facts.add("A company website was captured from the source.");
        }

        List<String> inferences = new ArrayList<>();
        inferences.add("Momentum is estimated from source frequency and source-reported language, not verified growth.");
        if (company.sourceCount() < 2) {
            inferences.add("A second independent source would materially improve confidence.");
        }
        List<String> whyInteresting = new ArrayList<>();
        whyInteresting.add("Radar score reflects startup importance signals, not investment quality.");
        if (!themeMatches.isEmpty()) {
            whyInteresting.add("Matches your themes: " + String.join(", ", themeMatches) + ".");
        }
        List<String> risks = company.sourceCount() < 2
                ? List.of("Only one discovery source is currently captured.", "Traction claims have not been verified.")
                : List.of("Traction claims still require primary-source verification.");
        List<String> questions = List.of(
                "What measurable customer or usage traction can be independently verified?",
                "Who are the founders and why is this team unusually suited to the problem?",
                "What changed recently that makes this company worth tracking now?");
        String summary = company.description() == null || company.description().isBlank()
                ? company.name() + " is a newly discovered startup with limited source detail."
                : company.description();
        String whyCare = relevance.matchedInterests().isEmpty()
                ? "No configured interest matched. Track it only if the market or product becomes relevant."
                : "It overlaps with your interests: " + String.join(", ", relevance.matchedInterests()) + ".";
        return new AnalysisPayload(summary, company.sector(), "Unknown from current sources", "Unknown from current sources",
                "Unknown from current sources", "Unknown", List.of(), "Unknown from current sources", List.of(),
                company.categories(), List.of("New independent source", "Verified traction update", "Funding event"),
                facts, inferences, whyInteresting,
                List.of("Seen across " + company.sourceCount() + " source(s)."), List.of(), List.of(), List.of(),
                risks, List.of(), List.of(), questions,
                "Radar relevance is based on public startup signals, not investment suitability.", whyCare,
                "Unknown from current sources", relevance.matchedInterests().isEmpty()
                        ? "No clear career overlap is supported by current source data."
                        : "Potential career overlap with: " + String.join(", ", relevance.matchedInterests()) + ".",
                List.of(), company.sourceCount() >= 2 ? "MEDIUM" : "LOW", whyInteresting,
                relevance.reasons(),
                dimensions, radarScore, personalScore);
    }

    private static int points(String value, int points) {
        return value == null || value.isBlank() || "Unknown".equalsIgnoreCase(value) ? 0 : points;
    }

    private static int keywordPoints(String corpus, Map<String, Integer> keywords) {
        return keywords.entrySet().stream().filter(entry -> corpus.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue).sum();
    }

    private static boolean containsWholePhrase(String corpus, String phrase) {
        String normalized = phrase == null ? "" : phrase.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return false;
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalized)
                + "(?![\\p{L}\\p{N}])");
        return pattern.matcher(corpus).find();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
