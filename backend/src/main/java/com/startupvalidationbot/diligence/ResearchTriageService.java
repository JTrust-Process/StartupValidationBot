package com.startupvalidationbot.diligence;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchTriageService {
    private final DiligenceStore store;
    private final ZoneId zone;
    public ResearchTriageService(DiligenceStore store, @Value("${radar.time-zone:America/New_York}") String timeZone) {
        this.store = store;
        this.zone = ZoneId.of(timeZone);
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ResearchTriage.Queue queue() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return ResearchTriage.queue(store.researchInputs(), now, now.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate(), zone.getId());
    }
}
