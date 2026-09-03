package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.offering.OfferingDomain.Candidate;
import com.startupvalidationbot.offering.OfferingDomain.Match;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.radar.RadarStore;

class OfferingDiscoveryServiceTest {
    @Test
    void enrichesAndPersistsOnlyDeterministicallyMatchedRadarCandidates() {
        OfferingSourceAdapter source = mock(OfferingSourceAdapter.class);
        OfferingMatchService matcher = mock(OfferingMatchService.class);
        OfferingStore store = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        Candidate matched = candidate("Matched Co", "0001");
        Candidate unmatched = candidate("Unmatched Co", "0002");
        when(source.fetchRecent()).thenReturn(List.of(matched, unmatched));
        when(store.baselineDue(anyInt())).thenReturn(false);
        when(store.listStoredIdentities()).thenReturn(List.of());
        when(radar.listCompanies()).thenReturn(List.of());
        when(matcher.match(matched, List.of())).thenReturn(new Match(7L, MatchStatus.CONFIRMED, 90, "Exact name"));
        when(matcher.match(unmatched, List.of())).thenReturn(new Match(null, MatchStatus.UNMATCHED, 0, "No match"));
        when(source.enrich(matched)).thenReturn(matched);
        var stored = mock(OfferingDomain.Offering.class);
        when(store.upsert(matched, new Match(7L, MatchStatus.CONFIRMED, 90, "Exact name")))
                .thenReturn(new OfferingStore.UpsertResult(stored, true));

        var result = new OfferingDiscoveryService(source, matcher, store, radar, 30).discover();

        assertThat(result.recordsInspected()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        verify(source).enrich(matched);
        verify(source, never()).enrich(unmatched);
        verify(store).upsert(matched, new Match(7L, MatchStatus.CONFIRMED, 90, "Exact name"));
    }

    @Test
    void downgradesAStoredExactNameOnlyConfirmationDuringEveryDiscoveryRun() {
        OfferingSourceAdapter source = mock(OfferingSourceAdapter.class);
        OfferingMatchService matcher = mock(OfferingMatchService.class);
        OfferingStore store = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        var company = mock(com.startupvalidationbot.radar.RadarDomain.Company.class);
        List<com.startupvalidationbot.radar.RadarDomain.Company> companies = List.of(company);
        var identity = new OfferingStore.StoredIdentity(9L, "Bloomy, Inc.", null);
        Match likely = new Match(17L, MatchStatus.LIKELY, 85,
                "Unique exact name without corroborating domain. Manual verification required.");
        when(source.fetchRecent()).thenReturn(List.of());
        when(store.baselineDue(anyInt())).thenReturn(false);
        when(store.listStoredIdentities()).thenReturn(List.of(identity));
        when(radar.listCompanies()).thenReturn(companies);
        when(matcher.match("Bloomy, Inc.", null, companies)).thenReturn(likely);

        new OfferingDiscoveryService(source, matcher, store, radar, 30).discover();

        verify(store).updateMatch(9L, likely);
    }

    @Test
    void revisitsStoredLikelyMatchAndConfirmsAfterSecEnrichmentAddsFiledDomain() {
        OfferingSourceAdapter source = mock(OfferingSourceAdapter.class);
        OfferingMatchService matcher = mock(OfferingMatchService.class);
        OfferingStore store = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        var company = mock(com.startupvalidationbot.radar.RadarDomain.Company.class);
        List<com.startupvalidationbot.radar.RadarDomain.Company> companies = List.of(company);
        Candidate before = candidate("Bloomy, Inc.", "bloomy-accession");
        Candidate enriched = new Candidate(before.issuerName(), before.issuerCik(), "https://joinbloomy.com",
                before.platform(), before.intermediaryName(), before.intermediaryCik(), before.offeringUrl(),
                before.secFilingUrl(), before.accessionNumber(), before.fileNumber(), before.filingType(),
                before.filingDate(), before.securityType(), before.minimumInvestment(), before.targetAmount(),
                before.maximumAmount(), before.valuationOrCap(), before.deadline(), before.amountRaised(),
                before.source(), Map.of("issuerWebsite", "https://joinbloomy.com"));
        Match confirmed = new Match(17L, MatchStatus.CONFIRMED, 100, "Exact name and filed domain match.");
        var storedOffering = mock(OfferingDomain.Offering.class);
        when(storedOffering.id()).thenReturn(9L);
        when(source.fetchRecent()).thenReturn(List.of());
        when(store.baselineDue(anyInt())).thenReturn(false);
        when(store.listStoredIdentities()).thenReturn(List.of());
        when(store.listStoredCandidatesForResolution(20))
                .thenReturn(List.of(new OfferingStore.StoredCandidate(9L, before)));
        when(radar.listCompanies()).thenReturn(companies);
        when(source.enrich(before)).thenReturn(enriched);
        when(matcher.match(enriched, companies)).thenReturn(confirmed);
        when(store.upsert(enriched, confirmed)).thenReturn(new OfferingStore.UpsertResult(storedOffering, false));

        new OfferingDiscoveryService(source, matcher, store, radar, 30).discover();

        verify(store).markResolution(9L, "CONFIRMED", confirmed.reason(),
                List.of("STORED_SEC_FACTS", "SEC_FILING"));
    }

    private static Candidate candidate(String name, String accession) {
        return new Candidate(name, "0000000001", null, "UNKNOWN", null, null, null,
                "https://www.sec.gov/Archives/example", accession, null, "C", LocalDate.now(), null,
                null, null, null, null, null, null, "SEC_EDGAR_RECENT_INDEX", Map.of());
    }
}
