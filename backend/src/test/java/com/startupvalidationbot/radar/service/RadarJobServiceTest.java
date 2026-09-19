package com.startupvalidationbot.radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.offering.OfferingDiscoveryService;
import com.startupvalidationbot.diligence.AutonomousDiligenceService;
import com.startupvalidationbot.diligence.DiligenceDomain.RunResult;
import com.startupvalidationbot.diligence.discovery.CampaignDiscoveryDomain;
import com.startupvalidationbot.radar.RadarDomain.JobResult;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.JobStart;

class RadarJobServiceTest {
    private final RadarStore store = mock(RadarStore.class);
    private final RadarDiscoveryService discovery = mock(RadarDiscoveryService.class);

    @Test
    void skippedCandidateDiagnosticCompletesDiscoveryJob() {
        when(store.beginJob(eq("discovery"), eq("run-1"), any()))
                .thenReturn(new JobStart(true, false, "lease-1"));
        when(discovery.discoverEnabledSources()).thenReturn(new RadarDiscoveryService.DiscoveryResult(
                61, 61, 0, List.of(), List.of("Product Hunt / candidate: invalid company identity")));
        RadarJobService service = new RadarJobService(store, discovery, mock(RadarAnalysisService.class),
                mock(RadarTrendService.class), mock(RadarDigestService.class),
                mock(OfferingDiscoveryService.class), 120);

        JobResult result = service.run("discovery", "run-1", false);

        assertThat(result.ok()).isTrue();
        assertThat(result.errorCount()).isZero();
        assertThat(result.errors()).isEmpty();
        assertThat(result.diagnostics()).containsExactly("Product Hunt / candidate: invalid company identity");
        verify(store).completeJob(eq("discovery"), eq("run-1"), eq("lease-1"), eq("COMPLETED"),
                eq(result), eq(null));
    }

    @Test
    void successfulDiscoveryCompletesJobWithoutDiagnostics() {
        when(store.beginJob(eq("discovery"), eq("run-success"), any()))
                .thenReturn(new JobStart(true, false, "lease-success"));
        when(discovery.discoverEnabledSources()).thenReturn(new RadarDiscoveryService.DiscoveryResult(
                3, 2, 1, List.of(), List.of()));
        RadarJobService service = new RadarJobService(store, discovery, mock(RadarAnalysisService.class),
                mock(RadarTrendService.class), mock(RadarDigestService.class),
                mock(OfferingDiscoveryService.class), 120);

        JobResult result = service.run("discovery", "run-success", false);

        assertThat(result.ok()).isTrue();
        assertThat(result.processed()).isEqualTo(3);
        assertThat(result.errors()).isEmpty();
        verify(store).completeJob(eq("discovery"), eq("run-success"), eq("lease-success"), eq("COMPLETED"),
                eq(result), eq(null));
    }

    @Test
    void fatalDiscoveryErrorFailsJob() {
        when(store.beginJob(eq("discovery"), eq("run-2"), any()))
                .thenReturn(new JobStart(true, false, "lease-2"));
        when(discovery.discoverEnabledSources()).thenReturn(new RadarDiscoveryService.DiscoveryResult(
                0, 0, 0, List.of("Product Hunt: upstream unavailable"), List.of()));
        RadarJobService service = new RadarJobService(store, discovery, mock(RadarAnalysisService.class),
                mock(RadarTrendService.class), mock(RadarDigestService.class),
                mock(OfferingDiscoveryService.class), 120);

        JobResult result = service.run("discovery", "run-2", false);

        assertThat(result.ok()).isFalse();
        verify(store).completeJob(eq("discovery"), eq("run-2"), eq("lease-2"), eq("FAILED"), eq(result),
                eq("Product Hunt: upstream unavailable"));
    }

    @Test
    void autonomousDiligencePersistsNamedSanitizedCountersInJobDiagnostics() {
        AutonomousDiligenceService diligence = mock(AutonomousDiligenceService.class);
        when(store.beginJob(eq("autonomous-diligence"), eq("diligence-1"), any()))
                .thenReturn(new JobStart(true, false, "lease-diligence"));
        when(diligence.run()).thenReturn(new RunResult(12, 4, 3, 2, 1, 2, 1,
                1, 2, 3, 2, 1, List.of(), new CampaignDiscoveryDomain.RunResult(
                        8, 5, 2, 2, 1, 4, 1, 2, 1, 3, 1, List.of())));
        RadarJobService service = new RadarJobService(store, discovery, mock(RadarAnalysisService.class),
                mock(RadarTrendService.class), mock(RadarDigestService.class),
                mock(OfferingDiscoveryService.class), diligence, 120);

        JobResult result = service.run("autonomous-diligence", "diligence-1", false);

        assertThat(result.diagnostics()).contains("offeringsConsidered=4", "packetsReady=1",
                "packetsPartial=2", "emailsSent=2", "emailsFailed=1",
                "campaignCompaniesSearched=5", "campaignConfirmed=1", "campaignCacheHits=3");
        verify(store).completeJob(eq("autonomous-diligence"), eq("diligence-1"), eq("lease-diligence"),
                eq("COMPLETED"), eq(result), eq(null));
    }
}
