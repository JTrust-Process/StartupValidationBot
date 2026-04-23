package com.startupvalidationbot.dto;

import com.startupvalidationbot.enums.DealStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DecisionRequest {

    @NotNull
    private DealStatus status;

    @NotBlank
    private String rationale;

    @NotBlank
    private String whatWouldChangeMyMind;

    @NotBlank
    private String nextMilestoneNeeded;
}