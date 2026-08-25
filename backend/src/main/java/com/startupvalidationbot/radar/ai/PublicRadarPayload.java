package com.startupvalidationbot.radar.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/** The only application data boundary that an external Radar AI provider may receive. */
public final class PublicRadarPayload {
    private PublicRadarPayload() {
    }

    public static Map<String, Object> from(PublicCompanyAnalysisInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("companyName", value(input.companyName()));
        payload.put("domain", value(input.domain()));
        payload.put("websiteUrl", value(input.websiteUrl()));
        payload.put("publicDescription", value(input.publicDescription()));
        payload.put("sector", value(input.sector()));
        payload.put("categories", input.categories());
        payload.put("headquarters", value(input.headquarters()));
        payload.put("foundedYear", input.foundedYear() == null ? "Unknown" : input.foundedYear());
        payload.put("acceleratorBatch", value(input.acceleratorBatch()));
        payload.put("publicLaunchInformation", input.publicLaunchInformation());
        payload.put("publicFundingInformation", input.publicFundingInformation());
        payload.put("publicInvestorInformation", input.publicInvestorInformation());
        payload.put("publicTractionInformation", input.publicTractionInformation());
        payload.put("sources", input.sources());
        payload.put("sourceCount", input.sourceCount());
        return payload;
    }

    private static String value(Object value) {
        return value == null || value.toString().isBlank() ? "Unknown" : value.toString();
    }
}
