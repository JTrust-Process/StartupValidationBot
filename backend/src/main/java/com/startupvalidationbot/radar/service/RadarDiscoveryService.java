package com.startupvalidationbot.radar.service;

import static com.startupvalidationbot.radar.RadarDomain.*;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.startupvalidationbot.radar.ContentHash;
import com.startupvalidationbot.radar.RadarRequests.ManualDiscovery;
import com.startupvalidationbot.radar.RadarIntelStore;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarStore.CompanyUpsert;
import com.startupvalidationbot.radar.RadarStore.DiscoverySaveResult;
import com.startupvalidationbot.radar.ai.RadarAiRunBudget;
import com.startupvalidationbot.radar.source.SourceFetchException;
import com.startupvalidationbot.radar.source.StartupSourceAdapter;

@Service
public class RadarDiscoveryService {
    private final RadarStore store;
    private final RadarIntelStore intelStore;
    private final RadarAnalysisService analysisService;
    private final List<StartupSourceAdapter> adapters;
    private final int maxPerSource;

    public RadarDiscoveryService(RadarStore store, RadarIntelStore intelStore,
            RadarAnalysisService analysisService, List<StartupSourceAdapter> adapters,
            @Value("${radar.discovery-max-per-source:30}") int maxPerSource) {
        this.store = store;
        this.intelStore = intelStore;
        this.analysisService = analysisService;
        this.adapters = adapters;
        this.maxPerSource = Math.max(1, Math.min(maxPerSource, 100));
    }

    public DiscoveryResult discoverEnabledSources() {
        int processed = 0;
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();
        Map<Long, QueuedAnalysis> analysisQueue = new LinkedHashMap<>();

        for (Source source : store.listSources().stream().filter(Source::enabled).toList()) {
            StartupSourceAdapter adapter = adapters.stream()
                    .filter(candidate -> candidate.supports(source.sourceType()))
                    .findFirst().orElse(null);
            if (adapter == null) {
                String error = "No adapter for source type " + source.sourceType();
                errors.add(source.name() + ": " + error);
                store.markSource(source, "UNSUPPORTED", error);
                continue;
            }
            if ("MANUAL".equalsIgnoreCase(source.sourceType())
                    || "YC_DIRECTORY".equalsIgnoreCase(source.sourceType())) {
                store.markSource(source, "MANUAL_ONLY", null);
                continue;
            }
            try {
                List<Candidate> candidates = adapter.discover(source, maxPerSource);
                for (Candidate candidate : candidates) {
                    try {
                        IngestResult result = ingest(source, candidate);
                        CompanyUpsert upsert = result.upsert();
                        processed++;
                        if (upsert.created()) {
                            created++;
                        } else {
                            updated++;
                        }
                        QueuedAnalysis queued = new QueuedAnalysis(result.company(), upsert.created(),
                                result.discovery().snapshotCreated());
                        analysisQueue.merge(result.company().id(), queued,
                                (previous, current) -> previous.priority() >= current.priority() ? previous : current);
                    } catch (RuntimeException error) {
                        errors.add(source.name() + " / " + candidate.companyName() + ": " + error.getMessage());
                    }
                }
                store.markSource(source, "OK", null);
            } catch (SourceFetchException error) {
                errors.add(source.name() + ": " + error.getMessage());
                store.markSource(source, "ERROR", error.getMessage());
            }
        }
        RadarAiRunBudget budget = analysisService.newRunBudget();
        analysisQueue.values().stream().sorted(Comparator.comparingInt(QueuedAnalysis::priority).reversed()
                .thenComparing(item -> item.company().id())).forEach(item -> {
                    try {
                        analysisService.analyze(item.company(), "RADAR", budget);
                    } catch (RuntimeException error) {
                        errors.add(item.company().name() + " analysis: " + error.getMessage());
                    }
                });
        return new DiscoveryResult(processed, created, updated, errors);
    }

    public Company ingestManual(ManualDiscovery request) {
        if (request.foundedYear() != null
                && (request.foundedYear() < 1800 || request.foundedYear() > Year.now().getValue() + 1)) {
            throw new IllegalArgumentException("foundedYear must be between 1800 and next year");
        }
        Source source = store.findSource("manual")
                .orElseGet(() -> store.upsertSource("manual", "MANUAL", "Manual startup discovery", null, true));
        String rawText = request.rawText() == null || request.rawText().isBlank()
                ? String.join("\n", request.companyName(), value(request.description()))
                : request.rawText();
        Candidate candidate = new Candidate(source.sourceKey(),
                defaultExternalId(request.externalId(), request.companyName(), request.sourceUrl()),
                request.companyName(), request.websiteUrl(), request.description(),
                request.sector() == null || request.sector().isBlank() ? "Unknown" : request.sector(),
                request.categories() == null ? List.of() : request.categories(), request.headquarters(),
                request.foundedYear(), request.sourceUrl(), LocalDateTime.now(), rawText);
        IngestResult result = ingest(source, candidate);
        analysisService.analyze(result.company(), "RADAR", analysisService.newRunBudget());
        return store.findCompany(result.company().id()).orElseThrow();
    }

    private IngestResult ingest(Source source, Candidate candidate) {
        CompanyUpsert upsert = store.upsertCompany(candidate);
        DiscoverySaveResult discovery = store.saveDiscoveryAndSnapshot(upsert.company().id(), source, candidate);
        // Persist tiered changes so the watchlist and Radar Home can rank by what actually matters.
        intelStore.recordChanges(upsert.company().id(), discovery.snapshotId(), discovery.changes());
        store.saveResearchSource(upsert.company().id(), source.sourceType(), source.name(), candidate.sourceUrl(),
                candidate.description(), true);
        Company refreshed = store.findCompany(upsert.company().id()).orElseThrow();
        return new IngestResult(upsert, refreshed, discovery);
    }

    private static String defaultExternalId(String requested, String companyName, String sourceUrl) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return ContentHash.sha256(companyName + "|" + value(sourceUrl)).substring(0, 24);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public record DiscoveryResult(int processed, int created, int updated, List<String> errors) {
    }

    private record IngestResult(CompanyUpsert upsert, Company company, DiscoverySaveResult discovery) {
    }

    private record QueuedAnalysis(Company company, boolean created, boolean changed) {
        int priority() {
            if (created) return 3;
            if (company.watched() && changed) return 2;
            return changed ? 1 : 0;
        }
    }
}
