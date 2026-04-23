package com.startupvalidationbot.dto;

import com.startupvalidationbot.enums.DealStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DecisionResponse {
    private Long id;
    private DealStatus status;
    private String rationale;
    private String whatWouldChangeMyMind;
    private String nextMilestoneNeeded;
}