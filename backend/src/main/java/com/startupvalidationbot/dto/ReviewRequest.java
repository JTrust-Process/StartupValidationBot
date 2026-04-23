package com.startupvalidationbot.dto;

import java.time.LocalDate;

import com.startupvalidationbot.enums.ThesisDirection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewRequest {

    @NotNull
    private LocalDate nextReviewDate;

    @NotBlank
    private String reviewNote;

    @NotNull
    private ThesisDirection thesisDirection;
}