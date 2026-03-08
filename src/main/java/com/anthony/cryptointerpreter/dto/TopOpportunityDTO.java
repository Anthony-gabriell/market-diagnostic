package com.anthony.cryptointerpreter.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TopOpportunityDTO(
        String symbol,
        double opportunityScore,
        BigDecimal rsi,
        String volumeProfile,
        double riskRewardRatio,
        String volatilityLevel,
        String summary,
        LocalDateTime scannedAt
) {
}
