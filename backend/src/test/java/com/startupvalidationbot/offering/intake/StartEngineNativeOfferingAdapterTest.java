package com.startupvalidationbot.offering.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status;
import com.startupvalidationbot.diligence.platform.PlatformHttpClient;

class StartEngineNativeOfferingAdapterTest {
    @Test
    void parsesOnlyCanonicalOfferingLinksAndCurrentCampaignTerms() {
        String directory = """
                <a href="/offering/gridcool"><h3>GridCool Systems</h3><p>Funding progress</p></a>
                <a href="/learn">Learn</a>
                """;
        var listing = StartEngineNativeOfferingAdapter.parseDirectory(directory, 25);
        assertThat(listing).singleElement().satisfies(value -> {
            assertThat(value.name()).isEqualTo("GridCool Systems");
            assertThat(value.url()).isEqualTo("https://www.startengine.com/offering/gridcool");
        });

        String detail = """
                <h1>GridCool Systems</h1>
                <p>Open for investment under Regulation Crowdfunding / Reg CF.</p>
                <p>Amount raised: $642,000 Minimum investment: $250 Price per share: $2.50</p>
                <p>Pre-money valuation: $18M Funding goal: $100,000 Deadline: October 1, 2026</p>
                <p>Security type: Common Stock</p>
                <a href="https://gridcool.example">Official website</a>
                <a href="https://www.sec.gov/Archives/edgar/data/123/000000012326000001/0000000123-26-000001-index.html">SEC filing</a>
                """;
        var candidate = StartEngineNativeOfferingAdapter.parseCampaign(listing.getFirst(), detail,
                LocalDateTime.now());
        assertThat(candidate.status()).isEqualTo(Status.ACTIVE);
        assertThat(candidate.actionableRegCf()).isTrue();
        assertThat(candidate.amountRaised()).isEqualByComparingTo("642000");
        assertThat(candidate.minimumInvestment()).isEqualByComparingTo("250");
        assertThat(candidate.pricePerShare()).isEqualByComparingTo("2.50");
        assertThat(candidate.issuerDomain()).isEqualTo("gridcool.example");
        assertThat(candidate.secAccessionNumber()).isEqualTo("0000000123-26-000001");
    }

    @Test
    void closedCampaignIsHistoricalNotActionable() {
        var listing = new StartEngineNativeOfferingAdapter.Listing("closed-co", "Closed Co",
                "https://www.startengine.com/offering/closed-co", "Past offering");
        var candidate = StartEngineNativeOfferingAdapter.parseCampaign(listing,
                "<h1>Closed Co</h1><p>This Offering is Closed. Reg CF. Amount raised: $50,000.</p>",
                LocalDateTime.now());
        assertThat(candidate.status()).isEqualTo(Status.CLOSED);
        assertThat(candidate.actionableRegCf()).isFalse();
    }

    @Test
    void elapsedDeadlineOverridesOpenCampaignCopy() {
        var listing = new StartEngineNativeOfferingAdapter.Listing("stale-co", "Stale Co",
                "https://www.startengine.com/offering/stale-co", "Open for investment");
        var candidate = StartEngineNativeOfferingAdapter.parseCampaign(listing,
                "<h1>Stale Co</h1><p>Reg CF. Deadline: January 1, 2020.</p>", LocalDateTime.now());

        assertThat(candidate.status()).isEqualTo(Status.CLOSED);
        assertThat(candidate.actionableRegCf()).isFalse();
    }

    @Test
    void robotVerificationIsReportedUnavailableWithoutDetailRequests() {
        PlatformHttpClient http = mock(PlatformHttpClient.class);
        when(http.get(anyString(), any())).thenReturn(
                "<h1>JavaScript is disabled</h1><p>We need to verify that you're not a robot.</p>");

        var result = new StartEngineNativeOfferingAdapter(http).discover(25, 10);

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.requests()).isEqualTo(1);
        assertThat(result.detailRequests()).isZero();
        assertThat(result.errors()).singleElement().asString().contains("no CAPTCHA");
    }
}
