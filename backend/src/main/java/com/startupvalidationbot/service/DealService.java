package com.startupvalidationbot.service;

import com.startupvalidationbot.dto.*;
import com.startupvalidationbot.entity.*;
import com.startupvalidationbot.enums.DealStatus;
import com.startupvalidationbot.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DealService {

    private final DealRepository dealRepository;
    private final QuickScreenRepository quickScreenRepository;
    private final DecisionRepository decisionRepository;
    private final DeepDiligenceRepository deepDiligenceRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public List<DealResponse> getAllDeals() {
        return dealRepository.findAll()
                .stream()
                .map(this::mapDealToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DealResponse getDealById(Long dealId) {
        Deal deal = findDealOrThrow(dealId);
        return mapDealToResponse(deal);
    }

    public DealResponse createDeal(DealCreateRequest request) {
        Deal deal = new Deal();
        deal.setCompanyName(request.getCompanyName());
        deal.setSector(request.getSector());
        deal.setPlatform(request.getPlatform());
        deal.setRoundType(request.getRoundType());
        deal.setShortDescription(request.getShortDescription());
        deal.setValuation(request.getValuation());
        deal.setMinimumInvestment(request.getMinimumInvestment());
        deal.setStatus(DealStatus.NEW);
        deal.setQuickScore(0);
        deal.setDeepScore(null);

        Deal savedDeal = dealRepository.save(deal);
        return mapDealToResponse(savedDeal);
    }

    public DealResponse saveQuickScreen(Long dealId, QuickScreenRequest request) {
        Deal deal = findDealOrThrow(dealId);

        int total = request.getBusinessClarity()
                + request.getTractionEvidence()
                + request.getEdge()
                + request.getPriceSanity()
                + request.getTrustTransparency();

        QuickScreen quickScreen = quickScreenRepository.findByDealId(dealId)
                .orElseGet(QuickScreen::new);

        quickScreen.setDeal(deal);
        quickScreen.setBusinessClarity(request.getBusinessClarity());
        quickScreen.setTractionEvidence(request.getTractionEvidence());
        quickScreen.setEdge(request.getEdge());
        quickScreen.setPriceSanity(request.getPriceSanity());
        quickScreen.setTrustTransparency(request.getTrustTransparency());
        quickScreen.setTotal(total);
        quickScreen.setWhatIsIt(request.getWhatIsIt());
        quickScreen.setWhyMightItWin(request.getWhyMightItWin());
        quickScreen.setBestProofPoint(request.getBestProofPoint());
        quickScreen.setBiggestDoubt(request.getBiggestDoubt());
        quickScreen.setWhySpendingTime(request.getWhySpendingTime());

        QuickScreen savedQuickScreen = quickScreenRepository.save(quickScreen);

        deal.setQuickScore(total);
        deal.setQuickScreen(savedQuickScreen);
        Deal savedDeal = dealRepository.save(deal);

        return mapDealToResponse(savedDeal);
    }

    public DealResponse saveDecision(Long dealId, DecisionRequest request) {
        Deal deal = findDealOrThrow(dealId);

        if (request.getStatus() == DealStatus.NEW) {
            throw new IllegalArgumentException("Decision status cannot be NEW.");
        }

        Decision decision = decisionRepository.findByDealId(dealId)
                .orElseGet(Decision::new);

        decision.setDeal(deal);
        decision.setStatus(request.getStatus());
        decision.setRationale(request.getRationale());
        decision.setWhatWouldChangeMyMind(request.getWhatWouldChangeMyMind());
        decision.setNextMilestoneNeeded(request.getNextMilestoneNeeded());

        Decision savedDecision = decisionRepository.save(decision);

        deal.setStatus(request.getStatus());
        deal.setDecision(savedDecision);
        Deal savedDeal = dealRepository.save(deal);

        return mapDealToResponse(savedDeal);
    }

    public DealResponse saveDeepDiligence(Long dealId, DeepDiligenceRequest request) {
        Deal deal = findDealOrThrow(dealId);

        int total = request.getBusinessModelScore()
                + request.getMarketCustomerScore()
                + request.getTractionQualityScore()
                + request.getCompetitiveEdgeScore()
                + request.getRiskScore();

        DeepDiligence deepDiligence = deepDiligenceRepository.findByDealId(dealId)
                .orElseGet(DeepDiligence::new);

        deepDiligence.setDeal(deal);
        deepDiligence.setBusinessModelScore(request.getBusinessModelScore());
        deepDiligence.setBusinessModelNote(request.getBusinessModelNote());
        deepDiligence.setMarketCustomerScore(request.getMarketCustomerScore());
        deepDiligence.setMarketCustomerNote(request.getMarketCustomerNote());
        deepDiligence.setTractionQualityScore(request.getTractionQualityScore());
        deepDiligence.setTractionQualityNote(request.getTractionQualityNote());
        deepDiligence.setCompetitiveEdgeScore(request.getCompetitiveEdgeScore());
        deepDiligence.setCompetitiveEdgeNote(request.getCompetitiveEdgeNote());
        deepDiligence.setRiskScore(request.getRiskScore());
        deepDiligence.setRiskNote(request.getRiskNote());
        deepDiligence.setTotal(total);

        DeepDiligence savedDeepDiligence = deepDiligenceRepository.save(deepDiligence);

        deal.setDeepScore(total);
        deal.setDeepDiligence(savedDeepDiligence);
        Deal savedDeal = dealRepository.save(deal);

        return mapDealToResponse(savedDeal);
    }

    public DealResponse saveReview(Long dealId, ReviewRequest request) {
        Deal deal = findDealOrThrow(dealId);

        Review review = reviewRepository.findByDealId(dealId)
                .orElseGet(Review::new);

        review.setDeal(deal);
        review.setNextReviewDate(request.getNextReviewDate());
        review.setReviewNote(request.getReviewNote());
        review.setThesisDirection(request.getThesisDirection());

        Review savedReview = reviewRepository.save(review);

        deal.setReview(savedReview);
        Deal savedDeal = dealRepository.save(deal);

        return mapDealToResponse(savedDeal);
    }

    private Deal findDealOrThrow(Long dealId) {
        return dealRepository.findById(dealId)
                .orElseThrow(() -> new EntityNotFoundException("Deal not found with id: " + dealId));
    }

    private DealResponse mapDealToResponse(Deal deal) {
        return DealResponse.builder()
                .id(deal.getId())
                .companyName(deal.getCompanyName())
                .sector(deal.getSector())
                .platform(deal.getPlatform())
                .roundType(deal.getRoundType())
                .shortDescription(deal.getShortDescription())
                .valuation(deal.getValuation())
                .minimumInvestment(deal.getMinimumInvestment())
                .status(deal.getStatus())
                .quickScore(deal.getQuickScore())
                .deepScore(deal.getDeepScore())
                .createdAt(deal.getCreatedAt())
                .updatedAt(deal.getUpdatedAt())
                .quickScreen(mapQuickScreenToResponse(deal.getQuickScreen()))
                .decision(mapDecisionToResponse(deal.getDecision()))
                .deepDiligence(mapDeepDiligenceToResponse(deal.getDeepDiligence()))
                .review(mapReviewToResponse(deal.getReview()))
                .build();
    }

    private QuickScreenResponse mapQuickScreenToResponse(QuickScreen quickScreen) {
        if (quickScreen == null) return null;

        return QuickScreenResponse.builder()
                .id(quickScreen.getId())
                .businessClarity(quickScreen.getBusinessClarity())
                .tractionEvidence(quickScreen.getTractionEvidence())
                .edge(quickScreen.getEdge())
                .priceSanity(quickScreen.getPriceSanity())
                .trustTransparency(quickScreen.getTrustTransparency())
                .total(quickScreen.getTotal())
                .whatIsIt(quickScreen.getWhatIsIt())
                .whyMightItWin(quickScreen.getWhyMightItWin())
                .bestProofPoint(quickScreen.getBestProofPoint())
                .biggestDoubt(quickScreen.getBiggestDoubt())
                .whySpendingTime(quickScreen.getWhySpendingTime())
                .build();
    }

    private DecisionResponse mapDecisionToResponse(Decision decision) {
        if (decision == null) return null;

        return DecisionResponse.builder()
                .id(decision.getId())
                .status(decision.getStatus())
                .rationale(decision.getRationale())
                .whatWouldChangeMyMind(decision.getWhatWouldChangeMyMind())
                .nextMilestoneNeeded(decision.getNextMilestoneNeeded())
                .build();
    }

    private DeepDiligenceResponse mapDeepDiligenceToResponse(DeepDiligence deepDiligence) {
        if (deepDiligence == null) return null;

        return DeepDiligenceResponse.builder()
                .id(deepDiligence.getId())
                .businessModelScore(deepDiligence.getBusinessModelScore())
                .businessModelNote(deepDiligence.getBusinessModelNote())
                .marketCustomerScore(deepDiligence.getMarketCustomerScore())
                .marketCustomerNote(deepDiligence.getMarketCustomerNote())
                .tractionQualityScore(deepDiligence.getTractionQualityScore())
                .tractionQualityNote(deepDiligence.getTractionQualityNote())
                .competitiveEdgeScore(deepDiligence.getCompetitiveEdgeScore())
                .competitiveEdgeNote(deepDiligence.getCompetitiveEdgeNote())
                .riskScore(deepDiligence.getRiskScore())
                .riskNote(deepDiligence.getRiskNote())
                .total(deepDiligence.getTotal())
                .build();
    }

    private ReviewResponse mapReviewToResponse(Review review) {
        if (review == null) return null;

        return ReviewResponse.builder()
                .id(review.getId())
                .nextReviewDate(review.getNextReviewDate())
                .reviewNote(review.getReviewNote())
                .thesisDirection(review.getThesisDirection())
                .build();
    }
}