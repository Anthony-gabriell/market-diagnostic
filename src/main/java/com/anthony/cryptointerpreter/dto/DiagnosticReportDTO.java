package com.anthony.cryptointerpreter.dto;

import java.math.BigDecimal;
import java.util.List;

public record DiagnosticReportDTO(
        BigDecimal currentPrice,
        BigDecimal rsi,
        String volumeProfile,
        String volatilityLevel,
        double riskRewardRatio,
        double opportunityScore,
        List<String> signals,
        String summary,
        String actionPlan
) {
}
