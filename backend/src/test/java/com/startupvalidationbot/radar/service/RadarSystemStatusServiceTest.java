package com.startupvalidationbot.radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.diligence.notification.ResendDiligenceEmailSender;
import com.startupvalidationbot.radar.RadarStore;

class RadarSystemStatusServiceTest {
    @Test void activeResendConfigurationIsReportedWithoutCredentialsOrAddresses() throws Exception {
        var status = service("private-resend-key", "Private sender <sender@example.com>", "recipient@example.com", "").status();
        assertThat(status.integrations()).containsEntry("emailDelivery", true);
        assertThat(status.emailDelivery().provider()).isEqualTo("Resend");
        assertThat(status.emailDelivery().recipientConfigured()).isTrue();
        assertThat(status.emailDelivery().fromConfigured()).isTrue();
        String output = new ObjectMapper().writeValueAsString(status);
        assertThat(output).doesNotContain("private-resend-key", "sender@example.com", "recipient@example.com", "private-worker-token");
    }
    @Test void missingKeyOrRecipientIsNotConfiguredButLegacyEndpointStillWorks() {
        assertThat(service("", "Sender", "recipient@example.com", "").status().emailDelivery().configured()).isFalse();
        assertThat(service("key", "Sender", "", "").status().emailDelivery().configured()).isFalse();
        assertThat(service("", "", "", "https://server.example/send").status().emailDelivery().provider()).isEqualTo("Server endpoint");
        assertThat(service("", "", "", "https://server.example/send").status().integrations()).containsEntry("emailDelivery", true);
    }
    private static RadarSystemStatusService service(String key, String from, String recipient, String legacy) {
        RadarStore radar = mock(RadarStore.class);
        DiligenceStore diligence = mock(DiligenceStore.class);
        when(diligence.lastEmailAttempt()).thenReturn(Map.of());
        var sender = new ResendDiligenceEmailSender("resend", key, from, "", new ObjectMapper());
        return new RadarSystemStatusService(radar, sender, diligence, recipient, false, "groq", "routine", "deep",
                "", "", "", "", "", "", legacy, "", "", "", "private-worker-token");
    }
}
