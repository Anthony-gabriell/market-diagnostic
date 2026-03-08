package com.anthony.cryptointerpreter.client;

import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches B3 stock data from HG Brasil Finance API in a single bulk request
 * and maps results to the shared {@link Ticker24hDTO} format so that the
 * existing RSI / volume / opportunity-score logic works identically for stocks.
 *
 * Endpoint: GET https://api.hgbrasil.com/finance/stock_price
 *             ?key=<hgbrasil.api.key>
 *             &symbol=PETR4,VALE3,...   (all 50 tickers in one call)
 */
@Slf4j
// @Component
public class HGBrasilClient implements MarketDataClient {

    // ── Ticker list ───────────────────────────────────────────────────────────

    /** 50 most liquid B3 tickers, grouped by segment. */
    private static final List<String> SYMBOLS = List.of(
            // Blue chips
            "PETR4", "VALE3", "ITUB4", "BBDC4", "BBAS3",
            "ABEV3", "WEGE3", "PRIO3", "BPAC11", "GGBR4",
            // Large caps
            "RENT3", "SUZB3", "RAIL3", "EQTL3", "VBBR3",
            "ELET3", "ELET6", "JBSS3", "LREN3", "SBSP3",
            // Mid caps
            "CSNA3", "CPLE6", "EMBR3", "MGLU3", "TOTS3",
            "HAPV3", "COGN3", "CYRE3", "MRVE3", "CCRO3",
            // Finance & insurance
            "FLRY3", "BRFS3", "NTCO3", "IRBR3", "TAEE11",
            "CSAN3", "CPFE3", "UGPA3", "RDOR3", "VIVT3",
            // Mixed / growth
            "KLBN11", "PETZ3", "YDUQ3", "MULT3", "BEEF3",
            "EGIE3",  "TIMS3", "ENEV3", "CMIN3", "CMIG4"
    );

    // ── Infrastructure ────────────────────────────────────────────────────────

    @Value("${hgbrasil.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /** Full request URL, built once after the bean is constructed. */
    private String bulkUrl;

    /**
     * Cache of the last successful API response, keyed by ticker symbol.
     * {@link #fetchMarketData()} populates it; {@link #getCloseHistory} and
     * {@link #getVolumeHistory} read from it so no second HTTP call is needed.
     */
    private volatile Map<String, StockResult> lastResults = Map.of();

    public HGBrasilClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    @PostConstruct
    void buildUrl() {
        bulkUrl = UriComponentsBuilder
                .fromHttpUrl("https://api.hgbrasil.com/finance/stock_price")
                .queryParam("key",    apiKey)
                .queryParam("symbol", String.join(",", SYMBOLS))
                .toUriString();

        log.info("HGBrasilClient ready — {} tickers, URL built", SYMBOLS.size());
    }

    // ── MarketDataClient ──────────────────────────────────────────────────────

    /**
     * Executes a single bulk HTTP request for all 50 tickers, caches the raw
     * results, and returns them mapped to {@link Ticker24hDTO}.
     */
    @Override
    public List<Ticker24hDTO> fetchMarketData() {
        log.info("Fetching B3 stocks from HG Brasil...");
        try {
            HGBrasilResponse response = restTemplate.getForObject(bulkUrl, HGBrasilResponse.class);

            if (response == null || response.getResults() == null) return List.of();

            ObjectMapper mapper = new ObjectMapper();
            lastResults = new HashMap<>();

            response.getResults().forEach((key, value) -> {
                // Se o valor for um Map (objeto), é uma ação válida.
                // Se for um Boolean (o "true" do erro), a gente ignora.
                if (value instanceof Map) {
                    StockResult stock = mapper.convertValue(value, StockResult.class);
                    lastResults.put(key, stock);
                }
            });

            log.info("HG Brasil retornou {} ações válidas.", lastResults.size());

            return lastResults.entrySet().stream()
                    .map(e -> toTicker24h(e.getKey(), e.getValue()))
                    .toList();

        } catch (Exception ex) {
            log.error("Erro no parsing: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public String getMarketName() {
        return "B3";
    }

    // ── Price history accessors (no extra HTTP call) ──────────────────────────

    /**
     * Returns daily close prices for {@code symbol} sorted oldest → newest.
     * Used by {@code MarketScannerService} to calculate RSI per stock.
     * Call after {@link #fetchMarketData()} to ensure the cache is warm.
     */
    public List<BigDecimal> getCloseHistory(String symbol) {
        StockResult stock = lastResults.get(symbol);
        if (stock == null || stock.getStockPriceHistory() == null) return List.of();
        return stock.getStockPriceHistory().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())   // yyyy-MM-dd sorts correctly
                .map(e -> BigDecimal.valueOf(e.getValue().getClose()))
                .toList();
    }

    /**
     * Returns daily volumes for {@code symbol} sorted oldest → newest.
     * Used by {@code MarketScannerService} for volume classification.
     * Call after {@link #fetchMarketData()} to ensure the cache is warm.
     */
    public List<BigDecimal> getVolumeHistory(String symbol) {
        StockResult stock = lastResults.get(symbol);
        if (stock == null || stock.getStockPriceHistory() == null) return List.of();
        return stock.getStockPriceHistory().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> BigDecimal.valueOf(e.getValue().getVolume()))
                .toList();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Maps one HG Brasil stock result to {@link Ticker24hDTO}.
     *
     * <ul>
     *   <li>{@code lastPrice} — current intraday price from {@code price} field</li>
     *   <li>{@code highPrice} / {@code lowPrice} — from the most recent trading day in
     *       {@code stock_price_history}</li>
     *   <li>{@code volume} — shares traded on the most recent day</li>
     * </ul>
     *
     * All values are stored as Strings to match the shared DTO contract used by
     * {@code AnalysisService} for both crypto and stocks.
     */
    private Ticker24hDTO toTicker24h(String symbol, StockResult stock) {
        String lastPrice = String.valueOf(stock.getPrice());
        String high      = lastPrice;
        String low       = lastPrice;
        String volume    = "0";

        Map<String, DayHistory> history = stock.getStockPriceHistory();
        if (history != null && !history.isEmpty()) {
            // Max on yyyy-MM-dd strings gives the most recent trading day correctly
            String latestDate = history.keySet().stream().max(String::compareTo).orElseThrow();
            DayHistory day = history.get(latestDate);
            high   = String.valueOf(day.getHigh());
            low    = String.valueOf(day.getLow());
            volume = String.valueOf(day.getVolume());
        }

        return new Ticker24hDTO(symbol, high, low, lastPrice, volume);
    }

    // ── HG Brasil response model ──────────────────────────────────────────────
    //
    // Static inner classes (not records) are used deliberately:
    //   • Records have a private canonical constructor — Jackson cannot instantiate them.
    //   • @Data generates a no-arg constructor + getters + setters, which is the
    //     standard JavaBean contract Jackson's ObjectMapper relies on.
    //   • @JsonProperty is explicit on every field so deserialization does not
    //     depend on the -parameters compiler flag or parameter-name reflection.
    //   • volume is double (not long) because HG Brasil returns it as a JSON
    //     floating-point number (e.g. 45123800.0); long would cause a
    //     MismatchedInputException.

    /**
     * Root response wrapper.
     * <pre>
     * { "by": "default", "valid_key": true,
     *   "results": { "PETR4": {...}, "VALE3": {...} } }
     * </pre>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HGBrasilResponse {
        // Mudamos de StockResult para Object para aceitar o "true" que a API manda no erro
        private Map<String, Object> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class StockResult {
        private String symbol;
        private double price;

        // O segredo: use @JsonProperty exatamente como a API envia
        @JsonProperty("stock_price_history")
        private Map<String, DayHistory> stockPriceHistory;

        // Adicione isso para capturar erros específicos por ativo
        @JsonProperty("error")
        private boolean error;
    }

    /**
     * OHLCV data for a single trading day.
     * {@code volume} is {@code double} because HG Brasil serialises it as a
     * JSON floating-point number; mapping to {@code long} would throw a
     * {@code MismatchedInputException}.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DayHistory {
        @JsonProperty("open")
        private double open;

        @JsonProperty("high")
        private double high;

        @JsonProperty("low")
        private double low;

        @JsonProperty("close")
        private double close;

        @JsonProperty("volume")
        private double volume;
    }
}
