package com.startupvalidationbot.diligence.discovery;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityDecision;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityStatus;
import com.startupvalidationbot.radar.CompanyIdentity;

@Component
public class CampaignIdentityVerifier {
    public IdentityDecision verify(CampaignIdentity identity, CampaignCandidate candidate) {
        Set<String> names = new LinkedHashSet<>();
        add(names, identity.companyName());
        add(names, identity.issuerName());
        identity.aliases().forEach(value -> add(names, value));
        String candidateName = CompanyIdentity.normalizeName(candidate.issuerName());

        Set<String> domains = new LinkedHashSet<>();
        addDomain(domains, identity.companyDomain());
        addDomain(domains, CompanyIdentity.normalizeDomain(identity.companyWebsite()));
        addDomain(domains, CompanyIdentity.normalizeDomain(identity.issuerWebsite()));
        String candidateDomain = CompanyIdentity.normalizeDomain(candidate.issuerDomain());
        if (candidateDomain != null && !domains.isEmpty() && !domains.contains(candidateDomain)) {
            return new IdentityDecision(IdentityStatus.REJECTED, 0,
                    "Candidate issuer domain conflicts with the known company or SEC issuer domain.");
        }

        boolean exactName = !candidateName.isBlank() && names.contains(candidateName);
        boolean exactDomain = candidateDomain != null && domains.contains(candidateDomain);
        if (exactName && exactDomain) {
            return new IdentityDecision(IdentityStatus.CONFIRMED, 98,
                    "Exact normalized issuer name and verified issuer domain match.");
        }
        if (exactDomain && names.stream().anyMatch(value -> CampaignHtml.similarity(value, candidateName) >= 0.5)) {
            return new IdentityDecision(IdentityStatus.CONFIRMED, 94,
                    "Verified issuer domain matches and the campaign name is consistent.");
        }
        if (exactName && identity.platformConfirmedBySec()
                && candidate.platform().equalsIgnoreCase(identity.knownPlatform())) {
            return new IdentityDecision(IdentityStatus.CONFIRMED, 90,
                    "Exact normalized issuer name matches SEC identity and the intermediary identifies this platform.");
        }
        if (exactName) {
            return new IdentityDecision(IdentityStatus.POSSIBLE, 75,
                    "Exact normalized name match lacks independent domain or intermediary corroboration.");
        }
        double similarity = names.stream().mapToDouble(value -> CampaignHtml.similarity(value, candidateName))
                .max().orElse(0);
        if (similarity >= 0.75) {
            return new IdentityDecision(IdentityStatus.POSSIBLE, 55,
                    "Fuzzy name similarity requires manual identity review.");
        }
        return new IdentityDecision(IdentityStatus.REJECTED, 0,
                "Campaign name does not match the known company identity.");
    }

    private static void add(Set<String> target, String value) {
        String normalized = CompanyIdentity.normalizeName(value);
        if (!normalized.isBlank()) target.add(normalized);
    }

    private static void addDomain(Set<String> target, String value) {
        if (value != null && !value.isBlank()) target.add(value.toLowerCase());
    }
}
