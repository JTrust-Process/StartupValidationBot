package com.startupvalidationbot.diligence;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.startupvalidationbot.diligence.DiligenceDomain.CompanyAvailability;
import com.startupvalidationbot.diligence.DiligenceDomain.Diagnostics;
import com.startupvalidationbot.diligence.DiligenceDomain.Packet;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/radar")
public class DiligenceController {
    private final DiligenceStore store;
    private final AutonomousDiligenceService service;
    private final com.startupvalidationbot.diligence.notification.DiligenceNotificationService notifications;

    public DiligenceController(DiligenceStore store, AutonomousDiligenceService service,
            com.startupvalidationbot.diligence.notification.DiligenceNotificationService notifications) {
        this.store = store; this.service = service; this.notifications = notifications;
    }

    @GetMapping("/diligence")
    public List<Packet> list(@RequestParam(required = false) String status, HttpServletRequest request) {
        browserOnly(request);
        if (status != null && !status.isBlank()) {
            try { com.startupvalidationbot.diligence.DiligenceDomain.PacketStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException error) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown diligence status");
            }
        }
        return store.list(status);
    }

    @GetMapping("/diligence/{id}")
    public Packet detail(@PathVariable long id, HttpServletRequest request) {
        browserOnly(request);
        return store.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diligence packet not found"));
    }

    @PostMapping("/diligence/{id}/refresh")
    public Packet refresh(@PathVariable long id, HttpServletRequest request) {
        browserOnly(request);
        if (store.find(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diligence packet not found");
        }
        return service.refresh(id);
    }

    @GetMapping("/companies/{companyId}/investment-availability")
    public CompanyAvailability availability(@PathVariable long companyId, HttpServletRequest request) {
        browserOnly(request); return store.availability(companyId);
    }

    @GetMapping("/admin/diligence")
    public Diagnostics diagnostics(HttpServletRequest request) {
        browserOnly(request); return store.diagnostics(notifications.configured());
    }

    private static void browserOnly(HttpServletRequest request) {
        if ("WORKER_TOKEN".equals(request.getAttribute("radarAuthentication"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Worker credentials are limited to Radar job execution");
        }
    }
}
