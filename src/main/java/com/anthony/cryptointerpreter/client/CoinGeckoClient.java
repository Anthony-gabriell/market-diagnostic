package com.anthony.cryptointerpreter.client;

import com.anthony.cryptointerpreter.dto.CryptoPriceDTO;
import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CoinGeckoClient implements MarketDataClient {

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    private static final Map<String, String> SYMBOL_TO_ID = Map.ofEntries(
            // Tier 1 — mega caps
            Map.entry("BTCUSDT",   "bitcoin"),
            Map.entry("ETHUSDT",   "ethereum"),
            Map.entry("BNBUSDT",   "binancecoin"),
            Map.entry("SOLUSDT",   "solana"),
            Map.entry("XRPUSDT",   "ripple"),
            // Tier 2 — large caps
            Map.entry("ADAUSDT",   "cardano"),
            Map.entry("AVAXUSDT",  "avalanche-2"),
            Map.entry("DOGEUSDT",  "dogecoin"),
            Map.entry("DOTUSDT",   "polkadot"),
            Map.entry("MATICUSDT", "matic-network"),
            // Tier 3 — mid caps
            Map.entry("LINKUSDT",  "chainlink"),
            Map.entry("LTCUSDT",   "litecoin"),
            Map.entry("UNIUSDT",   "uniswap"),
            Map.entry("ATOMUSDT",  "cosmos"),
            Map.entry("XLMUSDT",   "stellar"),
            // Tier 4 — established alts
            Map.entry("VETUSDT",   "vechain"),
            Map.entry("FILUSDT",   "filecoin"),
            Map.entry("TRXUSDT",   "tron"),
            Map.entry("ETCUSDT",   "ethereum-classic"),
            Map.entry("ALGOUSDT",  "algorand"),
            // Tier 5 — DeFi / gaming
            Map.entry("AAVEUSDT",  "aave"),
            Map.entry("FTMUSDT",   "fantom"),
            Map.entry("SANDUSDT",  "the-sandbox"),
            Map.entry("MANAUSDT",  "decentraland"),
            Map.entry("AXSUSDT",   "axie-infinity")
    );

    /** Stable ordered list derived from the map. */
    private static final List<String> SYMBOLS = List.of(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT",
            "ADAUSDT", "AVAXUSDT", "DOGEUSDT", "DOTUSDT", "MATICUSDT",
            "LINKUSDT", "LTCUSDT", "UNIUSDT", "ATOMUSDT", "XLMUSDT",
            "VETUSDT", "FILUSDT", "TRXUSDT", "ETCUSDT", "ALGOUSDT",
            "AAVEUSDT", "FTMUSDT", "SANDUSDT", "MANAUSDT", "AXSUSDT"
    );

    private final RestClient restClient;
    private final String apiKey;

    /**
     * Per-scan chart cache. Keyed by symbol; populated lazily by
     * {@link #getCloseHistory} / {@link #getVolumeHistory} and cleared at
     * the beginning of every {@link #fetchMarketData()} call.
     */
    private final Map<String, MarketChartData> chartCache = new ConcurrentHashMap<>();

    public CoinGeckoClient(RestClient.Builder builder,
                           @Value("${coingecko.api.key}") String apiKey) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey;
    }

    // ── MarketDataClient ──────────────────────────────────────────────────────

    /**
     * Fetches all 25 coins in a single /coins/markets call, clears the chart
     * cache so subsequent history lookups reflect fresh data.
     */
    @Override
    public List<Ticker24hDTO> fetchMarketData() {
        chartCache.clear();

        String ids = SYMBOLS.stream()
                .map(SYMBOL_TO_ID::get)
                .collect(Collectors.joining(","));
        log.info("Fetching market data for {} coins from CoinGecko", SYMBOL_TO_ID.size());

        List<Map<String, Object>> coins = restClient.get()
                .uri("/coins/markets?vs_currency=usd&ids={ids}", ids)
                .header("x-cg-demo-api-key", apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        Map<String, Ticker24hDTO> idToTicker = new HashMap<>();
        if (coins != null) {
            for (Map<String, Object> coin : coins) {
                String id = (String) coin.get("id");
                String symbol = idToSymbol(id);
                if (symbol == null) continue;
                idToTicker.put(id, mapToTicker(symbol, coin));
            }
        }

        List<Ticker24hDTO> result = new ArrayList<>();
        for (String symbol : SYMBOLS) {
            String id = SYMBOL_TO_ID.get(symbol);
            if (idToTicker.containsKey(id)) result.add(idToTicker.get(id));
        }
        log.info("CoinGecko returned {} tickers", result.size());
        return result;
    }

    @Override
    public String getMarketName() {
        return "CoinGecko";
    }

    // ── Single-symbol APIs ────────────────────────────────────────────────────

    public Ticker24hDTO fetchTicker24h(String symbol) {
        String id = requireId(symbol);
        List<Map<String, Object>> coins = restClient.get()
                .uri("/coins/markets?vs_currency=usd&ids={id}", id)
                .header("x-cg-demo-api-key", apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (coins == null || coins.isEmpty()) {
            throw new IllegalStateException("No data returned for symbol: " + symbol);
        }
        return mapToTicker(symbol, coins.get(0));
    }

    public CryptoPriceDTO fetchPrice(String symbol) {
        String id = requireId(symbol);
        Map<String, Map<String, Object>> response = restClient.get()
                .uri("/simple/price?ids={id}&vs_currencies=usd", id)
                .header("x-cg-demo-api-key", apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null || !response.containsKey(id)) {
            throw new IllegalStateException("No price data for symbol: " + symbol);
        }
        Object price = response.get(id).get("usd");
        return new CryptoPriceDTO(symbol, price.toString());
    }

    // ── History accessors (cached per scan) ───────────────────────────────────

    public List<BigDecimal> getCloseHistory(String symbol) {
        return cachedChart(symbol).closes();
    }

    public List<BigDecimal> getVolumeHistory(String symbol) {
        return cachedChart(symbol).volumes();
    }

    private MarketChartData cachedChart(String symbol) {
        return chartCache.computeIfAbsent(symbol, this::fetchMarketChart);
    }

    private MarketChartData fetchMarketChart(String symbol) {
        String id = requireId(symbol);
        Map<String, List<List<Object>>> data = restClient.get()
                .uri("/coins/{id}/market_chart?vs_currency=usd&days=15&interval=daily", id)
                .header("x-cg-demo-api-key", apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<BigDecimal> closes  = new ArrayList<>();
        List<BigDecimal> volumes = new ArrayList<>();

        if (data != null) {
            List<List<Object>> prices = data.get("prices");
            if (prices != null) {
                for (List<Object> point : prices) {
                    closes.add(new BigDecimal(point.get(1).toString()));
                }
            }
            List<List<Object>> vols = data.get("total_volumes");
            if (vols != null) {
                for (List<Object> point : vols) {
                    volumes.add(new BigDecimal(point.get(1).toString()));
                }
            }
        }

        return new MarketChartData(closes, volumes);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ticker24hDTO mapToTicker(String symbol, Map<String, Object> coin) {
        String high   = safeString(coin.get("high_24h"));
        String low    = safeString(coin.get("low_24h"));
        String price  = safeString(coin.get("current_price"));
        String volume = safeString(coin.get("total_volume"));
        return new Ticker24hDTO(symbol, high, low, price, volume);
    }

    private String safeString(Object value) {
        return value != null ? value.toString() : "0";
    }

    private String idToSymbol(String id) {
        return SYMBOL_TO_ID.entrySet().stream()
                .filter(e -> e.getValue().equals(id))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String requireId(String symbol) {
        String id = SYMBOL_TO_ID.get(symbol != null ? symbol.toUpperCase() : "");
        if (id == null) {
            throw new IllegalArgumentException("Unknown CoinGecko symbol: " + symbol);
        }
        return id;
    }

    private record MarketChartData(List<BigDecimal> closes, List<BigDecimal> volumes) {}
}
