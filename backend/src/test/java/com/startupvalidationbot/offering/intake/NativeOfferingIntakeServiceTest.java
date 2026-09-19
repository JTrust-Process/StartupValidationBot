package com.startupvalidationbot.offering.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingMatchService;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.NativeOfferingCandidate;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.SourceResult;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain.Status;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.CompanyUpsert;
import com.startupvalidationbot.radar.service.RadarDiscoveryService;

class NativeOfferingIntakeServiceTest {
    @Test
    void createsRadarCompanyFromLegitimateLiveOfferingAndReusesExistingPipeline() {
        Fixture f = new Fixture();
        Company company = company(7L, "GridCool Systems", null);
        when(f.radar.listCompanies()).thenReturn(List.of(), List.of(company));
        when(f.matcher.match(anyString(), any(), any())).thenReturn(
                new Match(null, MatchStatus.UNMATCHED, 0, "No match"),
                new Match(7L, MatchStatus.LIKELY, 85, "Unique exact public-offering name."));
        when(f.discovery.ingestOfficialOffering(any(), anyString(), anyString()))
                .thenReturn(new CompanyUpsert(company, true));
        when(f.nativeStore.saveCandidate(any(), any(), anyString(), anyInt(), anyBoolean()))
                .thenReturn(true);
        when(f.nativeStore.upsertPlatformOffering(any(), any(), any()))
                .thenReturn(new NativeOfferingStore.PlatformUpsert(44L, true));

        var result = f.service(adapter(candidate("gridcool", Status.ACTIVE)), 25).run();

        assertThat(result.newCompanies()).isEqualTo(1);
        assertThat(result.newOfferings()).isEqualTo(1);
        assertThat(result.activeCandidates()).isEqualTo(1);
        verify(f.discovery).ingestOfficialOffering(any(), anyString(), anyString());
        verify(f.diligence).upsertCampaign(any());
    }

    @Test
    void matchesExistingRadarCompanyWithoutCreatingDuplicate() {
        Fixture f = new Fixture();
        Company company = company(8L, "GridCool Systems", "gridcool.example");
        when(f.radar.listCompanies()).thenReturn(List.of(company));
        when(f.matcher.match(anyString(), any(), any())).thenReturn(
                new Match(8L, MatchStatus.CONFIRMED, 100, "Exact verified domain."));
        when(f.nativeStore.saveCandidate(any(), any(), anyString(), anyInt(), anyBoolean()))
                .thenReturn(false);
        when(f.nativeStore.upsertPlatformOffering(any(), any(), any()))
                .thenReturn(new NativeOfferingStore.PlatformUpsert(45L, false));

        var result = f.service(adapter(candidate("gridcool", Status.ACTIVE)), 25).run();

        assertThat(result.newCompanies()).isZero();
        assertThat(result.matchedCompanies()).isEqualTo(1);
        assertThat(result.duplicatesPrevented()).isEqualTo(1);
        verify(f.discovery, never()).ingestOfficialOffering(any(), anyString(), anyString());
    }

    @Test
    void ambiguousAndClosedCandidatesStayOutOfOfferingAndReviewPacketCreation() {
        Fixture f = new Fixture();
        when(f.radar.listCompanies()).thenReturn(List.of(company(1L, "Twin Co", null), company(2L, "Twin Co", null)));
        when(f.matcher.match(anyString(), any(), any())).thenReturn(
                new Match(null, MatchStatus.AMBIGUOUS, 45, "Two exact names."));

        var result = f.service(adapter(candidate("ambiguous", Status.ACTIVE), candidate("closed", Status.CLOSED)), 25).run();

        assertThat(result.rejected()).isEqualTo(2);
        assertThat(result.newOfferings()).isZero();
        verify(f.nativeStore, never()).upsertPlatformOffering(any(), any(), any());
        verify(f.diligence, never()).upsertCampaign(any());
    }

    private static NativeOfferingSourceAdapter adapter(NativeOfferingCandidate... candidates) {
        return new NativeOfferingSourceAdapter() {
            @Override public String source() { return "REPUBLIC"; }
            @Override public String capability() { return "PUBLIC_LIVE_DIRECTORY"; }
            @Override public SourceResult discover(int max, int details) {
                return new SourceResult(source(), capability(), "OK", true, 1, 0,
                        List.of(candidates).stream().limit(max).toList(), List.of());
            }
        };
    }

    private static NativeOfferingCandidate candidate(String slug, Status status) {
        return new NativeOfferingCandidate("REPUBLIC_DIRECTORY", "Republic", slug, "GridCool Systems",
                "https://republic.com/" + slug, "https://gridcool.example", "gridcool.example", status,
                "REG_CF", "Republic Funding Portal", "Crowd SAFE", new BigDecimal("642000"),
                new BigDecimal("100000"), new BigDecimal("1235000"), "$18M valuation cap",
                new BigDecimal("250"), null, LocalDate.now().plusDays(30), "Technology",
                "Liquid cooling systems.", null, null, null, null, null, null,
                Map.of("listingText", "Public Reg CF campaign"), LocalDateTime.now());
    }

    private static Company company(long id, String name, String domain) {
        LocalDateTime now = LocalDateTime.now();
        return new Company(id, name, domain, domain == null ? null : "https://" + domain,
                "Liquid cooling systems.", "Technology", List.of("Technology"), null, null,
                List.of(), 60, 50, "", 1, now, now, false, false);
    }

    private static final class Fixture {
        private final NativeOfferingStore nativeStore = mock(NativeOfferingStore.class);
        private final OfferingStore offerings = mock(OfferingStore.class);
        private final DiligenceStore diligence = mock(DiligenceStore.class);
        private final OfferingMatchService matcher = mock(OfferingMatchService.class);
        private final RadarStore radar = mock(RadarStore.class);
        private final RadarDiscoveryService discovery = mock(RadarDiscoveryService.class);

        private NativeOfferingIntakeService service(NativeOfferingSourceAdapter adapter, int newCompanyLimit) {
            return new NativeOfferingIntakeService(List.of(adapter), nativeStore, offerings, diligence,
                    matcher, radar, discovery, 25, 10, newCompanyLimit);
        }
    }
}
