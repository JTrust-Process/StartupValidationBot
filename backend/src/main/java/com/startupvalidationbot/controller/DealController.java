package com.startupvalidationbot.controller;

import com.startupvalidationbot.dto.*;
import com.startupvalidationbot.service.DealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PutMapping("/{dealId}")
    public DealResponse updateDeal(
            @PathVariable Long dealId,
            @Valid @RequestBody DealUpdateRequest request
    ) {
        return dealService.updateDeal(dealId, request);
    }

    @DeleteMapping("/{dealId}")
    public void deleteDeal(@PathVariable Long dealId) {
        dealService.deleteDeal(dealId);
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