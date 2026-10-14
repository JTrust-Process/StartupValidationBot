package com.startupvalidationbot.diligence.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import com.startupvalidationbot.diligence.DiligenceDomain.CampaignStatus;

/** Only campaign-body statements qualify; site-wide calls to action are not offering evidence. */
public final class PublicCampaignStatus {
    private PublicCampaignStatus() { }
    public record Evidence(CampaignStatus status, String excerpt, String explanation) { }
    private static final Map<CampaignStatus, Pattern> SIGNALS = signals();
    private static Map<CampaignStatus, Pattern> signals() {
        Map<CampaignStatus, Pattern> values = new LinkedHashMap<>();
        values.put(CampaignStatus.WITHDRAWN, pattern("offering (?:has been |is )?withdrawn"));
        values.put(CampaignStatus.TERMINATED, pattern("offering (?:has been |is )?terminated"));
        values.put(CampaignStatus.FUNDED, pattern("successfully funded|funded and closed"));
        values.put(CampaignStatus.CLOSED, pattern("campaign (?:has )?ended|offering (?:is |has )?closed|closed offering"));
        values.put(CampaignStatus.RESERVATION, pattern("accepting reservations|reserve your investment|reservations only|reservation[- ]only"));
        values.put(CampaignStatus.COMING_SOON, pattern("coming soon|offering (?:opens|launches) (?:soon|on)"));
        values.put(CampaignStatus.CLOSING_SOON, pattern("offering (?:is )?closing soon|[0-9]+ days? left to invest"));
        values.put(CampaignStatus.ACTIVE, pattern("open for investment|(?:currently|now) accepting investments|offering is (?:now )?open|invest now"));
        return java.util.Collections.unmodifiableMap(values);
    }
    private static Pattern pattern(String value) { return Pattern.compile("(?i)\\b(?:" + value + ")\\b"); }

    public static String body(String html) {
        return (html == null ? "" : html)
                .replaceAll("(?is)<(script|style|nav|header|footer|aside)\\b[^>]*>.*?</\\1>", " ");
    }
    public static String text(String html) {
        return body(html).replaceAll("(?is)</(?:p|div|section|h[1-6]|li|button)>", ". ")
                .replaceAll("(?s)<[^>]+>", " ").replace("&amp;", "&").replace("&nbsp;", " ")
                .replace("&quot;", "\"").replace("&#39;", "'").replaceAll("\\s+", " ").trim();
    }
    public static String wall(String html) {
        String value = html == null ? "" : html;
        if (Pattern.compile("(?is)(cf-chl-|challenge-platform|verify (?:that )?you are human|checking your browser|<title[^>]*>\\s*(?:just a moment|attention required))").matcher(value).find()) return "BOT_CHALLENGE";
        if (Pattern.compile("(?is)<(?:title|h1)[^>]*>\\s*(?:sign in|log in|login)(?:\\s|<)|<input[^>]+type=[\"']password[\"']").matcher(body(value)).find()) return "LOGIN_WALL";
        return null;
    }
    public static boolean campaignBody(String html) {
        return Pattern.compile("(?i)\\b(offering|campaign|minimum investment|security type|funding goal|regulation crowdfunding|reg cf|reserve your investment|accepting reservations|coming soon)\\b")
                .matcher(text(html)).find();
    }
    public static Evidence inspect(String html) {
        String value = text(html);
        Map<CampaignStatus, String> found = new LinkedHashMap<>();
        for (var signal : SIGNALS.entrySet()) {
            var matcher = signal.getValue().matcher(value);
            while (matcher.find()) {
                int boundary = Math.max(value.lastIndexOf('.', matcher.start()), Math.max(value.lastIndexOf(';', matcher.start()), value.lastIndexOf('!', matcher.start())));
                String prefix = value.substring(Math.max(boundary + 1, matcher.start() - 80), matcher.start());
                if (prefix.matches("(?is).*\\b(?:not(?: yet| currently)?|no longer|never|cannot|can't|do not|don't|will(?: be)?|would|could|might|may|plans? to|expects? to|if|when|previous|previously|last campaign)\\b[^.?!;]*")) continue;
                int end = value.indexOf('.', matcher.end());
                String excerpt = value.substring(Math.max(0, boundary + 1), Math.min(value.length(), end < 0 ? matcher.end() + 160 : end + 1)).trim();
                found.putIfAbsent(signal.getKey(), excerpt.substring(0, Math.min(600, excerpt.length())));
                break;
            }
        }
        if (found.containsKey(CampaignStatus.FUNDED)) found.remove(CampaignStatus.CLOSED);
        if (found.containsKey(CampaignStatus.RESERVATION)) found.remove(CampaignStatus.COMING_SOON);
        if (found.containsKey(CampaignStatus.CLOSING_SOON)) found.remove(CampaignStatus.ACTIVE);
        if (found.size() != 1) return new Evidence(CampaignStatus.UNKNOWN,
                found.isEmpty() ? "" : String.join(" | ", found.values()),
                found.isEmpty() ? "No unambiguous present public status statement." : "Conflicting public status statements require review.");
        var item = found.entrySet().iterator().next();
        return new Evidence(item.getKey(), item.getValue(), "Explicit campaign-body statement; personal eligibility and transaction access are not established.");
    }
}
