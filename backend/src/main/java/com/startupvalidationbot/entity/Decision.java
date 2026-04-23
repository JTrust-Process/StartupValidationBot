package com.startupvalidationbot.entity;

import com.startupvalidationbot.enums.DealStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "decisions")
@Getter
@Setter
@NoArgsConstructor
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStatus status;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rationale;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String whatWouldChangeMyMind;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String nextMilestoneNeeded;

    @OneToOne
    @JoinColumn(name = "deal_id", nullable = false, unique = true)
    private Deal deal;
}