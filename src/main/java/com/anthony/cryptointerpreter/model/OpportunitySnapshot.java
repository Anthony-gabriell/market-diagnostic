package com.anthony.cryptointerpreter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "opportunity_snapshot")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private double opportunityScore;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rsi;

    @Column(nullable = false)
    private String volumeProfile;

    @Column(nullable = false)
    private double riskRewardRatio;

    @Column(nullable = false)
    private String volatilityLevel;

    @Column(nullable = false, length = 512)
    private String summary;

    @Column(nullable = false)
    private LocalDateTime scannedAt;
}
