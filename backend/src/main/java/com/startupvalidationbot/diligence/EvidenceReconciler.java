package com.startupvalidationbot.diligence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class EvidenceReconciler {
    private static final Map<String, String> TAXONOMY = Map.ofEntries(
            Map.entry("sales", "REVENUE"), Map.entry("revenue", "REVENUE"),
            Map.entry("gmv", "GMV"), Map.entry("gross merchandise value", "GMV"),
            Map.entry("bookings", "BOOKINGS"), Map.entry("users", "USERS"),
            Map.entry("paying customers", "PAYING_CUSTOMERS"), Map.entry("customers", "CUSTOMERS"),
            Map.entry("loi", "LOI"), Map.entry("pilot", "PILOT"),
            Map.entry("amount raised", "AMOUNT_RAISED"), Map.entry("valuation", "VALUATION"),
            Map.entry("valuation cap", "VALUATION_CAP"));

    public String metric(String value) {
        if (value == null) return "UNKNOWN";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return TAXONOMY.getOrDefault(normalized, normalized.toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    public boolean conflicts(String leftMetric, String leftPeriod, BigDecimal left,
            String rightMetric, String rightPeriod, BigDecimal right) {
        return metric(leftMetric).equals(metric(rightMetric)) && same(leftPeriod, rightPeriod)
                && left != null && right != null && left.compareTo(right) != 0;
    }

    public List<String> reconcile(Map<String, String> secFacts, Map<String, String> platformFacts) {
        List<String> discrepancies = new ArrayList<>();
        compareMoney(discrepancies, "minimum investment", secFacts.get("MINIMUMINVESTMENT"), platformFacts.get("minimumInvestment"));
        compareMoney(discrepancies, "target amount", secFacts.get("OFFERINGAMOUNT"), platformFacts.get("targetAmount"));
        compareMoney(discrepancies, "maximum amount", secFacts.get("MAXIMUMOFFERINGAMOUNT"), platformFacts.get("maximumAmount"));
        String secSecurity = value(secFacts, "SECURITYOFFEREDTYPE", "securityType");
        String campaignSecurity = platformFacts.get("securityType");
        if (secSecurity != null && campaignSecurity != null
                && !secSecurity.equalsIgnoreCase(campaignSecurity)) {
            discrepancies.add("Security type differs between the SEC filing and campaign page.");
        }
        return List.copyOf(discrepancies);
    }

    private static void compareMoney(List<String> output, String name, String left, String right) {
        BigDecimal a = number(left); BigDecimal b = number(right);
        if (a != null && b != null && a.compareTo(b) != 0) output.add("The " + name + " differs between SEC and platform evidence.");
    }
    private static boolean same(String left, String right) { return left == null ? right == null : left.equalsIgnoreCase(right); }
    private static String value(Map<String,String> map, String... keys) { for (String key : keys) if (map.get(key) != null) return map.get(key); return null; }
    private static BigDecimal number(String value) { try { return value == null ? null : new BigDecimal(value.replace("$","").replace(",","").trim()); } catch (Exception error) { return null; } }
}
