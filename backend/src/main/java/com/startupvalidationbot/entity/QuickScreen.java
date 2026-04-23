package com.startupvalidationbot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quick_screens")
@Getter
@Setter
@NoArgsConstructor
public class QuickScreen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer businessClarity;

    @Column(nullable = false)
    private Integer tractionEvidence;

    @Column(nullable = false)
    private Integer edge;

    @Column(nullable = false)
    private Integer priceSanity;

    @Column(nullable = false)
    private Integer trustTransparency;

    @Column(nullable = false)
    private Integer total;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String whatIsIt;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String whyMightItWin;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String bestProofPoint;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String biggestDoubt;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String whySpendingTime;

    @OneToOne
    @JoinColumn(name = "deal_id", nullable = false, unique = true)
    private Deal deal;
}