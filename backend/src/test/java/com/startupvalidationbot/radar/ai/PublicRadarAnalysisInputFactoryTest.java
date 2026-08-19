package com.startupvalidationbot.radar.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.radar.RadarDomain.Company;
import com.startupvalidationbot.radar.RadarDomain.ResearchSource;
import com.startupvalidationbot.radar.RadarStore;

class PublicRadarAnalysisInputFactoryTest {
    @Test
    void excludesManualAndPrivateDiligenceTextFromProviderBoundary() {
        RadarStore store = mock(RadarStore.class);
        Company company = new Company(12L, "Boundary Labs", "boundary.test", "https://boundary.test",
                "Manually pasted Form C and private user notes", "Software", List.of("Automation"), null, 2025,
                List.of(), 0, 0, "", 2, LocalDateTime.now(), LocalDateTime.now(), false, false);
        when(store.listResearchSources(12L)).thenReturn(List.of(
                new ResearchSource(1L, "MANUAL", "Private diligence", "https://example.com/manual",
                        LocalDateTime.now(), "Deal Scout pasted text: SSN, bank info, private note", true),
                new ResearchSource(2L, "RSS", "Public launch", "https://news.example.com/launch",
                        LocalDateTime.now(), "Boundary Labs launched an automation product for developers.", true),
                new ResearchSource(3L, "HACKER_NEWS", "Show HN: Boundary Labs",
                        "https://news.ycombinator.com/item?id=123",
                        LocalDateTime.now(), "Boundary Labs now serves 20 developer teams.", true)));

        PublicCompanyAnalysisInput input = new PublicRadarAnalysisInputFactory(store).create(company);
        String payloadText = GroqRadarAiProvider.publicPayload(input).toString();

        assertThat(input.publicDescription()).contains("launched an automation product");
        assertThat(input.sources()).extracting(PublicCompanyAnalysisInput.PublicSourceEvidence::sourceType)
                .containsExactly("RSS", "HACKER_NEWS");
        assertThat(payloadText)
                .doesNotContain("Form C", "private user notes", "SSN", "bank info", "Deal Scout pasted text");
    }
}
