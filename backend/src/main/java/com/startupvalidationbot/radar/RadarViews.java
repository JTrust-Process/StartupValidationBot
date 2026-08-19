package com.startupvalidationbot.radar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.startupvalidationbot.radar.RadarDomain.Analysis;
import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.CompanyDetail;
import com.startupvalidationbot.radar.RadarDomain.ResearchSource;
import com.startupvalidationbot.radar.RadarDomain.Snapshot;
import com.startupvalidationbot.radar.RadarDomain.Source;
import com.startupvalidationbot.radar.RadarDomain.Trend;

public final class RadarViews {
    private RadarViews() {
    }

    public record PublicCompany(long id, String name, String domain, String websiteUrl, String description,
            String sector, List<String> categories, int radarScore, int sourceCount, LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt, String accelerator, String acceleratorBatch) {
        public static PublicCompany from(Company company) {
            return new PublicCompany(company.id(), company.name(), company.domain(), company.websiteUrl(),
                    company.description(), company.sector(), company.categories(), company.radarScore(),
                    company.sourceCount(), company.firstSeenAt(), company.lastSeenAt(), company.accelerator(),
                    company.acceleratorBatch());
        }
    }

    public record PublicAnalysis(String analysisType, String analysisOrigin, String provider, String model,
            String summary, String problem, String solution, String businessModel, String stage,
            List<String> founders, String fundingSummary, List<String> likelyInvestors, List<String> trendTags,
            List<String> monitoringTriggers, List<String> facts, List<String> inferences,
            List<String> whyInteresting, List<String> momentumSignals, List<String> tractionSignals,
            List<String> technicalDifferentiation, List<String> marketSignals, List<String> risks,
            List<String> bullCase, List<String> bearCase, List<String> unansweredQuestions, String whyItMatters,
            List<String> sourceUrls, String confidence, List<String> radarScoreInputs,
            Map<String, Integer> radarDimensions, int radarScore, LocalDateTime createdAt) {
        public static PublicAnalysis from(Analysis analysis) {
            if (analysis == null) return null;
            return new PublicAnalysis(analysis.analysisType(), analysis.analysisOrigin(), analysis.provider(),
                    analysis.model(), analysis.summary(), analysis.problem(), analysis.solution(),
                    analysis.businessModel(), analysis.stage(), analysis.founders(), analysis.fundingSummary(),
                    analysis.likelyInvestors(), analysis.trendTags(), analysis.monitoringTriggers(), analysis.facts(),
                    analysis.inferences(), analysis.whyInteresting(), analysis.momentumSignals(),
                    analysis.tractionSignals(), analysis.technicalDifferentiation(), analysis.marketSignals(),
                    analysis.risks(), analysis.bullCase(), analysis.bearCase(), analysis.unansweredQuestions(),
                    analysis.whyItMatters(), analysis.sourceUrls(), analysis.confidence(),
                    analysis.radarScoreInputs(), analysis.radarDimensions(), analysis.radarScore(),
                    analysis.createdAt());
        }
    }

    public record PublicSnapshot(LocalDateTime capturedAt, List<String> notableChanges) {
        public static PublicSnapshot from(Snapshot snapshot) {
            return new PublicSnapshot(snapshot.capturedAt(), snapshot.notableChanges());
        }
    }

    public record PublicResearchSource(String sourceType, String title, String url, LocalDateTime sourceDate) {
        public static PublicResearchSource from(ResearchSource source) {
            return new PublicResearchSource(source.sourceType(), source.title(), source.url(), source.sourceDate());
        }
    }

    public record PublicCompanyDetail(PublicCompany company, PublicAnalysis latestAnalysis,
            List<PublicSnapshot> snapshots, List<PublicResearchSource> researchSources) {
        public static PublicCompanyDetail from(CompanyDetail detail) {
            return new PublicCompanyDetail(PublicCompany.from(detail.company()),
                    PublicAnalysis.from(detail.latestAnalysis()),
                    detail.snapshots().stream().map(PublicSnapshot::from).toList(),
                    detail.researchSources().stream().map(PublicResearchSource::from).toList());
        }
    }

    public record PublicSource(long id, String sourceKey, String sourceType, String name, boolean enabled,
            LocalDateTime lastCheckedAt, String lastStatus, String lastError) {
        public static PublicSource from(Source source) {
            return new PublicSource(source.id(), source.sourceKey(), source.sourceType(), source.name(),
                    source.enabled(), source.lastCheckedAt(), source.lastStatus(), source.lastError());
        }
    }

    public record PublicTrend(long id, String key, String name, String summary, int companyCount, int momentumScore,
            LocalDateTime periodStart, LocalDateTime periodEnd, List<PublicCompany> companies) {
        public static PublicTrend from(Trend trend) {
            return new PublicTrend(trend.id(), trend.key(), trend.name(), trend.summary(), trend.companyCount(),
                    trend.momentumScore(), trend.periodStart(), trend.periodEnd(),
                    trend.companies().stream().map(PublicCompany::from).toList());
        }
    }
}
