package com.anthony.cryptointerpreter.client;

import com.anthony.cryptointerpreter.dto.CryptoPriceDTO;
import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binance market data client.
 *
 * <p>Implements {@link MarketDataClient} so that {@code MarketScannerService}
 * can treat crypto exactly like any other data provider (B3, etc.).
 *
 * <p>Klines are fetched lazily and cached per scan: the first call to
 * {@link #getCloseHistory} or {@link #getVolumeHistory} for a given symbol
 * fetches the candles once; the second call for the same symbol reads from
 * the cache. {@link #fetchMarketData()} clears the cache at the start of
 * each scan to prevent stale data.
 */
@Slf4j
@Component
public class BinanceClient implements MarketDataClient {

    private static final String BASE_URL       = "https://api.binance.com";
    private static final String KLINE_INTERVAL = "1h";
    private static final int    KLINE_LIMIT    = 15;

    // ── Symbol list ───────────────────────────────────────────────────────────

    /** 25 most liquid USDT pairs on Binance, ordered by average daily volume. */
    private static final List<String> SYMBOLS = List.of(
            // Tier 1 — mega caps
            "BTCUSDT",  "ETHUSDT",  "BNBUSDT",  "SOLUSDT",  "XRPUSDT",
            // Tier 2 — large caps
            "ADAUSDT",  "AVAXUSDT", "DOGEUSDT", "DOTUSDT",  "MATICUSDT",
            // Tier 3 — mid caps
            "LINKUSDT", "LTCUSDT",  "UNIUSDT",  "ATOMUSDT", "XLMUSDT",
            // Tier 4 — established alts
            "VETUSDT",  "FILUSDT",  "TRXUSDT",  "ETCUSDT",  "ALGOUSDT",
            // Tier 5 — DeFi / gaming
            "AAVEUSDT", "FTMUSDT",  "SANDUSDT", "MANAUSDT", "AXSUSDT"
    );

    // ── Infrastructure ────────────────────────────────────────────────────────

    private final RestClient restClient;

    /**
     * Per-scan kline cache. Keyed by symbol; populated lazily by
     * {@link #getCloseHistory} / {@link #getVolumeHistory} and cleared at
     * the beginning of every {@link #fetchMarketData()} call.
     */
    private final Map<String, List<List<Object>>> klineCache = new ConcurrentHashMap<>();

    public BinanceClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    // ── MarketDataClient ──────────────────────────────────────────────────────

    /**
     * Iterates all 25 configured symbols, calls {@link #fetchTicker24h} for each,
     * and returns the results as a {@link List}<{@link Ticker24hDTO}>.
     * Clears the kline cache so that subsequent history lookups reflect fresh data.
     */
    @Override
    public List<Ticker24hDTO> fetchMarketData() {
        klineCache.clear();
        log.info("Fetching 24h tickers for {} crypto pairs from Binance", SYMBOLS.size());

        List<Ticker24hDTO> tickers = new ArrayList<>();
        for (String symbol : SYMBOLS) {
            try {
                tickers.add(fetchTicker24h(symbol));
            } catch (Exception e) {
                log.warn("Skipping {} — ticker fetch failed: {}", symbol, e.getMessage());
            }
        }

        log.info("Binance returned {} tickers", tickers.size());
        return tickers;
    }

    @Override
    public String getMarketName() {
        return "Binance";
    }

    // ── Kline history accessors (cached per scan) ─────────────────────────────

    /**
     * Returns the last {@value #KLINE_LIMIT} hourly close prices for {@code symbol},
     * oldest → newest. Fetches klines on first call; subsequent calls use the cache.
     */
    public List<BigDecimal> getCloseHistory(String symbol) {
        return cachedKlines(symbol).stream()
                .map(k -> new BigDecimal(k.get(4).toString()))
                .toList();
    }

    /**
     * Returns the last {@value #KLINE_LIMIT} hourly base-asset volumes for
     * {@code symbol}, oldest → newest. Fetches klines on first call; subsequent
     * calls use the cache.
     */
    public List<BigDecimal> getVolumeHistory(String symbol) {
        return cachedKlines(symbol).stream()
                .map(k -> new BigDecimal(k.get(5).toString()))
                .toList();
    }

    private List<List<Object>> cachedKlines(String symbol) {
        return klineCache.computeIfAbsent(symbol,
                s -> fetchKlines(s, KLINE_INTERVAL, KLINE_LIMIT));
    }

    // ── Single-symbol APIs (used by AnalysisService and AnalysisController) ───

    public CryptoPriceDTO fetchPrice(String symbol) {
        requireCryptoSymbol(symbol);
        return restClient.get()
                .uri("/api/v3/ticker/price?symbol={symbol}", symbol)
                .retrieve()
                .body(CryptoPriceDTO.class);
    }

    public Ticker24hDTO fetchTicker24h(String symbol) {
        requireCryptoSymbol(symbol);
        return restClient.get()
                .uri("/api/v3/ticker/24hr?symbol={symbol}", symbol)
                .retrieve()
                .body(Ticker24hDTO.class);
    }

    /**
     * Fetches OHLCV kline candles. Each candle is a raw Binance array where:
     *   index 0 = open time (ms), index 4 = close price, index 5 = volume.
     */
    public List<List<Object>> fetchKlines(String symbol, String interval, int limit) {
        requireCryptoSymbol(symbol);
        return restClient.get()
                .uri("/api/v3/klines?symbol={symbol}&interval={interval}&limit={limit}",
                        symbol, interval, limit)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Rejects non-crypto symbols before they reach the Binance API.
     * B3 tickers (e.g. PETR4, VALE3) do not end with a quote currency suffix
     * and would cause a 400 Bad Request from Binance.
     *
     * @throws IllegalArgumentException if the symbol is not a USDT pair
     */
    private static void requireCryptoSymbol(String symbol) {
        if (symbol == null || !symbol.toUpperCase().endsWith("USDT")) {
            throw new IllegalArgumentException(
                    "'" + symbol + "' is not a valid Binance symbol. " +
                    "Only USDT pairs are supported (e.g. BTCUSDT, ETHUSDT). " +
                    "B3 stocks must be analysed via the HG Brasil client.");
        }
    }
}
