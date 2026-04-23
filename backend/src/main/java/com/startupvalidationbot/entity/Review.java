package com.startupvalidationbot.entity;

import java.time.LocalDate;

import com.startupvalidationbot.enums.ThesisDirection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate nextReviewDate;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reviewNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThesisDirection thesisDirection;

    @OneToOne
    @JoinColumn(name = "deal_id", nullable = false, unique = true)
    private Deal deal;
}