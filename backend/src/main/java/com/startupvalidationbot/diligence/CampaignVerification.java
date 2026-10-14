package com.startupvalidationbot.diligence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;
import com.startupvalidationbot.diligence.discovery.CampaignUrlLineage;

/** URL lineage, an issuer-matched page, and a current public status are independent evidence. */
public final class CampaignVerification {
    public static final int FRESH_HOURS = 72;
    private CampaignVerification() { }
    public enum PageState { VERIFIED, ISSUER_UNRESOLVED, ISSUER_MISMATCH, ACCESS_BLOCKED, MISSING, UNAVAILABLE, HISTORICAL_ONLY, NOT_CHECKED, AMBIGUOUS }
    public record UrlEvidence(String url, String state, String classification, String sourceUrl,
            LocalDateTime observedAt, String explanation) { }
    public record Observation(long offeringId, String campaignUrl, String sourceClassification,
            String sourceUrl, LocalDateTime observedAt, PageState pageState, String publicStatus,
            String sourceOutcome, String issuerEvidence, String statusExcerpt, String verificationMethod,
            String explanation, int httpRequests) { }
    public record View(long offeringId, UrlEvidence urlEvidence, PageState pageState, String publicStatus,
            boolean fresh, LocalDateTime lastSuccessfulVerification, LocalDateTime latestAttempt,
            Observation latestObservation, Observation successfulObservation, String historicalStatus,
            List<String> limitations, List<String> questions, List<String> discrepancies) { }

    public static View project(long offeringId, String url, String provenance, Map<String, String> facts,
            PlatformCampaign history, String lifecycle, LocalDate deadline, Observation latest,
            Observation success, LocalDateTime now) {
        UrlEvidence link = CampaignUrlLineage.evidence(url, facts, history, provenance);
        List<String> limits = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        List<String> discrepancies = new ArrayList<>();
        boolean sameUrl = latest != null && link.url() != null && link.url().equals(latest.campaignUrl());
        boolean current = sameUrl && List.of("EXPLICIT_OBSERVED", "HISTORICAL").contains(link.state()) && latest.pageState() == PageState.VERIFIED
                && latest.observedAt() != null && !latest.observedAt().isAfter(now)
                && latest.observedAt().isAfter(now.minusHours(FRESH_HOURS));
        PageState page = sameUrl ? latest.pageState() : PageState.NOT_CHECKED;
        if ("CONFLICTING".equals(link.state())) { page = PageState.AMBIGUOUS; current = false; }
        boolean matchingSuccess = success != null && link.url() != null && link.url().equals(success.campaignUrl());
        String status = current ? latest.publicStatus() : matchingSuccess ? "STALE_OR_UNAVAILABLE" : "UNKNOWN";
        if (!current && matchingSuccess && page == PageState.VERIFIED) page = PageState.HISTORICAL_ONLY;
        LocalDate localDate = now.atZone(java.time.ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.of("America/New_York")).toLocalDate();
        boolean ended = List.of("ENDED", "WITHDRAWN", "TERMINATED").contains(lifecycle)
                || deadline != null && deadline.isBefore(localDate);
        boolean conflictingEnd = "WITHDRAWN".equals(lifecycle) && List.of("FUNDED", "CLOSED", "TERMINATED").contains(status)
                || "TERMINATED".equals(lifecycle) && List.of("FUNDED", "WITHDRAWN").contains(status);
        if (current && (ended && List.of("ACTIVE", "CLOSING_SOON", "RESERVATION", "COMING_SOON").contains(status) || conflictingEnd)) {
            discrepancies.add("Public page status conflicts with the retained SEC lifecycle/deadline. Both sources are retained; current availability is not established.");
            status = "CONFLICTING";
        }
        if (latest != null && latest.explanation().startsWith("Conflicting public status")) discrepancies.add(latest.explanation());
        limits.add("Public advertising does not establish personal eligibility or the ability to complete an investment transaction.");
        limits.add("Independent public status observations are current for at most " + FRESH_HOURS + " hours; failed access is not offering closure.");
        if (latest != null && latest.pageState() != PageState.VERIFIED) limits.add(latest.explanation());
        if (history != null && !matchingSuccess) limits.add("Retained legacy/listing facts are historical evidence, not proof of an independently verified campaign page.");
        if (link.url() == null) questions.add("What is the exact source-backed campaign URL for this offering?");
        if (!"EXPLICIT_OBSERVED".equals(link.state())) questions.add("Can the campaign URL provenance be established without guessing a path?");
        if (!current) questions.add("Can an accessible public page independently establish the current issuer and offering status?");
        if (current && "UNKNOWN".equals(status)) questions.add("Does the issuer-matched public page explicitly establish an open, reservation, or ended offering?");
        if (!discrepancies.isEmpty()) questions.add("Which current authoritative filing or explicit platform statement resolves the discrepancy?");
        return new View(offeringId, link, page, status, current,
                matchingSuccess ? success.observedAt() : null, latest == null || latest.httpRequests() == 0 ? null : latest.observedAt(),
                latest, matchingSuccess ? success : null, matchingSuccess ? success.publicStatus() : history == null ? null : history.status().name(),
                List.copyOf(limits), List.copyOf(questions), List.copyOf(discrepancies));
    }
}
