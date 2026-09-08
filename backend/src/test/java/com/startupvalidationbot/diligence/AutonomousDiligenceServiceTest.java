package com.startupvalidationbot.diligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.diligence.DiligenceDomain.FinancialPeriod;
import com.startupvalidationbot.diligence.DiligenceDomain.Packet;
import com.startupvalidationbot.diligence.DiligenceStore.PacketDraft;
import com.startupvalidationbot.diligence.notification.DiligenceNotificationService;
import com.startupvalidationbot.diligence.notification.DiligenceNotificationService.SendCounts;
import com.startupvalidationbot.diligence.platform.PlatformOfferingEnricher;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.Status;
import com.startupvalidationbot.offering.OfferingStore;
import com.startupvalidationbot.offering.intake.NativeOfferingDomain;
import com.startupvalidationbot.offering.intake.NativeOfferingIntakeService;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.CompanyDetail;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.service.RadarQueryService;

class AutonomousDiligenceServiceTest {
    @Test
    void platformFailureProducesPartialPacketWithoutFailingSecBackedJob() {
        DiligenceStore store = mock(DiligenceStore.class);
        OfferingStore offerings = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        RadarQueryService queries = mock(RadarQueryService.class);
        SecFinancialExtractor financials = mock(SecFinancialExtractor.class);
        EvidenceReconciler reconciler = mock(EvidenceReconciler.class);
        DiligenceNotificationService notifications = mock(DiligenceNotificationService.class);
        PlatformOfferingEnricher platform = mock(PlatformOfferingEnricher.class);
        Offering offering = offering();
        Company company = mock(Company.class);
        Packet packet = mock(Packet.class);
        when(company.id()).thenReturn(7L);
        when(radar.listCompanies()).thenReturn(List.of(company));
        when(offerings.list(null, null, null, null)).thenReturn(List.of(offering));
        when(store.eligibleOfferingIds(25)).thenReturn(List.of(1L));
        when(offerings.find(1L)).thenReturn(Optional.of(offering));
        when(offerings.facts(1L)).thenReturn(Map.of("REVENUEMOSTRECENTFISCALYEAR", "1000"));
        when(store.findCampaign(1L)).thenReturn(Optional.empty());
        when(platform.platform()).thenReturn("WEFUNDER");
        when(platform.supports(URI.create("https://wefunder.com/acme"))).thenReturn(true);
        when(platform.enrich(any(), any())).thenThrow(new IllegalStateException("upstream unavailable"));
        when(financials.extract(any(), any())).thenReturn(List.of(new FinancialPeriod("2025",
                new BigDecimal("1000"), null, null, null, null, null, null, null, null,
                "accession", "https://www.sec.gov/example")));
        when(reconciler.reconcile(any(), any())).thenReturn(List.of());
        when(queries.detail(7L)).thenReturn(new CompanyDetail(company, null, List.of(), List.of(), null, null));
        when(store.savePacket(any(PacketDraft.class))).thenReturn(packet);
        when(notifications.sendPending()).thenReturn(new SendCounts(0, 0));

        var result = new AutonomousDiligenceService(store, offerings, radar, queries, financials, reconciler,
                notifications, List.of(platform), 25).run();

        assertThat(result.packetsPartial()).isEqualTo(1);
        assertThat(result.platformErrors()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verify(store).markPlatform("WEFUNDER", "DEGRADED", 1, 0, "upstream unavailable");
    }

    @Test
    void nativeIntakeRunsBeforeExistingPacketPipelineAndStoredCampaignRemainsUsable() {
        DiligenceStore store = mock(DiligenceStore.class);
        OfferingStore offerings = mock(OfferingStore.class);
        RadarStore radar = mock(RadarStore.class);
        RadarQueryService queries = mock(RadarQueryService.class);
        SecFinancialExtractor financials = mock(SecFinancialExtractor.class);
        EvidenceReconciler reconciler = mock(EvidenceReconciler.class);
        DiligenceNotificationService notifications = mock(DiligenceNotificationService.class);
        NativeOfferingIntakeService nativeIntake = mock(NativeOfferingIntakeService.class);
        Offering offering = offering();
        Company company = mock(Company.class);
        Packet packet = mock(Packet.class);
        DiligenceDomain.PlatformCampaign campaign = mock(DiligenceDomain.PlatformCampaign.class);
        when(nativeIntake.run()).thenReturn(new NativeOfferingDomain.RunResult(1, 1, 1, 0, 1, 0,
                0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of()));
        when(company.id()).thenReturn(7L);
        when(radar.listCompanies()).thenReturn(List.of(company));
        when(offerings.list(null, null, null, null)).thenReturn(List.of(offering));
        when(store.eligibleOfferingIds(25)).thenReturn(List.of(1L));
        when(offerings.find(1L)).thenReturn(Optional.of(offering));
        when(offerings.facts(1L)).thenReturn(Map.of("REVENUEMOSTRECENTFISCALYEAR", "1000"));
        when(store.findCampaign(1L)).thenReturn(Optional.of(campaign));
        when(campaign.platform()).thenReturn("REPUBLIC");
        when(campaign.facts()).thenReturn(Map.of("amountRaised", "640000"));
        when(campaign.sourceFingerprint()).thenReturn("native-campaign");
        when(financials.extract(any(), any())).thenReturn(List.of(new FinancialPeriod("2025",
                new BigDecimal("1000"), null, null, null, null, null, null, null, null,
                "accession", "https://www.sec.gov/example")));
        when(reconciler.reconcile(any(), any())).thenReturn(List.of());
        when(queries.detail(7L)).thenReturn(new CompanyDetail(company, null, List.of(), List.of(), null, null));
        when(store.savePacket(any(PacketDraft.class))).thenReturn(packet);
        when(packet.status()).thenReturn(DiligenceDomain.PacketStatus.READY);
        when(notifications.sendPending()).thenReturn(new SendCounts(0, 0));
        when(store.actionablePacketCount()).thenReturn(1);

        var result = new AutonomousDiligenceService(store, offerings, radar, queries, financials, reconciler,
                notifications, List.of(), null, nativeIntake, 25).run();

        verify(nativeIntake).run();
        verify(store).savePacket(any(PacketDraft.class));
        assertThat(result.packetsReady()).isEqualTo(1);
        assertThat(result.reviewQueueAfter()).isEqualTo(1);
    }

    private static Offering offering() {
        LocalDateTime now = LocalDateTime.now();
        return new Offering(1, 7L, "Acme", "Acme, Inc.", "0001234567", "Wefunder", "Wefunder Portal LLC",
                "https://wefunder.com/acme", "https://www.sec.gov/example", "accession", "020-1", "C",
                LocalDate.now(), "REG_CF", "Crowd SAFE", new BigDecimal("100"),
                new BigDecimal("100000"), new BigDecimal("1235000"), "$12M cap",
                LocalDate.now().plusDays(30), null, Status.ACTIVE, "TEST",
                MatchStatus.CONFIRMED, 100, "Matched", now, now);
    }
}
