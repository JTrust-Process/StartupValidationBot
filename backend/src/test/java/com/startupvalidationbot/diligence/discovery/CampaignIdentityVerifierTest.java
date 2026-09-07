package com.startupvalidationbot.diligence.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.IdentityStatus;

class CampaignIdentityVerifierTest {
    private final CampaignIdentityVerifier verifier = new CampaignIdentityVerifier();

    @Test
    void confirmsOnlyWithCorroboratingDomainOrSecIntermediary() {
        assertThat(verifier.verify(identity(true), candidate("Bloomy", "joinbloomy.com")).status())
                .isEqualTo(IdentityStatus.CONFIRMED);
        assertThat(verifier.verify(identity(true), candidate("Bloomy, Inc.", null)).status())
                .isEqualTo(IdentityStatus.CONFIRMED);
        assertThat(verifier.verify(identity(false), candidate("Bloomy", null)).status())
                .isEqualTo(IdentityStatus.POSSIBLE);
    }

    @Test
    void rejectsAConflictingKnownIssuerDomain() {
        var decision = verifier.verify(identity(false), candidate("Bloomy", "different.example"));

        assertThat(decision.status()).isEqualTo(IdentityStatus.REJECTED);
        assertThat(decision.reason()).contains("conflicts");
    }

    @Test
    void verifiedDomainCanCorroborateAConsistentNameButFuzzyNameAloneCannotConfirm() {
        assertThat(verifier.verify(identity(false), candidate("Bloomy Platform", "joinbloomy.com")).status())
                .isEqualTo(IdentityStatus.CONFIRMED);
        CampaignIdentity longer = new CampaignIdentity(1, null, "Bloomy Energy Storage Systems", null, null,
                List.of(), null, null, null, null, null, "UNKNOWN", false, "fingerprint");

        assertThat(verifier.verify(longer, candidate("Bloomy Energy Storage", null)).status())
                .isEqualTo(IdentityStatus.POSSIBLE);
    }

    private static CampaignIdentity identity(boolean secPlatform) {
        return new CampaignIdentity(1, 2L, "Bloomy", "https://joinbloomy.com", "joinbloomy.com",
                List.of("Bloomy, Inc."), "Bloomy, Inc.", "000123", "https://joinbloomy.com",
                "StartEngine Primary LLC", "https://www.sec.gov/example", "STARTENGINE", secPlatform,
                "fingerprint");
    }

    private static CampaignCandidate candidate(String name, String domain) {
        return new CampaignCandidate("STARTENGINE", "https://www.startengine.com/offering/bloomy", name,
                domain, "ACTIVE", "bloomy", "PUBLIC_DIRECTORY", 80, Map.of());
    }
}
