package com.startupvalidationbot.diligence.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Applicable groups and longest matching paths, including bounded wildcard matching (RFC 9309). */
public final class RobotsRules {
    private RobotsRules() { }
    private record Rule(String path, boolean allow) { }
    private record Group(List<String> agents, List<Rule> rules) { }
    public static boolean disallowed(String text, String path) {
        List<Group> groups = new ArrayList<>();
        Group current = new Group(new ArrayList<>(), new ArrayList<>());
        groups.add(current);
        for (String line : text.split("\\R")) {
            String value = line.replaceFirst("#.*$", "").trim();
            int colon = value.indexOf(':');
            if (colon < 0) continue;
            String name = value.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String content = value.substring(colon + 1).trim();
            if (name.equals("user-agent")) {
                if (!current.rules().isEmpty()) { current = new Group(new ArrayList<>(), new ArrayList<>()); groups.add(current); }
                current.agents().add(content.toLowerCase(Locale.ROOT));
            } else if ((name.equals("allow") || name.equals("disallow")) && !current.agents().isEmpty() && !content.isBlank()) {
                current.rules().add(new Rule(normalize(content), name.equals("allow")));
            }
        }
        boolean named = groups.stream().anyMatch(g -> g.agents().contains("startupintelligence"));
        String token = named ? "startupintelligence" : "*";
        String target = normalize(path == null || path.isEmpty() ? "/" : path);
        int best = -1;
        boolean allow = true;
        for (Group group : groups) if (group.agents().contains(token)) for (Rule rule : group.rules()) {
            String pattern = rule.path();
            boolean exact = pattern.endsWith("$");
            if (exact) pattern = pattern.substring(0, pattern.length() - 1);
            int specificity = pattern.replace("*", "").length();
            if (matches(pattern, target, exact) && (specificity > best || specificity == best && rule.allow())) {
                best = specificity;
                allow = rule.allow();
            }
        }
        return !allow;
    }
    private static boolean matches(String pattern, String value, boolean exact) {
        int p = 0, v = 0, star = -1, restart = 0;
        while (v < value.length()) {
            if (p == pattern.length()) return !exact;
            if (pattern.charAt(p) == '*') { star = p++; restart = v; }
            else if (pattern.charAt(p) == value.charAt(v)) { p++; v++; }
            else if (star >= 0) { p = star + 1; v = ++restart; }
            else return false;
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') p++;
        return p == pattern.length();
    }
    private static String normalize(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '%' && i + 2 < value.length()) {
                try {
                    char c = (char) Integer.parseInt(value.substring(i + 1, i + 3), 16);
                    if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || "-._~".indexOf(c) >= 0) out.append(c);
                    else out.append('%').append(value.substring(i + 1, i + 3).toUpperCase(Locale.ROOT));
                    i += 2; continue;
                } catch (NumberFormatException ignored) { }
            }
            out.append(value.charAt(i));
        }
        return out.toString();
    }
}
