package com.startupvalidationbot.diligence.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.CampaignCandidate;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain.DiscoveryAttempt;
import com.startupvalidationbot.diligence.platform.PlatformHttpClient;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.Status;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarStore;

class CampaignDiscoveryServiceTest {
    @Test
    void attachesOneCorroboratedCampaignWithoutCreatingOtherRecords() {
        Fixture fixture = fixture(List.of(candidate("https://www.startengine.com/offering/bloomy")));

        var result = fixture.service.discover();

        assertThat(result.companiesEligible()).isEqualTo(1);
        assertThat(result.campaignsConfirmed()).isEqualTo(1);
        verify(fixture.offerings).attachCampaignUrl(2L, "STARTENGINE",
                "https://www.startengine.com/offering/bloomy");
        verify(fixture.store).saveCandidate(anyLong(), any(), any(), any());
    }

    @Test
    void doesNotAttachWhenMultipleCampaignsArePlausible() {
        Fixture fixture = fixture(List.of(candidate("https://www.startengine.com/offering/bloomy"),
                candidate("https://www.startengine.com/offering/bloomy-2026")));

        var result = fixture.service.discover();

        assertThat(result.campaignsPossible()).isEqualTo(2);
        assertThat(result.campaignsConfirmed()).isZero();
        verify(fixture.offerings, never()).attachCampaignUrl(anyLong(), any(), any());
    }

    @Test
    void unchangedNegativeCheckUsesCacheAndMakesNoPlatformRequest() {
        Fixture fixture = fixture(List.of());
        when(fixture.store.cached(anyLong(), any(), any(), any())).thenReturn(true);

        var result = fixture.service.discover();

        assertThat(result.cacheHits()).isEqualTo(1);
        assertThat(result.startEngineSearches()).isZero();
        verify(fixture.discoverer, never()).discover(any(), anyInt(), any());
    }

    @Test
    void limitsEligibleCompaniesPerRun() {
        CampaignDiscoveryStore store = mock(CampaignDiscoveryStore.class);
        OfferingStore offerings = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        PlatformCampaignDiscovery discoverer = mock(PlatformCampaignDiscovery.class);
        when(discoverer.platform()).thenReturn("STARTENGINE");
        when(discoverer.capability()).thenReturn("PUBLIC_DIRECTORY");
        when(discoverer.discover(any(), anyInt(), any()))
                .thenReturn(DiscoveryAttempt.success(List.of(), "PUBLIC_DIRECTORY"));
        List<Company> companies = List.of(company(1), company(2), company(3));
        when(radar.listCompanies()).thenReturn(companies);
        when(offerings.list(null, null, null, null)).thenReturn(List.of(
                offering(11, 1), offering(12, 2), offering(13, 3)));
        when(offerings.facts(anyLong())).thenReturn(Map.of());
        CampaignDiscoveryService service = new CampaignDiscoveryService(store, offerings, radar,
                mock(DiligenceStore.class), new CampaignIdentityVerifier(), List.of(discoverer),
                mock(PlatformHttpClient.class), 2, 5, 8, 24, 72, 120);

        var result = service.discover();

        assertThat(result.companiesEligible()).isEqualTo(2);
        assertThat(result.startEngineSearches()).isEqualTo(2);
        verify(discoverer, times(2)).discover(any(), anyInt(), any());
    }

    @Test
    void persistsRadarOnlyDiscoveryWithoutFabricatingAnOffering() {
        CampaignDiscoveryStore store = mock(CampaignDiscoveryStore.class);
        OfferingStore offerings = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        PlatformCampaignDiscovery discoverer = mock(PlatformCampaignDiscovery.class);
        when(discoverer.platform()).thenReturn("STARTENGINE");
        when(discoverer.capability()).thenReturn("PUBLIC_DIRECTORY");
        when(discoverer.discover(any(), anyInt(), any())).thenReturn(DiscoveryAttempt.success(
                List.of(new CampaignCandidate("STARTENGINE", "https://www.startengine.com/offering/company-1",
                        "Company 1", "company1.example", "ACTIVE", "company-1", "PUBLIC_DIRECTORY", 85,
                        Map.of())), "PUBLIC_DIRECTORY"));
        when(radar.listCompanies()).thenReturn(List.of(company(1)));
        when(offerings.list(null, null, null, null)).thenReturn(List.of());
        CampaignDiscoveryService service = new CampaignDiscoveryService(store, offerings, radar,
                mock(DiligenceStore.class), new CampaignIdentityVerifier(), List.of(discoverer),
                mock(PlatformHttpClient.class), 25, 5, 8, 24, 72, 120);

        var result = service.discover();

        assertThat(result.campaignsConfirmed()).isEqualTo(1);
        verify(store).saveCandidate(eq(1L), isNull(), any(), any());
        verify(offerings, never()).attachCampaignUrl(anyLong(), any(), any());
    }

    @Test
    void resolvesBloomyStyleLikelySecOfferingWhenIntermediaryAndNameAgree() {
        CampaignDiscoveryStore store = mock(CampaignDiscoveryStore.class);
        OfferingStore offerings = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        DiligenceStore diligence = mock(DiligenceStore.class);
        PlatformCampaignDiscovery discoverer = mock(PlatformCampaignDiscovery.class);
        when(discoverer.platform()).thenReturn("WEFUNDER");
        when(discoverer.capability()).thenReturn("TARGETED_CANONICAL_PROBE");
        when(discoverer.discover(any(), anyInt(), any())).thenReturn(DiscoveryAttempt.success(List.of(
                new CampaignCandidate("WEFUNDER", "https://wefunder.com/bloomy", "Bloomy, Inc.", null,
                        "FUNDED", "bloomy", "TARGETED_CANONICAL_PROBE", 75, Map.of())),
                "TARGETED_CANONICAL_PROBE"));
        Company bloomy = new Company(1L, "Bloomy", "joinbloomy.com", "https://joinbloomy.com", "",
                "Fintech", List.of("fintech"), null, null, List.of(), 65, 55, "", 1,
                LocalDateTime.now(), LocalDateTime.now(), false, false);
        Offering offering = new Offering(2, 1L, "Bloomy", "Bloomy, Inc.", "000123", "Wefunder",
                "Wefunder Portal LLC", null, "https://www.sec.gov/example", "accession", "020-1", "C",
                LocalDate.now(), "REG_CF", "Crowd SAFE", null, null, null, null, null, null,
                Status.ACTIVE, "SEC", MatchStatus.LIKELY, 85, "Name match", LocalDateTime.now(),
                LocalDateTime.now());
        when(radar.listCompanies()).thenReturn(List.of(bloomy));
        when(offerings.list(null, null, null, null)).thenReturn(List.of(offering));
        when(offerings.facts(2L)).thenReturn(Map.of());
        CampaignDiscoveryService service = new CampaignDiscoveryService(store, offerings, radar, diligence,
                new CampaignIdentityVerifier(), List.of(discoverer), mock(PlatformHttpClient.class),
                25, 5, 8, 24, 72, 120);

        var result = service.discover();

        assertThat(result.wefunderSearches()).isEqualTo(1);
        assertThat(result.campaignsConfirmed()).isEqualTo(1);
        verify(offerings).attachCampaignUrl(2L, "WEFUNDER", "https://wefunder.com/bloomy");
        verify(diligence).availability(1L, "WEFUNDER", "FOUND",
                "A corroborated public campaign URL was discovered and saved.", null);
    }

    @Test
    void recordsUnavailablePlatformWithoutFailingTheRun() {
        CampaignDiscoveryStore store = mock(CampaignDiscoveryStore.class);
        OfferingStore offerings = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        DiligenceStore diligence = mock(DiligenceStore.class);
        PlatformCampaignDiscovery discoverer = mock(PlatformCampaignDiscovery.class);
        when(discoverer.platform()).thenReturn("STARTENGINE");
        when(discoverer.capability()).thenReturn("PUBLIC_DIRECTORY");
        when(discoverer.discover(any(), anyInt(), any()))
                .thenReturn(DiscoveryAttempt.failed("PUBLIC_DIRECTORY", "Platform returned HTTP 403"));
        when(radar.listCompanies()).thenReturn(List.of(company(1)));
        when(offerings.list(null, null, null, null)).thenReturn(List.of(offering(2, 1)));
        when(offerings.facts(2L)).thenReturn(Map.of());
        CampaignDiscoveryService service = new CampaignDiscoveryService(store, offerings, radar, diligence,
                new CampaignIdentityVerifier(), List.of(discoverer), mock(PlatformHttpClient.class),
                25, 5, 8, 24, 72, 120);

        var result = service.discover();

        assertThat(result.errors()).isEqualTo(1);
        verify(diligence).availability(eq(1L), eq("STARTENGINE"), eq("UNAVAILABLE"), any(), any());
    }

    private static Fixture fixture(List<CampaignCandidate> candidates) {
        CampaignDiscoveryStore store = mock(CampaignDiscoveryStore.class);
        OfferingStore offerings = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        DiligenceStore diligence = mock(DiligenceStore.class);
        PlatformCampaignDiscovery discoverer = mock(PlatformCampaignDiscovery.class);
        when(discoverer.platform()).thenReturn("STARTENGINE");
        when(discoverer.capability()).thenReturn("PUBLIC_DIRECTORY");
        when(discoverer.discover(any(), anyInt(), any()))
                .thenReturn(DiscoveryAttempt.success(candidates, "PUBLIC_DIRECTORY"));
        Company company = new Company(1L, "Bloomy", "joinbloomy.com", "https://joinbloomy.com", "",
                "Fintech", List.of("fintech"), null, null, List.of("Bloomy, Inc."), 80, 70, "", 1,
                LocalDateTime.now(), LocalDateTime.now(), false, false);
        Offering offering = new Offering(2, 1L, "Bloomy", "Bloomy, Inc.", "000123", "StartEngine",
                "StartEngine Primary LLC", null, "https://www.sec.gov/example", "accession", "020-1", "C",
                LocalDate.now(), "REG_CF", "Crowd SAFE", null, null, null, null, null, null,
                Status.ACTIVE, "SEC", MatchStatus.CONFIRMED, 100, "Exact identity", LocalDateTime.now(),
                LocalDateTime.now());
        when(radar.listCompanies()).thenReturn(List.of(company));
        when(offerings.list(null, null, null, null)).thenReturn(List.of(offering));
        when(offerings.facts(2L)).thenReturn(Map.of("issuerWebsite", "https://joinbloomy.com"));
        CampaignDiscoveryService service = new CampaignDiscoveryService(store, offerings, radar, diligence,
                new CampaignIdentityVerifier(), List.of(discoverer), mock(PlatformHttpClient.class),
                25, 5, 8, 24, 72, 120);
        return new Fixture(service, store, offerings, discoverer);
    }

    private static Company company(long id) {
        return new Company(id, "Company " + id, "company" + id + ".example", "https://company" + id + ".example",
                "", "Fintech", List.of("fintech"), null, null, List.of(), 80, 70, "", 1,
                LocalDateTime.now(), LocalDateTime.now(), false, false);
    }

    private static Offering offering(long id, long companyId) {
        return new Offering(id, companyId, "Company " + companyId, "Company " + companyId,
                "000" + companyId, "StartEngine", "StartEngine Primary LLC", null,
                "https://www.sec.gov/example/" + id, "accession-" + id, "020-" + id, "C", LocalDate.now(),
                "REG_CF", "Crowd SAFE", null, null, null, null, null, null, Status.ACTIVE, "SEC",
                MatchStatus.CONFIRMED, 100, "Exact identity", LocalDateTime.now(), LocalDateTime.now());
    }

    private static CampaignCandidate candidate(String url) {
        return new CampaignCandidate("STARTENGINE", url, "Bloomy", "joinbloomy.com", "ACTIVE", "bloomy",
                "PUBLIC_DIRECTORY", 85, Map.of());
    }

    private record Fixture(CampaignDiscoveryService service, CampaignDiscoveryStore store,
            OfferingStore offerings, PlatformCampaignDiscovery discoverer) { }
}
