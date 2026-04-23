package com.startupvalidationbot.entity;

import com.startupvalidationbot.enums.DealStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deals")
@Getter
@Setter
@NoArgsConstructor
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false)
    private String platform;

    @Column(nullable = false)
    private String roundType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String shortDescription;

    @Column(precision = 19, scale = 2)
    private BigDecimal valuation;

    @Column(precision = 19, scale = 2)
    private BigDecimal minimumInvestment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStatus status = DealStatus.NEW;

    @Column(nullable = false)
    private Integer quickScore = 0;

    private Integer deepScore;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    private QuickScreen quickScreen;

    @OneToOne(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Decision decision;

    @OneToOne(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    private DeepDiligence deepDiligence;

    @OneToOne(mappedBy = "deal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Review review;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = DealStatus.NEW;
        }
        if (this.quickScore == null) {
            this.quickScore = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}