package com.startupvalidationbot.radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.InvalidCompanyIdentityException;
import com.startupvalidationbot.radar.RadarDomain.Candidate;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.EvidenceClassification;
import com.startupvalidationbot.radar.RadarDomain.Source;
import com.startupvalidationbot.radar.RadarIntelStore;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.CompanyUpsert;
import com.startupvalidationbot.radar.RadarStore.DiscoverySaveResult;
import com.startupvalidationbot.radar.source.SourceFetchException;
import com.startupvalidationbot.radar.source.StartupSourceAdapter;

class RadarDiscoveryServiceTest {
    private final RadarStore store = mock(RadarStore.class);
    private final RadarIntelStore intelStore = mock(RadarIntelStore.class);
    private final RadarAnalysisService analysisService = mock(RadarAnalysisService.class);
    private final StartupSourceAdapter adapter = mock(StartupSourceAdapter.class);
    private final Source source = new Source(1L, "product-hunt", "PRODUCT_HUNT", "Product Hunt", null,
            "{}", true, null, null, null);

    private RadarDiscoveryService service;

    @BeforeEach
    void setUp() {
        when(store.listSources()).thenReturn(List.of(source));
        when(adapter.supports("PRODUCT_HUNT")).thenReturn(true);
        service = new RadarDiscoveryService(store, intelStore, analysisService, List.of(adapter), 30);
    }

    @Test
    void invalidCandidateIdentityIsVisibleButDoesNotFailCoreDiscovery() throws Exception {
        Candidate candidate = candidate("বাংলা নাটক");
        when(adapter.discover(any(), anyInt())).thenReturn(List.of(candidate));
        when(store.upsertCompany(candidate))
                .thenThrow(new InvalidCompanyIdentityException("company name cannot normalize to an empty value"));

        RadarDiscoveryService.DiscoveryResult result = service.discoverEnabledSources();

        assertThat(result.errors()).isEmpty();
        assertThat(result.diagnostics()).containsExactly(
                "Product Hunt / বাংলা নাটক: company name cannot normalize to an empty value");
    }

    @Test
    void persistenceFailureRemainsFatal() throws Exception {
        Candidate candidate = candidate("Valid Company");
        when(adapter.discover(any(), anyInt())).thenReturn(List.of(candidate));
        when(store.upsertCompany(candidate)).thenThrow(new IllegalStateException("database unavailable"));

        RadarDiscoveryService.DiscoveryResult result = service.discoverEnabledSources();

        assertThat(result.errors()).containsExactly("Product Hunt / Valid Company: database unavailable");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void sourceFailureRemainsFatal() throws Exception {
        when(adapter.discover(any(), anyInt())).thenThrow(new SourceFetchException("upstream unavailable"));

        RadarDiscoveryService.DiscoveryResult result = service.discoverEnabledSources();

        assertThat(result.errors()).containsExactly("Product Hunt: upstream unavailable");
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void nativeOfferingProvenanceDoesNotJoinOrdinaryDiscoverySchedule() {
        Company company = company();
        Candidate candidate = new Candidate("native-offering-republic", "candidate-1", "Native Co",
                "https://native.example", "Public Reg CF offering", "Infrastructure",
                List.of("Infrastructure", "Reg CF"), null, null, "https://republic.com/native-co",
                LocalDateTime.now(), "Public official offering evidence", "", "",
                EvidenceClassification.PUBLIC_OFFICIAL);
        Source nativeSource = new Source(2L, candidate.sourceKey(), "PLATFORM_OFFERING",
                "Republic live offerings", "https://republic.com/native-co", "{}", false,
                null, null, null);
        when(store.upsertSource(candidate.sourceKey(), "PLATFORM_OFFERING", "Republic live offerings",
                candidate.sourceUrl(), false)).thenReturn(nativeSource);
        when(store.upsertCompany(candidate)).thenReturn(new CompanyUpsert(company, true));
        when(store.saveDiscoveryAndSnapshot(company.id(), nativeSource, candidate))
                .thenReturn(new DiscoverySaveResult(true, true, 4L, List.of()));
        when(store.findCompany(company.id())).thenReturn(java.util.Optional.of(company));

        service.ingestOfficialOffering(candidate, "Republic live offerings", candidate.sourceUrl());

        verify(store).upsertSource(candidate.sourceKey(), "PLATFORM_OFFERING", "Republic live offerings",
                candidate.sourceUrl(), false);
        verify(analysisService).analyzeDeterministic(company, "RADAR");
    }

    private static Candidate candidate(String name) {
        return new Candidate("product-hunt", "candidate-1", name, null, "Public description", "Unknown",
                List.of(), null, null, "https://example.test/candidate", LocalDateTime.now(), "Public text");
    }

    private static Company company() {
        LocalDateTime now = LocalDateTime.now();
        return new Company(7L, "Native Co", "native.example", "https://native.example",
                "Public Reg CF offering", "Infrastructure", List.of("Infrastructure", "Reg CF"),
                null, null, List.of(), 0, 0, "", 1, now, now, false, false);
    }
}
