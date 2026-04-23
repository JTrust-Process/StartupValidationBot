package com.startupvalidationbot.dto;

import com.startupvalidationbot.enums.ThesisDirection;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ReviewResponse {
    private Long id;
    private LocalDate nextReviewDate;
    private String reviewNote;
    private ThesisDirection thesisDirection;
}