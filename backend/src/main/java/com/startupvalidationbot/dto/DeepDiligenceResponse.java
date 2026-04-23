package com.startupvalidationbot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeepDiligenceResponse {
    private Long id;
    private Integer businessModelScore;
    private String businessModelNote;
    private Integer marketCustomerScore;
    private String marketCustomerNote;
    private Integer tractionQualityScore;
    private String tractionQualityNote;
    private Integer competitiveEdgeScore;
    private String competitiveEdgeNote;
    private Integer riskScore;
    private String riskNote;
    private Integer total;
}