package com.startupvalidationbot.radar.web;

import static com.startupvalidationbot.radar.RadarDomain.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.startupvalidationbot.radar.RadarRequests.JobRun;
import com.startupvalidationbot.radar.RadarRequests.ManualDiscovery;
import com.startupvalidationbot.radar.RadarRequests.SourceUpsert;
import com.startupvalidationbot.radar.RadarRequests.WatchlistUpdate;
import com.startupvalidationbot.radar.RadarAdminViews.AdminSource;
import com.startupvalidationbot.radar.RadarAdminViews.RadarExport;
import com.startupvalidationbot.radar.RadarAdminViews.SystemStatus;
import com.startupvalidationbot.radar.RadarStore;
import com.startupvalidationbot.radar.RadarViews.PublicCompany;
import com.startupvalidationbot.radar.RadarViews.PublicCompanyDetail;
import com.startupvalidationbot.radar.RadarViews.PublicSource;
import com.startupvalidationbot.radar.RadarViews.PublicTrend;
import com.startupvalidationbot.radar.service.RadarAnalysisService;
import com.startupvalidationbot.radar.service.RadarDiscoveryService;
import com.startupvalidationbot.radar.service.RadarDemoFixtureService;
import com.startupvalidationbot.radar.service.RadarDemoFixtureService.DemoFixtureResult;
import com.startupvalidationbot.radar.service.RadarJobService;
import com.startupvalidationbot.radar.service.RadarQueryService;
import com.startupvalidationbot.radar.service.RadarTrendService;
import com.startupvalidationbot.radar.service.RadarSystemStatusService;
import com.startupvalidationbot.radar.source.PublicSourceUrlPolicy;
import com.startupvalidationbot.radar.source.SourceFetchException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/radar")
public class RadarController {
    private static final Set<String> SOURCE_TYPES = Set.of("RSS", "PRODUCT_HUNT", "MANUAL", "YC_DIRECTORY",
            "HACKER_NEWS");

    private final RadarStore store;
    private final RadarQueryService queryService;
    private final RadarDiscoveryService discoveryService;
    private final RadarAnalysisService analysisService;
    private final RadarTrendService trendService;
    private final RadarJobService jobService;
    private final RadarSystemStatusService systemStatusService;
    private final RadarDemoFixtureService demoFixtureService;

    public RadarController(RadarStore store, RadarQueryService queryService,
            RadarDiscoveryService discoveryService, RadarAnalysisService analysisService,
            RadarTrendService trendService, RadarJobService jobService,
            RadarSystemStatusService systemStatusService, RadarDemoFixtureService demoFixtureService) {
        this.store = store;
        this.queryService = queryService;
        this.discoveryService = discoveryService;
        this.analysisService = analysisService;
        this.trendService = trendService;
        this.jobService = jobService;
        this.systemStatusService = systemStatusService;
        this.demoFixtureService = demoFixtureService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }

    @GetMapping("/companies")
    public List<PublicCompany> companies(@RequestParam(required = false) String search,
            @RequestParam(required = false) String sector, @RequestParam(required = false) Integer minRadar,
            @RequestParam(defaultValue = "radar") String sort) {
        if (!Set.of("radar", "newest", "updated").contains(sort)) {
            throw new IllegalArgumentException("Unsupported public Radar sort: " + sort);
        }
        return queryService.list(search, sector, minRadar, null, null, false, sort).stream()
                .map(PublicCompany::from).toList();
    }

    @GetMapping("/companies/{companyId}")
    public PublicCompanyDetail company(@PathVariable long companyId) {
        return PublicCompanyDetail.from(queryService.detail(companyId));
    }

    @GetMapping("/admin/companies")
    public List<Company> adminCompanies(@RequestParam(required = false) String search,
            @RequestParam(required = false) String sector, @RequestParam(required = false) Integer minRadar,
            @RequestParam(required = false) Integer minPersonal, @RequestParam(required = false) Boolean watched,
            @RequestParam(defaultValue = "false") Boolean includeIgnored,
            @RequestParam(defaultValue = "radar") String sort) {
        return queryService.list(search, sector, minRadar, minPersonal, watched, includeIgnored, sort);
    }

    @GetMapping("/admin/companies/{companyId}")
    public CompanyDetail adminCompany(@PathVariable long companyId) {
        return queryService.detail(companyId);
    }

    @GetMapping("/admin/sources")
    public List<AdminSource> adminSources() {
        return store.listSources().stream().map(AdminSource::from).toList();
    }

    @GetMapping("/admin/status")
    public SystemStatus systemStatus() {
        return systemStatusService.status();
    }

    @GetMapping("/admin/export")
    public ResponseEntity<RadarExport> export() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("startup-radar-export.json").build().toString())
                .body(store.exportRadar());
    }

    @PostMapping("/admin/fixtures/synthetic")
    public DemoFixtureResult seedSyntheticFixture() {
        return demoFixtureService.seed();
    }

    @PostMapping("/companies/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public Company manualDiscovery(@Valid @RequestBody ManualDiscovery request) {
        return discoveryService.ingestManual(request);
    }

    @PutMapping("/companies/{companyId}/watch")
    public CompanyDetail watch(@PathVariable long companyId, @Valid @RequestBody WatchlistUpdate request) {
        store.watchCompany(companyId, request.notes(), request.nextReviewAt());
        return queryService.detail(companyId);
    }

    @DeleteMapping("/companies/{companyId}/watch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwatch(@PathVariable long companyId) {
        store.unwatchCompany(companyId);
    }

    @PutMapping("/companies/{companyId}/ignore")
    public Company ignore(@PathVariable long companyId, @RequestParam(defaultValue = "true") boolean ignored) {
        store.ignoreCompany(companyId, ignored);
        return store.findCompany(companyId).orElseThrow();
    }

    @PostMapping("/companies/{companyId}/deep-dive")
    public Analysis deepDive(@PathVariable long companyId) {
        Company company = store.findCompany(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Radar company not found: " + companyId));
        return analysisService.analyze(company, "DEEP_DIVE");
    }

    @GetMapping("/sources")
    public List<PublicSource> sources() {
        return store.listSources().stream().map(PublicSource::from).toList();
    }

    @PostMapping("/sources")
    public Source upsertSource(@Valid @RequestBody SourceUpsert request) {
        String type = request.sourceType().trim().toUpperCase();
        if (!request.sourceKey().matches("[a-z0-9][a-z0-9-]{0,159}")) {
            throw new IllegalArgumentException("sourceKey must use lowercase letters, numbers, and hyphens");
        }
        if (!SOURCE_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported source type: " + request.sourceType());
        }
        if ("RSS".equals(type) && (request.url() == null || request.url().isBlank())) {
            throw new IllegalArgumentException("RSS sources require a URL");
        }
        if (request.url() != null && !request.url().isBlank()) {
            try {
                PublicSourceUrlPolicy.requirePublicHttpUrl(request.url());
            } catch (SourceFetchException error) {
                throw new IllegalArgumentException(error.getMessage(), error);
            }
        }
        return store.upsertSource(request.sourceKey().trim(), type, request.name().trim(), request.url(),
                request.enabled());
    }

    @GetMapping("/trends")
    public List<PublicTrend> trends() {
        return trendService.list().stream().map(PublicTrend::from).toList();
    }

    @PostMapping("/jobs/{jobType}")
    public JobResult runJob(@PathVariable String jobType, @RequestBody(required = false) JobRun request) {
        return jobService.run(jobType, request == null ? null : request.idempotencyKey(), false);
    }
}
