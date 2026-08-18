package com.startupvalidationbot.radar.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.startupvalidationbot.radar.RadarDomain.Analysis;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.CompanyDetail;
import com.startupvalidationbot.radar.RadarRequests.ManualDiscovery;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.service.RadarDigestService.DigestResult;

@Service
public class RadarDemoFixtureService {
    private final RadarStore store;
    private final RadarDiscoveryService discovery;
    private final RadarAnalysisService analysis;
    private final RadarTrendService trends;
    private final RadarDigestService digest;
    private final boolean enabled;

    public RadarDemoFixtureService(RadarStore store, RadarDiscoveryService discovery,
            RadarAnalysisService analysis, RadarTrendService trends, RadarDigestService digest,
            @Value("${radar.demo-fixture.enabled:false}") boolean enabled) {
        this.store = store;
        this.discovery = discovery;
        this.analysis = analysis;
        this.trends = trends;
        this.digest = digest;
        this.enabled = enabled;
    }

    public DemoFixtureResult seed() {
        if (!enabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Synthetic Radar fixture is disabled");
        }
        Company first = discovery.ingestManual(new ManualDiscovery("Synthetic Grid Labs",
                "https://synthetic-grid.example/platform",
                "Developer software that automates grid interconnection research for energy teams.",
                "Energy Software", List.of("Energy", "Developer Tools", "Automation"), "New York", 2025,
                "https://synthetic-grid.example/launch", "synthetic-grid-launch-v1", null));
        Company duplicate = discovery.ingestManual(new ManualDiscovery("Synthetic Grid Labs, Inc.",
                "https://synthetic-grid.example",
                "The public launch now reports two utility pilots and an expanded interconnection data product.",
                "Energy Software", List.of("Energy", "Developer Tools", "Automation"), "New York", 2025,
                "https://synthetic-grid.example/update", "synthetic-grid-update-v2", null));
        Company second = discovery.ingestManual(new ManualDiscovery("Synthetic Workflow Systems",
                "https://synthetic-workflow.example",
                "Enterprise automation software for reviewing infrastructure projects.",
                "Enterprise Software", List.of("Automation", "Developer Tools"), "Boston", 2024,
                "https://synthetic-workflow.example/launch", "synthetic-workflow-launch-v1", null));

        store.watchCompany(first.id(), "Synthetic watchlist fixture", null);
        Analysis deepDive = analysis.analyze(store.findCompany(first.id()).orElseThrow(), "DEEP_DIVE");
        int trendCount = trends.rebuild();
        DigestResult digestResult = digest.generateAndMaybeSend(false);
        CompanyDetail detail = store.getCompanyDetail(first.id());
        Company refreshed = detail.company();
        return new DemoFixtureResult(first.id(), duplicate.id(), second.id(), first.id().equals(duplicate.id()),
                detail.snapshots().size(), refreshed.radarScore(), refreshed.personalScore(), refreshed.watched(),
                deepDive.analysisType(), deepDive.analysisOrigin(), trendCount, digestResult.periodKey(),
                digestResult.ok() && !digestResult.sent());
    }

    public record DemoFixtureResult(long primaryCompanyId, long duplicateCompanyId, long secondCompanyId,
            boolean deduplicated, int snapshotCount, int radarScore, int personalScore, boolean watched,
            String analysisType, String analysisOrigin, int trendCount, String digestPeriodKey,
            boolean digestPreviewGenerated) {
    }
}
