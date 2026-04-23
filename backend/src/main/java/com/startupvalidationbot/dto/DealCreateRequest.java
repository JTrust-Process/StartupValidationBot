package com.startupvalidationbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DealCreateRequest {

    @NotBlank
    private String companyName;

    @NotBlank
    private String sector;

    @NotBlank
    private String platform;

    @NotBlank
    private String roundType;

    @NotBlank
    private String shortDescription;

    private BigDecimal valuation;
    private BigDecimal minimumInvestment;
}