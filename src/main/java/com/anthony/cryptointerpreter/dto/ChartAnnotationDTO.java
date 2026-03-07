package com.anthony.cryptointerpreter.dto;

import java.math.BigDecimal;

public record ChartAnnotationDTO(
        String type,
        BigDecimal priceLevel,
        String label,
        String color
) {
}
