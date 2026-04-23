package com.startupvalidationbot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuickScreenResponse {
    private Long id;
    private Integer businessClarity;
    private Integer tractionEvidence;
    private Integer edge;
    private Integer priceSanity;
    private Integer trustTransparency;
    private Integer total;
    private String whatIsIt;
    private String whyMightItWin;
    private String bestProofPoint;
    private String biggestDoubt;
    private String whySpendingTime;
}