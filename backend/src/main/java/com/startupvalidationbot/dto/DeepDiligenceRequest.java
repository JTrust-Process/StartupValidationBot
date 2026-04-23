package com.startupvalidationbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeepDiligenceRequest {

    @NotNull
    @Min(1)
    @Max(5)
    private Integer businessModelScore;

    @NotBlank
    private String businessModelNote;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer marketCustomerScore;

    @NotBlank
    private String marketCustomerNote;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer tractionQualityScore;

    @NotBlank
    private String tractionQualityNote;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer competitiveEdgeScore;

    @NotBlank
    private String competitiveEdgeNote;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer riskScore;

    @NotBlank
    private String riskNote;
}