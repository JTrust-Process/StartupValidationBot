package com.startupvalidationbot.radar.ai;

import java.util.Comparator;
import java.util.List;

import com.startupvalidationbot.radar.ContentHash;

/**
 * Deliberately narrow DTO for external analysis providers. Deal Scout and private
 * diligence types must never be added to this boundary.
 */
public record PublicCompanyAnalysisInput(
        long companyId,
        String companyName,
        String domain,
        String websiteUrl,
        String publicDescription,
        String sector,
        List<String> categories,
        String headquarters,
        Integer foundedYear,
        String acceleratorBatch,
        List<String> publicLaunchInformation,
        List<String> publicFundingInformation,
        List<String> publicInvestorInformation,
        List<String> publicTractionInformation,
        List<PublicSourceEvidence> sources,
        int sourceCount) {

    public PublicCompanyAnalysisInput {
        categories = sorted(categories);
        publicLaunchInformation = sorted(publicLaunchInformation);
        publicFundingInformation = sorted(publicFundingInformation);
        publicInvestorInformation = sorted(publicInvestorInformation);
        publicTractionInformation = sorted(publicTractionInformation);
        sources = sources == null ? List.of() : sources.stream()
                .sorted(Comparator.comparing(PublicSourceEvidence::sourceUrl,
                        Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(PublicSourceEvidence::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public String stableHash(String provider, String model, String promptVersion, String schemaVersion) {
        String sourceText = sources.stream()
                .map(source -> value(source.sourceType()) + "|" + value(source.title()) + "|"
                        + value(source.sourceUrl()) + "|" + value(source.publicExcerpt()))
                .reduce("", (left, right) -> left + "\n" + right);
        return ContentHash.sha256(String.join("\n",
                value(provider), value(model), value(promptVersion), value(schemaVersion),
                value(companyName), value(domain), value(websiteUrl), value(publicDescription), value(sector),
                String.join("|", categories), value(headquarters), String.valueOf(foundedYear),
                value(acceleratorBatch), String.join("|", publicLaunchInformation),
                String.join("|", publicFundingInformation), String.join("|", publicInvestorInformation),
                String.join("|", publicTractionInformation), sourceText, String.valueOf(sourceCount)));
    }

    public List<String> suppliedSourceUrls() {
        return sources.stream().map(PublicSourceEvidence::sourceUrl)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
    }

    private static List<String> sorted(List<String> values) {
        return values == null ? List.of() : values.stream().filter(value -> value != null && !value.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    public record PublicSourceEvidence(String sourceType, String title, String sourceUrl, String publicExcerpt) {
    }
}
