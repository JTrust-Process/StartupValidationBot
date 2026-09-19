package com.startupvalidationbot.offering;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OfferingTermNormalizer {
    private static final Pattern MONEY = Pattern.compile(
            "(?i)^\\s*\\$?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)"
                    + "\\s*(K|M|B|thousand|million|billion)?\\s*$");
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MM-dd-uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M-d-uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US).withResolverStyle(ResolverStyle.STRICT));

    private OfferingTermNormalizer() { }

    public static BigDecimal money(String value) {
        if (value == null) return null;
        Matcher matcher = MONEY.matcher(value.trim());
        if (!matcher.matches()) return null;
        BigDecimal multiplier = switch (lower(matcher.group(2))) {
            case "k", "thousand" -> new BigDecimal("1000");
            case "m", "million" -> new BigDecimal("1000000");
            case "b", "billion" -> new BigDecimal("1000000000");
            default -> BigDecimal.ONE;
        };
        try {
            return new BigDecimal(matcher.group(1).replace(",", "")).multiply(multiplier);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    public static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        String candidate = value.trim();
        for (DateTimeFormatter formatter : DATES) {
            try {
                return LocalDate.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next explicitly supported source format.
            }
        }
        try {
            return LocalDate.parse(candidate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public static String security(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.replaceAll("\\s+", " ").trim();
        if (value.matches("(?i).*\\b(?:senior|subordinate|junior)\\s+to\\s+(?:a\\s+)?safe\\b.*")) return null;
        if (matches(value, "\\b(?:crowd\\s+)?safe\\b")) return "SAFE";
        if (matches(value, "\\bcommon\\s+stock\\b")) return "Common Stock";
        if (matches(value, "\\bpreferred\\s+(?:stock|equity)\\b")) return "Preferred Stock";
        if (matches(value, "\\bconvertible\\s+(?:promissory\\s+)?note\\b")) return "Convertible Note";
        if (matches(value, "\\brevenue\\s+share\\b")) return "Revenue Share";
        if (value.equalsIgnoreCase("token")
                || matches(value, "\\b(?:digital\\s+security|security\\s+token|token(?:ized)?\\s+security|token\\s+standard)\\b")) return "Token";
        if (matches(value, "\\b(?:debt|promissory\\s+note)\\b")) return "Debt";
        if (value.equalsIgnoreCase("note")) return "Debt";
        if (matches(value, "\\bequity\\b")) return "Equity";
        return null;
    }

    private static boolean matches(String value, String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(value).find();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
