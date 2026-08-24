package com.startupvalidationbot.offering;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.startupvalidationbot.offering.OfferingDomain.Diagnostics;
import com.startupvalidationbot.offering.OfferingDomain.Offering;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/radar")
public class OfferingController {
    private final OfferingStore store;

    public OfferingController(OfferingStore store) {
        this.store = store;
    }

    @GetMapping("/offerings")
    public List<Offering> offerings(@RequestParam(required = false) String status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String matchStatus,
            @RequestParam(required = false) Long companyId, HttpServletRequest request) {
        browserOnly(request);
        validate(status, OfferingDomain.Status.class, "status");
        validate(matchStatus, OfferingDomain.MatchStatus.class, "matchStatus");
        if (platform != null && platform.length() > 240) {
            throw new IllegalArgumentException("platform must be 240 characters or fewer");
        }
        return store.list(status, platform, matchStatus, companyId);
    }

    @GetMapping("/offerings/{id}")
    public Offering offering(@PathVariable long id, HttpServletRequest request) {
        browserOnly(request);
        return store.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Offering not found: " + id));
    }

    @GetMapping("/companies/{companyId}/offerings")
    public List<Offering> companyOfferings(@PathVariable long companyId, HttpServletRequest request) {
        browserOnly(request);
        return store.list(null, null, null, companyId);
    }

    @GetMapping("/admin/offering-discovery")
    public Diagnostics diagnostics(HttpServletRequest request) {
        browserOnly(request);
        return store.diagnostics();
    }

    private static <E extends Enum<E>> void validate(String value, Class<E> type, String name) {
        if (value == null || value.isBlank()) return;
        try { Enum.valueOf(type, value.trim().toUpperCase()); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unsupported " + name + ": " + value); }
    }

    private static void browserOnly(HttpServletRequest request) {
        if ("WORKER_TOKEN".equals(request.getAttribute("radarAuthentication"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Worker token is limited to offering discovery job execution");
        }
    }
}
