package com.startupvalidationbot.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.startupvalidationbot.dto.DealCreateRequest;
import com.startupvalidationbot.dto.DealResponse;
import com.startupvalidationbot.dto.DecisionRequest;
import com.startupvalidationbot.dto.DeepDiligenceRequest;
import com.startupvalidationbot.dto.QuickScreenRequest;
import com.startupvalidationbot.dto.ReviewRequest;
import com.startupvalidationbot.service.DealService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/deals")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class DealController {

    private final DealService dealService;

    @GetMapping
    public List<DealResponse> getAllDeals() {
        return dealService.getAllDeals();
    }

    @GetMapping("/{dealId}")
    public DealResponse getDealById(@PathVariable Long dealId) {
        return dealService.getDealById(dealId);
    }

    @PostMapping
    public DealResponse createDeal(@Valid @RequestBody DealCreateRequest request) {
        return dealService.createDeal(request);
    }

    @PutMapping("/{dealId}/quick-screen")
    public DealResponse saveQuickScreen(
            @PathVariable Long dealId,
            @Valid @RequestBody QuickScreenRequest request
    ) {
        return dealService.saveQuickScreen(dealId, request);
    }

    @PutMapping("/{dealId}/decision")
    public DealResponse saveDecision(
            @PathVariable Long dealId,
            @Valid @RequestBody DecisionRequest request
    ) {
        return dealService.saveDecision(dealId, request);
    }

    @PutMapping("/{dealId}/deep-diligence")
    public DealResponse saveDeepDiligence(
            @PathVariable Long dealId,
            @Valid @RequestBody DeepDiligenceRequest request
    ) {
        return dealService.saveDeepDiligence(dealId, request);
    }

    @PutMapping("/{dealId}/review")
    public DealResponse saveReview(
            @PathVariable Long dealId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return dealService.saveReview(dealId, request);
    }
}