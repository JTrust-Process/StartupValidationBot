package com.startupvalidationbot.offering.intake;

import static com.startupvalidationbot.offering.intake.NativeOfferingDomain.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingSourceAdapter;
import com.startupvalidationbot.offering.PlatformNormalizer;
import com.startupvalidationbot.radar.CompanyIdentity;

@Component
public class SecNativeOfferingAdapter implements NativeOfferingSourceAdapter {
    private final OfferingSourceAdapter sec;

    public SecNativeOfferingAdapter(OfferingSourceAdapter sec) { this.sec = sec; }

    @Override public String source() { return "SEC_REG_CF"; }
    @Override public String capability() { return "SEC_RECENT_FORM_C"; }

    @Override
    public SourceResult discover(int maxCandidates, int maxDetailRequests) {
        List<String> errors = new ArrayList<>();
        List<NativeOfferingCandidate> candidates = new ArrayList<>();
        int details = 0;
        try {
            List<Candidate> recent = sec.fetchRecent();
            int limit = Math.max(1, Math.min(maxCandidates, 25));
            int detailLimit = Math.max(0, Math.min(maxDetailRequests, 25));
            for (Candidate candidate : recent.stream().limit(limit).toList()) {
                Candidate enriched = candidate;
                if (details < detailLimit) {
                    details++;
                    try { enriched = sec.enrich(candidate); }
                    catch (RuntimeException error) {
                        errors.add("SEC filing " + candidate.accessionNumber() + ": " + safe(error));
                    }
                }
                candidates.add(from(enriched, LocalDateTime.now()));
            }
            return new SourceResult(source(), capability(), errors.isEmpty() ? "OK" : "DEGRADED",
                    true, 1 + details, details, List.copyOf(candidates), List.copyOf(errors));
        } catch (RuntimeException error) {
            return SourceResult.failed(source(), capability(), safe(error), 1);
        }
    }

    static NativeOfferingCandidate from(Candidate candidate, LocalDateTime retrievedAt) {
        Map<String, String> evidence = new LinkedHashMap<>(candidate.facts());
        evidence.put("secLifecycleStatus", status(candidate).name());
        String platform = PlatformNormalizer.normalize(first(candidate.intermediaryName(), candidate.platform()));
        return new NativeOfferingCandidate("SEC_EDGAR_RECENT", platform, candidate.accessionNumber(),
                candidate.issuerName(), candidate.offeringUrl(), candidate.issuerWebsite(),
                CompanyIdentity.normalizeDomain(candidate.issuerWebsite()), status(candidate), "REG_CF",
                candidate.intermediaryName(), candidate.securityType(), candidate.amountRaised(),
                candidate.targetAmount(), candidate.maximumAmount(), candidate.valuationOrCap(),
                candidate.minimumInvestment(), null, candidate.deadline(), null,
                "SEC-filed Regulation Crowdfunding issuer.", candidate.issuerCik(), candidate.accessionNumber(),
                candidate.fileNumber(), candidate.secFilingUrl(), candidate.filingType(), candidate.filingDate(),
                Map.copyOf(evidence), retrievedAt);
    }

    static Status status(Candidate candidate) {
        String form = candidate.filingType() == null ? "" : candidate.filingType().toUpperCase();
        if (form.equals("C-W")) return Status.WITHDRAWN;
        if (form.equals("C-TR")) return Status.TERMINATED;
        if (candidate.deadline() != null) {
            if (candidate.deadline().isBefore(LocalDate.now())) return Status.CLOSED;
            if (!candidate.deadline().isAfter(LocalDate.now().plusDays(7))) return Status.CLOSING_SOON;
            return Status.ACTIVE;
        }
        if (form.equals("C") || form.equals("C/A") || form.equals("C-U") || form.equals("C-U/A")) {
            return Status.UNKNOWN;
        }
        return Status.UNKNOWN;
    }

    private static String first(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String safe(RuntimeException error) {
        String value = error.getMessage();
        return value == null ? error.getClass().getSimpleName()
                : value.replaceAll("https?://\\S+", "[external URL]");
    }
}
