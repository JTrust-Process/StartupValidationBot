package com.startupvalidationbot.diligence.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.startupvalidationbot.diligence.DiligenceDomain.PlatformCampaign;

class CampaignUrlLineageTest {
    private static final String URL = "https://wefunder.com/acme";

    @Test void syntacticUrlAndCompanyIdentityAreNotLineage() {
        assertThat(CampaignUrlLineage.allowed(URL, Map.of("issuerName", "Acme", "intermediaryCik", "0001670254"), null, "SEC_EDGAR")).isFalse();
        assertThat(CampaignUrlLineage.allowed(URL, Map.of("_secCampaign.state", "CONFIRMED", "_secCampaign.url", URL), null, "SEC_EDGAR")).isFalse();
    }

    @Test void explicitlyObservedSecAndIssuerLinksAreAllowed() {
        for (String prefix : new String[] { "_secCampaign", "_issuerCampaign" }) {
            assertThat(CampaignUrlLineage.allowed(URL, Map.of(prefix + ".state", "CONFIRMED", prefix + ".url", URL,
                    prefix + ".sourceUrl", "https://www.sec.gov/Archives/example.xml", prefix + ".source", "SEC_FILED_FACT"), null, "SEC_EDGAR")).isTrue();
        }
        assertThat(CampaignUrlLineage.allowed(URL, Map.of("offeringUrl", URL), null, "MANUAL")).isTrue();
    }

    @Test void verifiedHistoryIsAllowedButLegacyGuessesAreNot() {
        PlatformCampaign prior = mock(PlatformCampaign.class);
        when(prior.campaignUrl()).thenReturn(URL);
        when(prior.lastVerifiedAt()).thenReturn(LocalDateTime.now().minusDays(1));
        when(prior.campaignUrlSource()).thenReturn("TARGETED_CANONICAL_PROBE");
        assertThat(CampaignUrlLineage.allowed(URL, Map.of(), prior, "SEC_EDGAR")).isFalse();
        when(prior.campaignUrlSource()).thenReturn("PUBLIC_CAMPAIGN_URL");
        assertThat(CampaignUrlLineage.allowed(URL, Map.of(), prior, "SEC_EDGAR")).isTrue();
    }
}
