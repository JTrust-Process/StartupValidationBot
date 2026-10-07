package com.startupvalidationbot.offering;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Label-bound extraction only. Raw conditional values remain evidence, not canonical guesses. */
public final class SecOfferingTermExtractor {
    private static final String MONEY = "(\\$?\\s*\\d+(?:,\\d{3})*(?:\\.\\d+)?\\s*(?:million|billion|thousand|[KMB]\\b)?)";
    private static final String MINIMUM = "minimum\\s+(?:individual\\s+)?(?:investment|subscription|purchase)(?:\\s+amount)?(?:\\s+in\\s+this\\s+offering)?";
    private static final Pattern MIN = pattern(MINIMUM + "\\s*(?::|=|is|of|shall be|will be)?\\s*" + MONEY);
    private static final Pattern AMENDED_MIN = pattern(MINIMUM + "[^.;]{0,90}?(?:reduced|increased|change(?:d)?|set)[^.;]{0,30}?(?:from\\s+" + MONEY + "\\s+)?to\\s+" + MONEY);
    private static final Pattern AMENDED_MIN_BEFORE = pattern("(?:reduce|increase|change|set)[^.;]{0,30}?" + MINIMUM + "\\s*(?:from\\s+" + MONEY + "\\s+)?to\\s+" + MONEY);
    private static final Pattern VALUATION = pattern("((?:(?:pre|post)[- ]money\\s+)?(?:company\\s+)?valuation(?:\\s+cap)?)\\s*(?::|=|is|of|shall be|will be)?\\s*" + MONEY);
    private static final Pattern TRAILING_CAP = pattern(MONEY + "\\s+(?:(pre|post)[- ]money\\s+)?(?:valuation\\s+)?cap\\b(?!\\s*(?::|=|is|of)?\\s*\\$?\\d)");
    private static final Pattern RAISED = pattern("(?:total\\s+amount\\s+of\\s+securities\\s+sold|(?:total|final)\\s+amount\\s+raised|campaign\\s+raised|(?:final\\s+number\\s+of\\s+)?raised|final\\s+number)\\s*(?::|=|of|was|is|approximately)?\\s*" + MONEY);
    private static final Pattern COMMITMENTS = pattern("investment\\s+commitments(?:\\s+of)?\\s*(?::|=)?\\s*" + MONEY);
    private static final Pattern CONDITIONAL = pattern("early[- ]bird|first\\s+\\$|different\\s+(?:classes|tranches)|depending\\s+on|tiered|earlier investors|later investors");
    private static final Pattern PROVISIONAL = pattern("subject to final accounting|final accounting[^.]{0,40}(?:not|pending)|estimated|provisional");
    private static final Pattern COMPLETED = pattern("(?:offering|campaign)\\s+(?:has\\s+|was\\s+|is\\s+)?(?:successfully\\s+)?(?:closed|completed|ended)|closed successfully|final\\s+(?:amount|number|escrow closing)");
    public record Fact(String field, String value, String type, String excerpt) { }

    private SecOfferingTermExtractor() { }

    public static void extract(Map<String, String> facts, String htmlOrText) {
        facts.put("_secTerm.parsed", "true");
        Map<String, List<Fact>> found = new LinkedHashMap<>();
        structured(found, facts, "minimumInvestment", "MINIMUM_INVESTMENT", "MINIMUMINVESTMENT", "MINIMUMSUBSCRIPTION", "MINIMUMPURCHASEAMOUNT");
        structured(found, facts, "valuationOrCap", "VALUATION_CAP", "VALUATIONCAP");
        structured(found, facts, "valuationOrCap", "POST_MONEY_VALUATION_CAP", "POSTMONEYVALUATIONCAP");
        structured(found, facts, "valuationOrCap", "PRE_MONEY_VALUATION_CAP", "PREMONEYVALUATIONCAP");
        structured(found, facts, "valuationOrCap", "PRE_MONEY_VALUATION", "PREMONEYVALUATION");
        structured(found, facts, "valuationOrCap", "POST_MONEY_VALUATION", "POSTMONEYVALUATION");
        structured(found, facts, "valuationOrCap", "COMPANY_VALUATION", "VALUATION", "COMPANYVALUATION");
        structured(found, facts, "pricePerShare", "PRICE_PER_SHARE", "PRICEPERSHARE");
        structured(found, facts, "discountRate", "DISCOUNT_RATE", "DISCOUNTRATE", "DISCOUNTPERCENT");
        structured(found, facts, "interestRate", "INTEREST_RATE", "INTERESTRATE");
        structured(found, facts, "investorCount", "INVESTOR_COUNT", "INVESTORCOUNT", "NUMBEROFINVESTORS");
        for (String key : List.of("CONVERSIONTERMS", "MATURITYDATE", "FINALCLOSINGDATE")) {
            String raw = IntermediaryRegistry.value(facts, key);
            if (raw != null) add(found, key.equals("CONVERSIONTERMS") ? "conversionTerms" : key.equals("MATURITYDATE") ? "maturity" : "closingDate", raw, key, key + ": " + raw);
        }
        String text = plain(htmlOrText == null ? "" : htmlOrText);
        List<String> narratives = new ArrayList<>();
        for (String key : List.of("PRICEDETERMINATIONMETHOD", "NATUREOFAMENDMENT", "PROGRESSUPDATE")) {
            String value = IntermediaryRegistry.value(facts, key);
            if (value != null) narratives.add(value);
        }
        if (!text.isBlank()) narratives.add(text);
        for (String narrative : narratives) {
            boolean amended = false;
            Matcher amendment = AMENDED_MIN.matcher(narrative);
            while (amendment.find()) {
                String value = amendment.group(2);
                add(found, "minimumInvestment", value, "AMENDED_MINIMUM_INVESTMENT", snippet(narrative, amendment.start(), amendment.end()));
                amended = true;
            }
            amendment = AMENDED_MIN_BEFORE.matcher(narrative);
            while (amendment.find()) { add(found, "minimumInvestment", amendment.group(2), "AMENDED_MINIMUM_INVESTMENT", snippet(narrative, amendment.start(), amendment.end())); amended = true; }
            if (!amended) scan(found, narrative, MIN, "minimumInvestment", "MINIMUM_INVESTMENT", 1);
            Matcher valuations = VALUATION.matcher(narrative);
            while (valuations.find()) {
                String label = valuations.group(1).toLowerCase(Locale.ROOT);
                String type = label.contains("cap") ? (label.contains("post") ? "POST_MONEY_VALUATION_CAP" : label.contains("pre") ? "PRE_MONEY_VALUATION_CAP" : "VALUATION_CAP")
                        : label.contains("pre") ? "PRE_MONEY_VALUATION" : label.contains("post") ? "POST_MONEY_VALUATION" : "COMPANY_VALUATION";
                add(found, "valuationOrCap", valuations.group(2), type, snippet(narrative, valuations.start(), valuations.end()));
            }
            Matcher caps = TRAILING_CAP.matcher(narrative);
            while (caps.find()) add(found, "valuationOrCap", caps.group(1), caps.group(2) == null ? "VALUATION_CAP" : caps.group(2).toUpperCase(Locale.ROOT) + "_MONEY_VALUATION_CAP", snippet(narrative, caps.start(), caps.end()));
            scan(found, narrative, pattern("price\\s+per\\s+share\\s*(?::|=|of|is)?\\s*" + MONEY), "pricePerShare", "PRICE_PER_SHARE", 1);
            scan(found, narrative, pattern("discount(?:\\s+rate)?\\s*(?::|=|of|is)?\\s*(\\d+(?:\\.\\d+)?)\\s*%"), "discountRate", "DISCOUNT_RATE", 1);
            scan(found, narrative, pattern("interest\\s+rate\\s*(?::|=|of|is)?\\s*(\\d+(?:\\.\\d+)?)\\s*%"), "interestRate", "INTEREST_RATE", 1);
            scan(found, narrative, pattern("maturity(?:\\s+date)?\\s*(?::|=|is)?\\s*(\\d{4}-\\d{2}-\\d{2}|[A-Za-z]+ \\d{1,2}, \\d{4}|\\d+ months|\\d+ years)"), "maturity", "MATURITY", 1);
            scan(found, narrative, COMMITMENTS, "investmentCommitments", "INVESTMENT_COMMITMENTS", 1);
            Matcher raised = RAISED.matcher(narrative);
            while (raised.find()) {
                String context = snippet(narrative, raised.start(), raised.end());
                boolean finality = COMPLETED.matcher(context).find() && !PROVISIONAL.matcher(context).find();
                String type = finality ? "FINAL_SECURITIES_SOLD" : "INTERIM_RAISED";
                if (context.matches("(?is).*(including|includes).{0,60}fees.*")) type += "_INCLUDING_FEES";
                add(found, "amountRaised", raised.group(1), type, context);
            }
            Matcher investors = pattern("(?:investor count\\s*:?\\s*|(?:from|by)\\s+)([0-9,]+)\\s*(?:investors)?").matcher(narrative);
            while (investors.find()) if (investors.group().toLowerCase(Locale.ROOT).contains("investor")) add(found, "investorCount", investors.group(1), "INVESTOR_COUNT", investors.group());
            if (pattern("(?:offering|campaign)[^.]{0,25}(?:closed|completed|ended)|closed successfully|final escrow closing").matcher(narrative).find()) {
                facts.put("_secTerm.completed", "true");
            }
            scan(found, narrative, pattern("(?:final closing date|(?:offering|campaign) (?:closed|ended)(?: on)?)\\s*:?\\s*(\\d{4}-\\d{2}-\\d{2}|[A-Za-z]+ \\d{1,2}, \\d{4})"), "closingDate", "FINAL_CLOSING_DATE", 1);
        }
        String subtype = IntermediaryRegistry.value(facts, "SECURITYOFFEREDOTHERDESC");
        String rawSecurity = IntermediaryRegistry.value(facts, "SECURITYOFFEREDTYPE", "securityType");
        if (rawSecurity != null) facts.put("_secTerm.rawSecurity", rawSecurity);
        if (subtype != null) facts.put("_secTerm.rawSecuritySubtype", subtype);
        String securityText = subtype != null ? subtype : rawSecurity;
        if (securityText != null) {
            String normalized = securityText.replaceAll("(?i)simple agreement for future equity|simple agreements for future equity|SAFEs", "SAFE");
            boolean safe = pattern("\\bSAFE\\b").matcher(normalized).find();
            boolean note = pattern("convertible (?:promissory )?note").matcher(normalized).find();
            if (safe && note || pattern("common stock.*(?:and|/)\\s*preferred stock|preferred stock.*(?:and|/)\\s*common stock").matcher(normalized).find()) {
                facts.put("_secTerm.ambiguity.securityType", "Multiple filed security instruments: " + securityText);
                facts.remove("securityType");
            } else {
                String security = OfferingTermNormalizer.security(normalized);
                if (security != null) {
                    facts.put("securityType", security);
                    facts.put("_secTerm.type.securityType", subtype == null ? "FILED_SECURITY_CATEGORY" : "FILED_SECURITY_SUBTYPE");
                    facts.put("_secTerm.excerpt.securityType", securityText);
                }
            }
        }
        String termsText = String.join("\n", narratives);
        if (pattern("\\bSAFE\\b[^.]{0,100}\\bcap\\b[^.]{0,150}convertible note|convertible note[^.]{0,100}\\bcap\\b[^.]{0,150}\\bSAFE\\b").matcher(termsText).find()) {
            facts.put("_secTerm.ambiguity.securityType", "Multiple securities have separate filed pricing terms");
            facts.remove("securityType");
        }
        for (var entry : found.entrySet()) {
            String field = entry.getKey();
            List<Fact> values = entry.getValue();
            for (int i = 0; i < values.size(); i++) {
                Fact fact = values.get(i);
                String key = "_secTerm.fact." + field + "." + i;
                facts.put(key + ".value", fact.value()); facts.put(key + ".type", fact.type()); facts.put(key + ".excerpt", fact.excerpt());
            }
            if (List.of("investmentCommitments", "maturity", "closingDate", "conversionTerms", "discountRate", "interestRate", "pricePerShare", "investorCount").contains(field)) continue;
            List<Fact> canonical = field.equals("amountRaised") ? values.stream().filter(v -> v.type().equals("FINAL_SECURITIES_SOLD")).toList() : values;
            if (field.equals("minimumInvestment") && values.stream().anyMatch(v -> v.type().equals("AMENDED_MINIMUM_INVESTMENT"))) canonical = values.stream().filter(v -> v.type().equals("AMENDED_MINIMUM_INVESTMENT")).toList();
            long variants = canonical.stream().map(v -> v.value() + (field.equals("valuationOrCap") ? "|" + v.type() : "")).distinct().count();
            boolean conditional = field.equals("valuationOrCap") && CONDITIONAL.matcher(termsText).find() && variants > 0;
            if (variants > 1 || conditional) {
                facts.put("_secTerm.ambiguity." + field, "Multiple or conditional filed terms require review");
                facts.remove(field);
            } else if (variants == 1) {
                facts.remove("_secTerm.ambiguity." + field);
                Fact chosen = canonical.getFirst();
                facts.put(field, chosen.value() + (field.equals("valuationOrCap") ? chosen.type().contains("CAP") ? " cap" : " valuation" : ""));
                facts.put("_secTerm.type." + field, chosen.type());
                facts.put("_secTerm.excerpt." + field, chosen.excerpt());
            }
        }
    }

    public static String plain(String value) {
        String normalized = value.replaceAll("(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)>", " ")
                .replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(120_000, normalized.length()));
    }
    private static void structured(Map<String, List<Fact>> found, Map<String, String> facts, String field, String type, String... keys) {
        for (String key : keys) { String value = IntermediaryRegistry.value(facts, key); if (value != null) add(found, field, value, type, key + ": " + value); }
    }
    private static void scan(Map<String, List<Fact>> found, String text, Pattern pattern, String field, String type, int group) {
        Matcher match = pattern.matcher(text);
        while (match.find()) add(found, field, match.group(group), type, snippet(text, match.start(), match.end()));
    }
    private static void add(Map<String, List<Fact>> found, String field, String raw, String type, String excerpt) {
        String value;
        if (List.of("maturity", "closingDate", "conversionTerms").contains(field)) value = raw.trim();
        else { BigDecimal money = OfferingTermNormalizer.money(type.endsWith("RATE") ? raw.replace("%", "") : raw); if (money == null || money.signum() <= 0 || type.endsWith("RATE") && money.compareTo(new BigDecimal("100")) > 0) return; value = money.stripTrailingZeros().toPlainString(); }
        Fact fact = new Fact(field, value, type, excerpt);
        List<Fact> items = found.computeIfAbsent(field, ignored -> new ArrayList<>());
        if (!items.contains(fact) && items.size() < 20) items.add(fact);
    }
    private static String snippet(String text, int start, int end) { return text.substring(Math.max(0, start - 100), Math.min(text.length(), end + 180)).trim(); }
    private static Pattern pattern(String value) { return Pattern.compile(value, Pattern.CASE_INSENSITIVE); }
}
