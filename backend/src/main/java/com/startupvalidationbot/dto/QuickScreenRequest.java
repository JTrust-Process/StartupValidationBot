package com.startupvalidationbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuickScreenRequest {

    @NotNull
    @Min(0)
    @Max(2)
    private Integer businessClarity;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer tractionEvidence;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer edge;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer priceSanity;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer trustTransparency;

    @NotBlank
    private String whatIsIt;

    @NotBlank
    private String whyMightItWin;

    @NotBlank
    private String bestProofPoint;

    @NotBlank
    private String biggestDoubt;

    @NotBlank
    private String whySpendingTime;
}