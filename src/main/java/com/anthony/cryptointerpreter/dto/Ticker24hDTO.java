package com.anthony.cryptointerpreter.dto;

public record Ticker24hDTO(
        String symbol,
        String highPrice,
        String lowPrice,
        String lastPrice,
        String volume
) {
}
