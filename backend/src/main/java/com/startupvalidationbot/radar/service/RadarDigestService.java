package com.startupvalidationbot.radar.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore;

@Service
public class RadarDigestService {
    private static final String DISCLAIMER = "This is a research shortlist, not financial advice. "
            + "Review primary sources, offering documents, and risks before making decisions.";

    private final RadarStore store;
    private final ObjectMapper objectMapper;
    private final String appUrl;
    private final String dealScoutRunUrl;
    private final String dealScoutToken;
    private final String emailSendUrl;
    private final String emailToken;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RadarDigestService(RadarStore store, ObjectMapper objectMapper,
            @Value("${radar.app-url:http://localhost:5173/#/radar}") String appUrl,
            @Value("${radar.deal-scout-run-url:}") String dealScoutRunUrl,
            @Value("${radar.deal-scout-token:}") String dealScoutToken,
            @Value("${radar.email-send-url:}") String emailSendUrl,
            @Value("${radar.email-token:}") String emailToken) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.appUrl = appUrl;
        this.dealScoutRunUrl = dealScoutRunUrl;
        this.dealScoutToken = dealScoutToken;
        this.emailSendUrl = emailSendUrl;
        this.emailToken = emailToken;
    }

    public DigestResult generateAndMaybeSend(boolean send) {
        List<Company> topRadar = store.listCompanies().stream()
                .filter(company -> !company.ignored())
                .sorted(Comparator.comparingInt(Company::radarScore).reversed())
                .limit(5)
                .toList();
        List<Company> watched = store.listWatchedCompanies().stream()
                .sorted(Comparator.comparing(Company::lastSeenAt).reversed())
                .limit(5)
                .toList();
        DealScoutSection dealScout = fetchDealScoutPreview();
        String subject = "Weekly Startup Intelligence - companies and deals to review";
        String text = buildText(topRadar, watched, dealScout);
        String html = buildHtml(topRadar, watched, dealScout);
        String periodKey = weeklyPeriodKey();

        if (!send || emailSendUrl.isBlank()) {
            store.saveDigest(periodKey, subject, text, html, "PREVIEW", null,
                    send && emailSendUrl.isBlank() ? "RADAR_EMAIL_SEND_URL is not configured" : null);
            return new DigestResult(true, false, periodKey, subject, text, html, null,
                    send && emailSendUrl.isBlank() ? "Preview generated; email endpoint is not configured." : null);
        }

        try {
            Map<String, Object> payload = Map.of("subject", subject, "text", text, "html", html);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(emailSendUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (!emailToken.isBlank()) {
                builder.header("Authorization", "Bearer " + emailToken);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode result = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !result.path("ok").asBoolean(false)) {
                String error = result.path("error").asText("email endpoint returned HTTP " + response.statusCode());
                store.saveDigest(periodKey, subject, text, html, "FAILED", null, error);
                return new DigestResult(false, false, periodKey, subject, text, html, null, error);
            }
            String messageId = result.path("id").asText(null);
            store.saveDigest(periodKey, subject, text, html, "SENT", messageId, null);
            return new DigestResult(true, true, periodKey, subject, text, html, messageId, null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            String message = "Digest email request was interrupted";
            store.saveDigest(periodKey, subject, text, html, "FAILED", null, message);
            return new DigestResult(false, false, periodKey, subject, text, html, null, message);
        } catch (Exception error) {
            store.saveDigest(periodKey, subject, text, html, "FAILED", null, error.getMessage());
            return new DigestResult(false, false, periodKey, subject, text, html, null, error.getMessage());
        }
    }

    private DealScoutSection fetchDealScoutPreview() {
        if (dealScoutRunUrl.isBlank()) {
            return new DealScoutSection(List.of(), "Deal Scout preview endpoint is not configured.");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(dealScoutRunUrl))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"send\":false}"));
            if (!dealScoutToken.isBlank()) {
                builder.header("Authorization", "Bearer " + dealScoutToken);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new DealScoutSection(List.of(), "Deal Scout returned HTTP " + response.statusCode() + ".");
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<DealCandidate> candidates = new ArrayList<>();
            for (JsonNode node : root.path("candidates")) {
                candidates.add(new DealCandidate(node.path("companyName").asText("Unknown company"),
                        node.path("platformOrSource").asText("Unknown source"), node.path("score").asInt(0),
                        strings(node.path("whyMatched")), strings(node.path("mainRedFlags")),
                        node.path("sourceUrl").asText("")));
            }
            return new DealScoutSection(candidates.stream().limit(5).toList(), null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new DealScoutSection(List.of(), "Deal Scout preview request was interrupted.");
        } catch (Exception error) {
            return new DealScoutSection(List.of(), "Deal Scout preview unavailable: " + error.getMessage());
        }
    }

    private String buildText(List<Company> radar, List<Company> watched, DealScoutSection dealScout) {
        StringBuilder body = new StringBuilder("STARTUP INTELLIGENCE WEEKLY\n\n").append(DISCLAIMER).append("\n\n")
                .append("NEW STARTUPS TO RESEARCH\n");
        if (radar.isEmpty()) {
            body.append("No Radar companies have been discovered yet.\n");
        }
        for (Company company : radar) {
            body.append("\n").append(company.name()).append(" — Radar ").append(company.radarScore())
                    .append(" / Personal ").append(company.personalScore()).append("\n")
                    .append(value(company.description(), "No source summary yet.")).append("\n")
                    .append(appUrl).append("/company/").append(company.id()).append("\n");
        }
        body.append("\nWATCHLIST UPDATES\n");
        if (watched.isEmpty()) {
            body.append("No companies are on the watchlist.\n");
        }
        for (Company company : watched) {
            body.append("- ").append(company.name()).append(" last seen ").append(company.lastSeenAt()).append("\n");
        }
        body.append("\nPRIVATE-MARKET DEALS TO REVIEW\n");
        for (DealCandidate candidate : dealScout.candidates()) {
            body.append("\n").append(candidate.companyName()).append(" — review priority ")
                    .append(candidate.score()).append("\n")
                    .append(candidate.whyMatched().isEmpty() ? "Review source and eligibility." : candidate.whyMatched().get(0))
                    .append("\n");
        }
        if (dealScout.candidates().isEmpty()) {
            body.append(value(dealScout.error(), "No Deal Scout candidates matched this week.")).append("\n");
        }
        return body.append("\n").append(DISCLAIMER).append("\n").toString();
    }

    private String buildHtml(List<Company> radar, List<Company> watched, DealScoutSection dealScout) {
        String text = buildText(radar, watched, dealScout);
        return "<!doctype html><html><body style=\"font-family:Arial,sans-serif;color:#172033;line-height:1.5\">"
                + "<h1 style=\"font-size:22px\">Startup Intelligence Weekly</h1>"
                + "<p style=\"padding:12px;border:1px solid #e7b86b;background:#fff8e7\">"
                + escape(DISCLAIMER) + "</p><pre style=\"font:14px/1.55 Arial,sans-serif;white-space:pre-wrap\">"
                + escape(text) + "</pre></body></html>";
    }

    private static String weeklyPeriodKey() {
        LocalDate now = LocalDate.now();
        WeekFields fields = WeekFields.ISO;
        return now.get(fields.weekBasedYear()) + "-W" + String.format("%02d", now.get(fields.weekOfWeekBasedYear()));
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record DealCandidate(String companyName, String platform, int score, List<String> whyMatched,
            List<String> redFlags, String sourceUrl) {
    }

    public record DealScoutSection(List<DealCandidate> candidates, String error) {
    }

    public record DigestResult(boolean ok, boolean sent, String periodKey, String subject, String text, String html,
            String messageId, String error) {
    }
}
