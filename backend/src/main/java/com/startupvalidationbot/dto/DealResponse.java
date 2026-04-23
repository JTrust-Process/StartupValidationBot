package com.startupvalidationbot.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.startupvalidationbot.enums.DealStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DealResponse {
    private Long id;
    private String companyName;
    private String sector;
    private String platform;
    private String roundType;
    private String shortDescription;
    private BigDecimal valuation;
    private BigDecimal minimumInvestment;
    private DealStatus status;
    private Integer quickScore;
    private Integer deepScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private QuickScreenResponse quickScreen;
    private DecisionResponse decision;
    private DeepDiligenceResponse deepDiligence;
    private ReviewResponse review;
}