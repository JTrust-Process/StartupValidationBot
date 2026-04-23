package com.startupvalidationbot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "deep_diligence")
@Getter
@Setter
@NoArgsConstructor
public class DeepDiligence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer businessModelScore;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String businessModelNote;

    @Column(nullable = false)
    private Integer marketCustomerScore;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String marketCustomerNote;

    @Column(nullable = false)
    private Integer tractionQualityScore;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String tractionQualityNote;

    @Column(nullable = false)
    private Integer competitiveEdgeScore;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String competitiveEdgeNote;

    @Column(nullable = false)
    private Integer riskScore;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String riskNote;

    @Column(nullable = false)
    private Integer total;

    @OneToOne
    @JoinColumn(name = "deal_id", nullable = false, unique = true)
    private Deal deal;
}