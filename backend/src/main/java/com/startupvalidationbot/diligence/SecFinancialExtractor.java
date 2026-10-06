package com.startupvalidationbot.diligence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import com.startupvalidationbot.diligence.DiligenceDomain.EvidenceClassification;
import com.startupvalidationbot.diligence.DiligenceDomain.FinancialPeriod;
import com.startupvalidationbot.diligence.DiligenceStore.EvidenceDraft;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingTermNormalizer;

@Component
public class SecFinancialExtractor {
    private static final Map<String, List<String>> CONCEPTS = Map.ofEntries(
            Map.entry("revenue", List.of("REVENUE", "REVENUES")),
            Map.entry("cost_of_goods", List.of("COSTGOODSSOLD", "COSTOFGOODSSOLD", "COSTOFREVENUE", "COSTOFREVENUES")),
            Map.entry("gross_profit", List.of("GROSSPROFIT")),
            Map.entry("net_income", List.of("NETINCOME", "NETINCOMELOSS", "NETLOSS")),
            Map.entry("cash", List.of("CASHEQUI", "CASHANDCASHEQUIVALENTS", "CASH")),
            Map.entry("assets", List.of("TOTALASSET", "TOTALASSETS")),
            Map.entry("current_assets", List.of("CURRENTASSETS", "TOTALCURRENTASSETS")),
            Map.entry("liabilities", List.of("TOTALLIABILITIES", "TOTALLIABILITY")),
            Map.entry("current_liabilities", List.of("CURRENTLIABILITIES", "TOTALCURRENTLIABILITIES")),
            Map.entry("short_term_debt", List.of("SHORTTERMDEBT", "CURRENTDEBT")),
            Map.entry("long_term_debt", List.of("LONGTERMDEBT")),
            Map.entry("equity", List.of("SHAREHOLDERSEQUITY", "STOCKHOLDERSEQUITY", "MEMBERSEQUITY", "TOTALEQUITY")),
            Map.entry("taxes_paid", List.of("TAXPAID", "TAXESPAID")));

    public List<FinancialPeriod> extract(Offering offering, Map<String, String> raw) {
        List<FinancialPeriod> result = new ArrayList<>();
        Map<String, String> facts = insensitive(raw);
        for (String marker : List.of("MOSTRECENT", "PRIOR")) {
            Map<String, FiledValue> values = values(facts, marker);
            if (values.isEmpty()) continue;
            result.add(new FinancialPeriod(period(facts, marker), number(values, "revenue"), number(values, "cost_of_goods"),
                    number(values, "net_income"), number(values, "cash"), number(values, "assets"), number(values, "liabilities"),
                    number(values, "short_term_debt"), number(values, "long_term_debt"), number(values, "taxes_paid"),
                    offering.accessionNumber(), offering.secFilingUrl(), number(values, "gross_profit"),
                    number(values, "current_assets"), number(values, "current_liabilities"), number(values, "equity"),
                    endingDate(facts, marker)));
        }
        return List.copyOf(result);
    }

    public List<EvidenceDraft> evidence(Offering offering, Map<String, String> raw) {
        Map<String, String> facts = insensitive(raw);
        List<EvidenceDraft> result = new ArrayList<>();
        for (String marker : List.of("MOSTRECENT", "PRIOR")) {
            for (var item : values(facts, marker).entrySet()) {
                FiledValue filed = item.getValue();
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("accessionNumber", facts.getOrDefault("_accession." + filed.key(), offering.accessionNumber()));
                metadata.put("filedLabel", facts.getOrDefault("_secLabel." + filed.key(), filed.key()));
                metadata.put("normalizedConcept", item.getKey());
                metadata.put("units", facts.getOrDefault("_secUnits", "USD")); // Structured Form C dollar fields.
                String document = facts.getOrDefault("_sourceUrl." + filed.key(), offering.secFilingUrl());
                metadata.put("filingDocument", document);
                metadata.put("relativePeriod", marker.equals("MOSTRECENT") ? "MOST_RECENT_FISCAL_YEAR" : "PRIOR_FISCAL_YEAR");
                LocalDate date = endingDate(facts, marker);
                if (date != null) metadata.put("periodEndingDate", date.toString());
                String observed = facts.get("_observedAt." + filed.key());
                if (observed != null) metadata.put("observedAt", observed);
                result.add(new EvidenceDraft("SEC_EDGAR", document, "SEC Form " + offering.filingType(),
                        item.getKey(), filed.value().toPlainString(), period(facts, marker),
                        EvidenceClassification.SEC_FILED_FACT, 95, filed.key() + ": " + facts.get(filed.key()), metadata));
            }
        }
        return List.copyOf(result);
    }

    public String extractionStatus(Offering offering, Map<String, String> raw) {
        if (!extract(offering, raw).isEmpty()) return "SUCCESS";
        Map<String, String> facts = insensitive(raw);
        for (String marker : List.of("MOSTRECENT", "PRIOR")) {
            for (List<String> aliases : CONCEPTS.values()) for (String alias : aliases) {
                String value = facts.get(alias + marker + "FISCALYEAR");
                if (value != null && !value.isBlank()) return "PARSE_FAILED";
            }
        }
        return "NO_FACT_PRESENT";
    }

    private static Map<String, FiledValue> values(Map<String, String> facts, String marker) {
        Map<String, FiledValue> result = new TreeMap<>();
        CONCEPTS.forEach((concept, aliases) -> {
            for (String alias : aliases) {
                String key = alias + marker + "FISCALYEAR";
                BigDecimal value = monetaryValue(facts.get(key));
                if (value != null) {
                    if (alias.equals("NETLOSS") && value.signum() > 0) value = value.negate();
                    result.put(concept, new FiledValue(key, value)); break;
                }
            }
        });
        return result;
    }

    public static BigDecimal monetaryValue(String value) {
        if (value == null) return null;
        String text = value.trim();
        boolean negative = text.startsWith("(") && text.endsWith(")");
        if (negative) text = text.substring(1, text.length() - 1).trim();
        if (!text.matches("[+-]?\\$?(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d{1,2})?")) return null;
        try {
            BigDecimal amount = new BigDecimal(text.replace("$", "").replace(",", ""));
            return amount.precision() - amount.scale() > 17 ? null : negative ? amount.negate() : amount;
        } catch (NumberFormatException error) { return null; }
    }

    private static String period(Map<String, String> facts, String marker) {
        String explicit = first(facts, marker + "FISCALYEAR", "FISCALYEAR" + marker);
        return explicit != null && explicit.matches("\\d{4}") ? explicit
                : marker.equals("MOSTRECENT") ? "MOST_RECENT_FISCAL_YEAR" : "PRIOR_FISCAL_YEAR";
    }
    private static LocalDate endingDate(Map<String, String> facts, String marker) {
        return OfferingTermNormalizer.date(first(facts, "FISCALYEAREND" + marker, marker + "FISCALYEAREND",
                "FISCALYEARENDDATE" + marker, marker + "FISCALYEARENDDATE", "PERIODEND" + marker));
    }
    private static String first(Map<String, String> facts, String... keys) {
        for (String key : keys) if (facts.get(key) != null && !facts.get(key).isBlank()) return facts.get(key);
        return null;
    }
    private static Map<String, String> insensitive(Map<String, String> raw) {
        var facts = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER); facts.putAll(raw); return facts;
    }
    private static BigDecimal number(Map<String, FiledValue> values, String concept) {
        return values.containsKey(concept) ? values.get(concept).value() : null;
    }
    private record FiledValue(String key, BigDecimal value) { }
}
