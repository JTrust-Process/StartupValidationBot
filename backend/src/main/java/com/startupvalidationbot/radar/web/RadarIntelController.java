package com.startupvalidationbot.radar.web;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.startupvalidationbot.radar.RadarIntelStore;
import com.startupvalidationbot.radar.intel.ChangeSignificance.Tier;
import com.startupvalidationbot.radar.RadarIntelViews.CompanyChangeView;
import com.startupvalidationbot.radar.RadarIntelViews.InterestProfileView;
import com.startupvalidationbot.radar.RadarIntelViews.InterestView;
import com.startupvalidationbot.radar.RadarIntelViews.RadarHome;
import com.startupvalidationbot.radar.RadarIntelViews.RelevanceExplanation;
import com.startupvalidationbot.radar.RadarIntelViews.SimilarCompanyView;
import com.startupvalidationbot.radar.RadarIntelViews.TrendView;
import com.startupvalidationbot.radar.service.RadarHomeService;
import com.startupvalidationbot.radar.service.RadarInterestService;
import com.startupvalidationbot.radar.service.RadarInterestService.SaveResult;
import com.startupvalidationbot.radar.service.RadarSimilarityService;
import com.startupvalidationbot.radar.service.RadarTrendService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phase 2 intelligence endpoints.
 *
 * Kept separate from {@link RadarController} so the hardened discovery/admin surface is untouched.
 * Every route below sits under /api/radar/**, so the existing auth interceptor protects it: none of
 * these are on the public allowlist.
 */
@RestController
@RequestMapping("/api/radar")
public class RadarIntelController {
    private final RadarHomeService homeService;
    private final RadarInterestService interestService;
    private final RadarSimilarityService similarityService;
    private final RadarTrendService trendService;
    private final RadarIntelStore intelStore;

    public RadarIntelController(RadarHomeService homeService, RadarInterestService interestService,
            RadarSimilarityService similarityService, RadarTrendService trendService,
            RadarIntelStore intelStore) {
        this.homeService = homeService;
        this.interestService = interestService;
        this.similarityService = similarityService;
        this.trendService = trendService;
        this.intelStore = intelStore;
    }

    /** The daily "what should I know today" view. Reads stored data only; makes no AI calls. */
    @GetMapping("/home")
    public ResponseEntity<RadarHome> home() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(homeService.build());
    }

    @GetMapping("/trends/detailed")
    public List<TrendView> detailedTrends() {
        return trendService.listDetailed();
    }

    @GetMapping("/admin/interests")
    public InterestProfileView interests() {
        return interestService.profile();
    }

    @PutMapping("/admin/interests")
    public SaveResult saveInterests(@Valid @RequestBody InterestProfileUpdate update) {
        return interestService.save(update.interests());
    }

    /** Deterministic rescore of every company. No model call, so it is safe to run at will. */
    @PostMapping("/admin/interests/recompute")
    public RecomputeResult recompute() {
        return new RecomputeResult(interestService.recomputePersonalScores());
    }

    @GetMapping("/companies/{companyId}/relevance")
    public RelevanceExplanation relevance(@PathVariable long companyId) {
        return interestService.explain(companyId);
    }

    /**
     * Records an interaction (WATCH, IGNORE, DEEP_DIVE, VISIT) and returns the refreshed relevance.
     * Signals are stored for future personalisation; today they only nudge an explainable score.
     */
    @PostMapping("/companies/{companyId}/signals")
    public RelevanceExplanation recordSignal(@PathVariable long companyId,
            @Valid @RequestBody SignalRequest request) {
        return interestService.recordSignal(companyId, request.signalType());
    }

    @GetMapping("/companies/{companyId}/similar")
    public List<SimilarCompanyView> similar(@PathVariable long companyId,
            @RequestParam(defaultValue = "6") int limit) {
        return similarityService.similarTo(companyId, limit);
    }

    /**
     * Recent changes across the Radar, optionally restricted to watched companies. Defaults to
     * IMPORTANT and above so trivial edits never reach this feed.
     */
    @GetMapping("/changes")
    public List<CompanyChangeView> recentChanges(
            @RequestParam(defaultValue = "true") boolean watched,
            @RequestParam(defaultValue = "IMPORTANT") String minSignificance,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "50") int limit) {
        Tier tier;
        try {
            tier = Tier.valueOf(minSignificance.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            tier = Tier.IMPORTANT;
        }
        return intelStore.recentChanges(tier, days, limit, watched);
    }

    @GetMapping("/companies/{companyId}/changes")
    public List<CompanyChangeView> changes(@PathVariable long companyId,
            @RequestParam(defaultValue = "20") int limit) {
        return intelStore.changesForCompany(companyId, limit);
    }

    public record InterestProfileUpdate(@Size(max = 40) List<InterestView> interests) {
    }

    public record SignalRequest(@NotBlank @Size(max = 40) String signalType) {
    }

    public record RecomputeResult(int companiesRescored) {
    }
}
