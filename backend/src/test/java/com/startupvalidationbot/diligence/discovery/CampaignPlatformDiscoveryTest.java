package com.startupvalidationbot.diligence.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;

import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignIdentity;
import com.startupvalidationbot.diligence.platform.PlatformHttpClient;

class CampaignPlatformDiscoveryTest {
    @Test
    void parsesOnlyMatchingCanonicalStartEngineOfferingAndEnrichesItsDomain() {
        PlatformHttpClient client = mock(PlatformHttpClient.class);
        URI directory = URI.create("https://www.startengine.com/explore");
        URI campaign = URI.create("https://www.startengine.com/offering/bloomy");
        when(client.get("STARTENGINE", directory)).thenReturn("""
                <a href="/offering/other"><h3>Other Co</h3><span>Invest now</span></a>
                <a href="/offering/bloomy"><h3>Bloomy</h3><span>Invest now</span></a>
                """);
        when(client.get("STARTENGINE", campaign)).thenReturn("""
                <html><head><title>Invest in Bloomy | StartEngine</title></head><body>
                <link rel="stylesheet" href="https://fonts.googleapis.com/css">
                <h1>Bloomy</h1><a href="https://joinbloomy.com">Company website</a><p>Invest now</p>
                </body></html>
                """);

        var result = new StartEngineCampaignDiscovery().discover(identity("Bloomy", "joinbloomy.com"), 5,
                new CampaignDiscoveryContext(client, 8));

        assertThat(result.error()).isNull();
        assertThat(result.candidates()).singleElement().satisfies(value -> {
            assertThat(value.campaignUrl()).isEqualTo(campaign.toString());
            assertThat(value.issuerName()).isEqualTo("Bloomy");
            assertThat(value.issuerDomain()).isEqualTo("joinbloomy.com");
            assertThat(value.status()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void parsesRepublicCanonicalSlugButRejectsNavigationLinks() {
        RepublicCampaignDiscovery discovery = new RepublicCampaignDiscovery();
        var candidates = discovery.parse(identity("Yakuru", "yakuru.example"), """
                <a href="/companies">Companies</a>
                <a href="/yakuru"><h2>Yakuru</h2><span>Accepting reservations</span></a>
                """, 5);

        assertThat(candidates).singleElement().satisfies(value -> {
            assertThat(value.campaignUrl()).isEqualTo("https://republic.com/yakuru");
            assertThat(value.status()).isEqualTo("RESERVATION");
        });
    }

    @Test
    void wefunderUsesBoundedCanonicalSlugsAndReportsBlockedAccess() {
        PlatformHttpClient client = mock(PlatformHttpClient.class);
        when(client.get("WEFUNDER", URI.create("https://wefunder.com/bloomy")))
                .thenThrow(new IllegalStateException("Platform returned HTTP 403"));
        WefunderCampaignDiscovery discovery = new WefunderCampaignDiscovery();

        assertThat(discovery.slugs(identity("Bloomy, Inc.", "joinbloomy.com"), 5)).containsExactly("bloomy");
        var result = discovery.discover(identity("Bloomy, Inc.", "joinbloomy.com"), 5,
                new CampaignDiscoveryContext(client, 8));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.error()).contains("HTTP 403");
    }

    @Test
    void contextEnforcesPerPlatformBudgetAndCachesFailuresWithinTheRun() {
        PlatformHttpClient client = mock(PlatformHttpClient.class);
        URI first = URI.create("https://www.startengine.com/offering/one");
        URI second = URI.create("https://www.startengine.com/offering/two");
        URI third = URI.create("https://www.startengine.com/offering/three");
        when(client.get("STARTENGINE", first)).thenReturn("one");
        when(client.get("STARTENGINE", second)).thenReturn("two");
        CampaignDiscoveryContext bounded = new CampaignDiscoveryContext(client, 2);

        assertThat(bounded.get("STARTENGINE", first)).isEqualTo("one");
        assertThat(bounded.get("STARTENGINE", second)).isEqualTo("two");
        Assertions.assertThatThrownBy(() -> bounded.get("STARTENGINE", third))
                .hasMessageContaining("budget exhausted");

        URI blocked = URI.create("https://republic.com/companies");
        when(client.get("REPUBLIC", blocked)).thenThrow(new IllegalStateException("Platform returned HTTP 403"));
        CampaignDiscoveryContext failures = new CampaignDiscoveryContext(client, 5);
        Assertions.assertThatThrownBy(() -> failures.get("REPUBLIC", blocked)).hasMessageContaining("403");
        Assertions.assertThatThrownBy(() -> failures.get("REPUBLIC", blocked)).hasMessageContaining("403");
        verify(client, times(1)).get("REPUBLIC", blocked);
    }

    private static CampaignIdentity identity(String name, String domain) {
        return new CampaignIdentity(1, null, name, "https://" + domain, domain, List.of(), name,
                null, "https://" + domain, null, null, "UNKNOWN", false, "fingerprint");
    }
}
