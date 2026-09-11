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

class RepublicNativeOfferingAdapterTest {
    @Test
    void parsesCurrentRegCfTermsAndSeparatesReservationsAndExcludedExemptions() {
        String html = """
                <a href="/gridcool"><h3>GridCool Systems</h3><p>Cooling retrofits Republic Funding Portal · Reg CF
                $642,000 raised 814 investors SAFE security type $18M valuation cap $250 minimum investment
                October 1, 2026 deadline</p></a>
                <a href="/mintworks"><h3>Mintworks</h3><p>Accepting reservations Republic Funding Portal · Reg CF
                $36,052 reserved 80 investors $100 min. investment</p></a>
                <a href="/oma3"><h3>OMA3</h3><p>Accredited only Capital R · Reg D 506(c) $10,811 raised</p></a>
                <a href="/atari-hotels"><h3>Atari Hotels</h3><p>Capital R · Reg A+ $14,500 raised</p></a>
                <a href="/companies">Browse all companies</a>
                """;

        var candidates = RepublicNativeOfferingAdapter.parseDirectory(html, 25, LocalDateTime.now());

        assertThat(candidates).hasSize(4);
        assertThat(candidates.get(0)).satisfies(value -> {
            assertThat(value.companyName()).isEqualTo("GridCool Systems");
            assertThat(value.canonicalUrl()).isEqualTo("https://republic.com/gridcool");
            assertThat(value.status()).isEqualTo(Status.ACTIVE);
            assertThat(value.actionableRegCf()).isTrue();
            assertThat(value.amountRaised()).isEqualByComparingTo("642000");
            assertThat(value.minimumInvestment()).isEqualByComparingTo("250");
            assertThat(value.valuationOrCap()).isEqualTo("$18M valuation cap");
            assertThat(value.securityType()).isEqualTo("SAFE");
            assertThat(value.sourceEvidence().get("securityTypeRaw")).contains("SAFE");
        });
        assertThat(candidates.get(1).status()).isEqualTo(Status.RESERVATION);
        assertThat(candidates.get(1).actionableRegCf()).isTrue();
        assertThat(candidates.get(2).exemption()).isEqualTo("REG_D");
        assertThat(candidates.get(2).actionableRegCf()).isFalse();
        assertThat(candidates.get(3).exemption()).isEqualTo("REG_A");
        assertThat(candidates.get(3).actionableRegCf()).isFalse();
    }

    @Test
    void honorsCandidateLimitWithoutFollowingUnrelatedLinks() {
        String html = """
                <a href="/first"><h3>First</h3>Republic Funding Portal · Reg CF $100 raised</a>
                <a href="/second"><h3>Second</h3>Republic Funding Portal · Reg CF $200 raised</a>
                <a href="https://example.com/not-republic"><h3>Noise</h3>Reg CF</a>
                """;
        assertThat(RepublicNativeOfferingAdapter.parseDirectory(html, 1, LocalDateTime.now()))
                .singleElement().extracting(value -> value.companyName()).isEqualTo("First");
    }

    @Test
    void elapsedDeadlineOverridesActiveMarketingCopy() {
        String html = """
                <a href="/stale"><h3>Stale Co</h3><p>Live opportunity · Reg CF
                $100,000 raised January 1, 2020 deadline</p></a>
                """;

        assertThat(RepublicNativeOfferingAdapter.parseDirectory(html, 25, LocalDateTime.now()))
                .singleElement().satisfies(value -> {
                    assertThat(value.status()).isEqualTo(Status.CLOSED);
                    assertThat(value.actionableRegCf()).isFalse();
                });
    }

    @Test
    void platformFailureDegradesWithoutAttemptingABypass() {
        PlatformHttpClient http = mock(PlatformHttpClient.class);
        when(http.get(anyString(), any())).thenThrow(new IllegalStateException("Platform returned HTTP 403"));

        var result = new RepublicNativeOfferingAdapter(http).discover(25, 10);

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.errors()).singleElement().asString().contains("HTTP 403");
    }

    @Test
    void normalizesExplicitSecuritiesAndRejectsNearbySentenceFragments() {
        String html = """
                <a href="/hope"><h3>Hope Neuron</h3><p>Republic Funding Portal · Reg CF
                Common Stock shares Common stock issued by the company security type $500 minimum investment</p></a>
                <a href="/olo"><h3>Olo Test</h3><p>Republic Funding Portal · Reg CF
                offered on or off this investment platform security type $100 minimum investment</p></a>
                """;

        var candidates = RepublicNativeOfferingAdapter.parseDirectory(html, 25, LocalDateTime.now());

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).securityType()).isEqualTo("Common Stock");
        assertThat(candidates.get(1).securityType()).isNull();
        assertThat(candidates.get(1).sourceEvidence().get("securityTypeRaw"))
                .contains("offered on or off this investment platform");
    }
}
