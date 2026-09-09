package com.startupvalidationbot.diligence.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.startupvalidationbot.diligence.DiligenceDomain.Packet;
import com.startupvalidationbot.diligence.DiligenceStore;
import com.startupvalidationbot.offering.OfferingDomain.MatchStatus;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import com.startupvalidationbot.offering.OfferingDomain.Status;
import com.startupvalidationbot.radar.ContentHash;

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

    @Test
    void newConfirmedNotificationRequiresConfirmedAndActiveForEveryLifecycleCombination() {
        Packet packet = packet();
        for (Status status : Status.values()) {
            for (MatchStatus matchStatus : MatchStatus.values()) {
                DiligenceStore store = mock(DiligenceStore.class);
                DiligenceEmailSender sender = mock(DiligenceEmailSender.class);
                DiligenceNotificationService service = new DiligenceNotificationService(store, sender,
                        "owner@example.com", "https://radar.example/#/radar");
                boolean expected = status == Status.ACTIVE && matchStatus == MatchStatus.CONFIRMED;
                when(store.queueNotification(anyString(), anyString(), eq(42L), anyString(), anyString(),
                        anyString(), anyString(), anyString())).thenReturn(true);

                assertThat(service.queueNewConfirmed(offering(status, matchStatus), packet)).isEqualTo(expected);

                if (expected) verify(store).queueNotification(anyString(), anyString(), eq(42L), anyString(),
                        anyString(), anyString(), anyString(), anyString());
                else verifyNoInteractions(store);
            }
        }
    }

    @Test
    void activeNotificationUsesNewIdempotencyFingerprintAndQueuesOnlyOnce() {
        DiligenceStore store = mock(DiligenceStore.class);
        DiligenceEmailSender sender = mock(DiligenceEmailSender.class);
        DiligenceNotificationService service = new DiligenceNotificationService(store, sender,
                "owner@example.com", "https://radar.example/#/radar");
        String activeFingerprint = ContentHash.sha256("NEW_CONFIRMED_OFFERING|ACTIVE|42");
        String legacyFingerprint = ContentHash.sha256("NEW_CONFIRMED_OFFERING|42");
        when(store.queueNotification(eq("NEW_CONFIRMED_OFFERING"), eq("OFFERING"), eq(42L),
                eq(activeFingerprint), anyString(), anyString(), anyString(), anyString())).thenReturn(true, false);

        assertThat(service.queueNewConfirmed(offering(Status.ACTIVE, MatchStatus.CONFIRMED), packet())).isTrue();
        assertThat(service.queueNewConfirmed(offering(Status.ACTIVE, MatchStatus.CONFIRMED), packet())).isFalse();

        ArgumentCaptor<String> fingerprints = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).queueNotification(eq("NEW_CONFIRMED_OFFERING"), eq("OFFERING"), eq(42L),
                fingerprints.capture(), anyString(), anyString(), anyString(), anyString());
        assertThat(fingerprints.getAllValues()).containsOnly(activeFingerprint);
        assertThat(activeFingerprint).isNotEqualTo(legacyFingerprint);
    }

    private static Offering offering(Status status, MatchStatus matchStatus) {
        LocalDateTime now = LocalDateTime.now();
        return new Offering(42, 7L, "Acme", "Acme, Inc.", "0001234567", "Wefunder", "Wefunder Portal LLC",
                "https://wefunder.com/acme", "https://www.sec.gov/example", "accession", "020-1", "C",
                LocalDate.now(), "REG_CF", "Crowd SAFE", null, null, null, null,
                LocalDate.now().plusDays(30), null, status, "TEST", matchStatus, 100, "Matched", now, now);
    }

    private static Packet packet() {
        return new Packet(9, 7, "Acme", 42, "Wefunder", "https://wefunder.com/acme",
                "https://www.sec.gov/example", null, "CONFIRMED", 75, 90, "Summary", List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), LocalDateTime.now(),
                LocalDateTime.now(), null, "Crowd SAFE", null, null, null, null, null,
                LocalDate.now().plusDays(30), List.of(), List.of());
    }
}
