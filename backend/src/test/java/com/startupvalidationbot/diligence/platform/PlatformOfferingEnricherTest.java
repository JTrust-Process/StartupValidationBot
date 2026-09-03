package com.startupvalidationbot.diligence.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.diligence.DiligenceDomain.CampaignStatus;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.Status;

class PlatformOfferingEnricherTest {
    private static final String ACTIVE = """
            <html><head><title>Acme Energy | PLATFORM</title><script type="application/ld+json">
            {"name":"Acme Energy"}</script></head><body>
            Open for investment. Security type: Crowd SAFE. Minimum investment: $100.
            Valuation cap: $12M. Discount: 20%. Target raise: $100,000. Maximum raise: $1,235,000.
            Amount raised: $640,000. Investors: 814. Deadline: October 31, 2026.
            </body></html>
            """;

    @Test
    void parsesWefunderActiveCampaignFixture() {
        var value = new WefunderOfferingEnricher(mock(PlatformHttpClient.class))
                .parse(offering(1), URI.create("https://wefunder.com/acme"), ACTIVE.replace("PLATFORM", "Wefunder"));
        assertThat(value.platform()).isEqualTo("WEFUNDER");
        assertThat(value.status()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(value.issuerName()).isEqualTo("Acme Energy");
        assertThat(value.minimumInvestment()).isEqualByComparingTo("100");
        assertThat(value.valuationCap()).isEqualByComparingTo("12000000");
        assertThat(value.amountRaised()).isEqualByComparingTo("640000");
        assertThat(value.investorCount()).isEqualTo(814);
        assertThat(value.deadline()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void parsesRepublicFundedAndStartEngineClosedFixturesWithoutInventingMissingTerms() {
        var republic = new RepublicOfferingEnricher(mock(PlatformHttpClient.class)).parse(offering(2),
                URI.create("https://republic.com/acme"), ACTIVE.replace("PLATFORM", "Republic")
                        .replace("Open for investment", "Successfully funded"));
        var startEngine = new StartEngineOfferingEnricher(mock(PlatformHttpClient.class)).parse(offering(3),
                URI.create("https://www.startengine.com/offering/acme"),
                "<title>Acme Labs - StartEngine</title><p>Offering closed</p>");
        assertThat(republic.status()).isEqualTo(CampaignStatus.FUNDED);
        assertThat(startEngine.status()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(startEngine.minimumInvestment()).isNull();
        assertThat(startEngine.facts()).doesNotContainKeys("minimumInvestment", "valuationCap");
    }

    @Test
    void rejectsUnexpectedHostsPrivateAddressesAndFragments() {
        assertThatThrownBy(() -> PlatformUrlPolicy.require("WEFUNDER", "https://attacker.example/acme"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformUrlPolicy.require("REPUBLIC", "http://127.0.0.1/test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformUrlPolicy.require("STARTENGINE", "https://startengine.com/acme#secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedCampaignDoesNotInventTerms() {
        var value = new RepublicOfferingEnricher(mock(PlatformHttpClient.class)).parse(offering(4),
                URI.create("https://republic.com/acme"), "<html><body>Temporarily unavailable</body></html>");

        assertThat(value.status()).isEqualTo(CampaignStatus.UNKNOWN);
        assertThat(value.issuerName()).isNull();
        assertThat(value.securityType()).isNull();
        assertThat(value.facts()).isEmpty();
    }

    @Test
    void retriesTransientFailureTwiceAndBoundsSuccessfulResponseBody() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<InputStream> unavailable = mock(HttpResponse.class);
        when(unavailable.statusCode()).thenReturn(503);
        when(unavailable.body()).thenAnswer(ignored -> new ByteArrayInputStream(new byte[0]));
        when(http.<InputStream>send(org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.any())).thenReturn(unavailable);
        PlatformHttpClient client = new PlatformHttpClient(50_000, 1, http);

        assertThatThrownBy(() -> client.get("WEFUNDER", URI.create("https://wefunder.com/acme")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("temporarily unavailable");
        verify(http, times(2)).send(org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.any());

        HttpClient oversizedHttp = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<InputStream> oversized = mock(HttpResponse.class);
        when(oversized.statusCode()).thenReturn(200);
        when(oversized.body()).thenReturn(new ByteArrayInputStream(new byte[50_001]));
        when(oversizedHttp.<InputStream>send(org.mockito.ArgumentMatchers.any(HttpRequest.class),
                org.mockito.ArgumentMatchers.any())).thenReturn(oversized);

        assertThatThrownBy(() -> new PlatformHttpClient(50_000, 1, oversizedHttp)
                .get("WEFUNDER", URI.create("https://wefunder.com/acme")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("size limit");
    }

    private static Offering offering(long id) {
        return new Offering(id, 7L, "Acme Energy", "Acme Energy, Inc.", "0001234567", "Wefunder",
                "Wefunder Portal LLC", "https://wefunder.com/acme", "https://www.sec.gov/example", "accession",
                "020-1", "C", LocalDate.now(), "REG_CF", "Crowd SAFE", null, null, null, null, null, null,
                Status.ACTIVE, "TEST", MatchStatus.CONFIRMED, 100, "Matched", LocalDateTime.now(), LocalDateTime.now());
    }
}
