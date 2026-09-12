package com.startupvalidationbot.diligence.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.startupvalidationbot.diligence.DiligenceStore;

class DiligenceNotificationServiceTest {
    @Test
    void missingRecipientDoesNotAttemptOrConsumeQueuedEmailEvents() {
        DiligenceStore store = mock(DiligenceStore.class);
        DiligenceEmailSender sender = mock(DiligenceEmailSender.class);
        when(sender.configured()).thenReturn(true);
        DiligenceNotificationService service = new DiligenceNotificationService(store, sender, "", "https://radar.example/#/radar");

        assertThat(service.configured()).isFalse();
        assertThat(service.sendPending()).isEqualTo(new DiligenceNotificationService.SendCounts(0, 0));
        verifyNoInteractions(store);
    }
}
