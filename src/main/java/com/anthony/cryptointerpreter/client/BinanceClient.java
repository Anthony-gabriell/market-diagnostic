package com.anthony.cryptointerpreter.client;

import com.anthony.cryptointerpreter.dto.CryptoPriceDTO;
import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class BinanceClient {

    private static final String BASE_URL = "https://api.binance.com";

    private final RestClient restClient;

    public BinanceClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(BASE_URL)
                .build();
    }

    public CryptoPriceDTO fetchPrice(String symbol) {
        return restClient.get()
                .uri("/api/v3/ticker/price?symbol={symbol}", symbol)
                .retrieve()
                .body(CryptoPriceDTO.class);
    }

    public Ticker24hDTO fetchTicker24h(String symbol) {
        return restClient.get()
                .uri("/api/v3/ticker/24hr?symbol={symbol}", symbol)
                .retrieve()
                .body(Ticker24hDTO.class);
    }

    /**
     * Fetches OHLCV kline candles. Each candle is an array where:
     *   index 4 = close price (String)
     *   index 5 = volume (String)
     */
    public List<List<Object>> fetchKlines(String symbol, String interval, int limit) {
        return restClient.get()
                .uri("/api/v3/klines?symbol={symbol}&interval={interval}&limit={limit}",
                        symbol, interval, limit)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}