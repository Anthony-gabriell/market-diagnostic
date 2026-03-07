package com.anthony.cryptointerpreter.analysis;

import com.anthony.cryptointerpreter.client.BinanceClient;
import com.anthony.cryptointerpreter.dto.ChartAnnotationDTO;
import com.anthony.cryptointerpreter.dto.DiagnosticReportDTO;
import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import com.anthony.cryptointerpreter.model.CryptoPrice;
import com.anthony.cryptointerpreter.model.CryptoPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String KLINE_INTERVAL = "1h";
    private static final int    KLINE_LIMIT    = 15;

    private static final BigDecimal PROXIMITY_THRESHOLD    = new BigDecimal("0.005");
    private static final BigDecimal HIGH_VOLUME_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal LOW_VOLUME_MULTIPLIER  = new BigDecimal("0.7");
    private static final BigDecimal RSI_OVERBOUGHT         = new BigDecimal("70");

    private final BinanceClient binanceClient;
    private final CryptoPriceRepository cryptoPriceRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    public List<ChartAnnotationDTO> getChartAnnotations(String symbol) {
        Ticker24hDTO ticker = binanceClient.fetchTicker24h(symbol);
        List<CryptoPrice> recentPrices = cryptoPriceRepository.findTop10BySymbolOrderByTimestampDesc(symbol);

        BigDecimal high24h      = new BigDecimal(ticker.highPrice());
        BigDecimal low24h       = new BigDecimal(ticker.lowPrice());
        BigDecimal currentPrice = new BigDecimal(ticker.lastPrice());

        List<ChartAnnotationDTO> annotations = new ArrayList<>();

        annotations.add(new ChartAnnotationDTO("RESISTANCE", high24h,
                "24h Resistance at " + formatPrice(high24h), "#FF4444"));
        annotations.add(new ChartAnnotationDTO("SUPPORT", low24h,
                "24h Support at " + formatPrice(low24h), "#00C851"));

        if (!recentPrices.isEmpty()) {
            BigDecimal recentHigh = recentPrices.stream().map(CryptoPrice::getPrice)
                    .max(BigDecimal::compareTo).orElseThrow();
            BigDecimal recentLow  = recentPrices.stream().map(CryptoPrice::getPrice)
                    .min(BigDecimal::compareTo).orElseThrow();

            if (!isNearLevel(recentHigh, high24h)) {
                annotations.add(new ChartAnnotationDTO("RESISTANCE", recentHigh,
                        "Recent Resistance at " + formatPrice(recentHigh), "#FF8800"));
            }
            if (!isNearLevel(recentLow, low24h)) {
                annotations.add(new ChartAnnotationDTO("SUPPORT", recentLow,
                        "Recent Support at " + formatPrice(recentLow), "#33B679"));
            }
        }

        if (currentPrice.compareTo(high24h) > 0) {
            annotations.add(new ChartAnnotationDTO("BREAKOUT", currentPrice,
                    "Breakout above " + formatPrice(high24h), "#FFD700"));
        } else if (currentPrice.compareTo(low24h) < 0) {
            annotations.add(new ChartAnnotationDTO("BREAKOUT", currentPrice,
                    "Breakdown below " + formatPrice(low24h), "#FF00FF"));
        }

        return annotations;
    }

    public DiagnosticReportDTO getDiagnosticReport(String symbol) {
        Ticker24hDTO ticker = binanceClient.fetchTicker24h(symbol);
        List<List<Object>> klines = binanceClient.fetchKlines(symbol, KLINE_INTERVAL, KLINE_LIMIT);

        BigDecimal high24h      = new BigDecimal(ticker.highPrice());
        BigDecimal low24h       = new BigDecimal(ticker.lowPrice());
        BigDecimal currentPrice = new BigDecimal(ticker.lastPrice());

        BigDecimal rsi           = calculateRSI(extractCloses(klines));
        String     volumeProfile = classifyVolume(extractVolumes(klines));
        String     volatility    = calculateVolatility(klines);
        double     rrRatio       = calculateRiskReward(currentPrice, low24h, high24h);
        double     oppScore      = calculateOpportunityScore(rsi, volumeProfile, rrRatio);

        // ── Confluence signals ────────────────────────────────────────────────

        boolean atResistance    = isNearLevel(currentPrice, high24h);
        boolean brokeResistance = currentPrice.compareTo(high24h) > 0;
        boolean volumeHigh      = "HIGH".equals(volumeProfile);
        boolean volumeLow       = "LOW".equals(volumeProfile);
        boolean rsiOverbought   = rsi.compareTo(RSI_OVERBOUGHT) > 0;

        List<String> signals = new ArrayList<>();
        if (atResistance && volumeLow)    signals.add("Fakeout Warning: Low buying pressure at resistance");
        if (brokeResistance && volumeHigh) signals.add("Breakout Confirmed: High institutional interest");
        if (rsiOverbought && atResistance) signals.add("Exhaustion Alert: Market overbought, expect correction");

        // ── Summary (descriptive state) ───────────────────────────────────────

        String summary = buildSummary(oppScore, volatility, signals, rsi, volumeProfile, rrRatio);

        // ── Action plan (direct instruction based on 3 metrics) ───────────────

        String actionPlan = buildActionPlan(
                oppScore, volatility, rrRatio, rsi, volumeProfile, low24h, high24h);

        return new DiagnosticReportDTO(currentPrice, rsi, volumeProfile, volatility,
                rrRatio, oppScore, signals, summary, actionPlan);
    }

    /**
     * Calculates a full DiagnosticReportDTO from pre-fetched data.
     * Used for non-Binance assets (e.g. B3 stocks via HG Brasil) where the
     * ticker and price history are sourced externally rather than from klines.
     *
     * @param ticker     24-h market snapshot (high, low, last, volume)
     * @param closes     daily close prices sorted oldest → newest (used for RSI)
     * @param volumes    daily volumes sorted oldest → newest (used for volume profile)
     */
    public DiagnosticReportDTO getDiagnosticReport(Ticker24hDTO ticker,
                                                   List<BigDecimal> closes,
                                                   List<BigDecimal> volumes) {
        BigDecimal high24h      = new BigDecimal(ticker.highPrice());
        BigDecimal low24h       = new BigDecimal(ticker.lowPrice());
        BigDecimal currentPrice = new BigDecimal(ticker.lastPrice());

        BigDecimal rsi           = calculateRSI(closes);
        String     volumeProfile = classifyVolume(volumes);
        String     volatility    = "NORMAL";   // no intraday klines for stocks
        double     rrRatio       = calculateRiskReward(currentPrice, low24h, high24h);
        double     oppScore      = calculateOpportunityScore(rsi, volumeProfile, rrRatio);

        boolean atResistance    = isNearLevel(currentPrice, high24h);
        boolean brokeResistance = currentPrice.compareTo(high24h) > 0;
        boolean volumeHigh      = "HIGH".equals(volumeProfile);
        boolean volumeLow       = "LOW".equals(volumeProfile);
        boolean rsiOverbought   = rsi.compareTo(RSI_OVERBOUGHT) > 0;

        List<String> signals = new ArrayList<>();
        if (atResistance && volumeLow)     signals.add("Fakeout Warning: Low buying pressure at resistance");
        if (brokeResistance && volumeHigh) signals.add("Breakout Confirmed: High institutional interest");
        if (rsiOverbought && atResistance) signals.add("Exhaustion Alert: Market overbought, expect correction");

        String summary    = buildSummary(oppScore, volatility, signals, rsi, volumeProfile, rrRatio);
        String actionPlan = buildActionPlan(oppScore, volatility, rrRatio, rsi, volumeProfile, low24h, high24h);

        return new DiagnosticReportDTO(currentPrice, rsi, volumeProfile, volatility,
                rrRatio, oppScore, signals, summary, actionPlan);
    }

    // ── Calculation helpers ───────────────────────────────────────────────────

    private BigDecimal calculateRSI(List<BigDecimal> closes) {
        if (closes.size() < 2) return new BigDecimal("50");

        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;

        for (int i = 1; i < closes.size(); i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            if (change.compareTo(BigDecimal.ZERO) > 0) totalGain = totalGain.add(change);
            else                                        totalLoss = totalLoss.add(change.abs());
        }

        int        periods = closes.size() - 1;
        BigDecimal avgGain = totalGain.divide(BigDecimal.valueOf(periods), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = totalLoss.divide(BigDecimal.valueOf(periods), 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) return new BigDecimal("100");

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP));
    }

    private String classifyVolume(List<BigDecimal> volumes) {
        if (volumes.size() < 2) return "AVERAGE";

        List<BigDecimal> historical = volumes.subList(0, volumes.size() - 1);
        BigDecimal currentVol = volumes.getLast();
        BigDecimal avgVol = historical.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(historical.size()), 8, RoundingMode.HALF_UP);

        if (avgVol.compareTo(BigDecimal.ZERO) == 0) return "AVERAGE";
        if (currentVol.compareTo(avgVol.multiply(HIGH_VOLUME_MULTIPLIER)) > 0) return "HIGH";
        if (currentVol.compareTo(avgVol.multiply(LOW_VOLUME_MULTIPLIER))  < 0) return "LOW";
        return "AVERAGE";
    }

    /**
     * Volatility = total price range across all 15 klines.
     * (maxHigh − minLow) / minLow × 100 > 2% → HIGH | < 0.5% → LOW | else NORMAL.
     */
    private String calculateVolatility(List<List<Object>> klines) {
        if (klines.isEmpty()) return "NORMAL";

        BigDecimal maxHigh = klines.stream()
                .map(k -> new BigDecimal(k.get(2).toString()))
                .max(BigDecimal::compareTo).orElseThrow();

        BigDecimal minLow = klines.stream()
                .map(k -> new BigDecimal(k.get(3).toString()))
                .min(BigDecimal::compareTo).orElseThrow();

        if (minLow.compareTo(BigDecimal.ZERO) == 0) return "NORMAL";

        BigDecimal rangePercent = maxHigh.subtract(minLow)
                .divide(minLow, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (rangePercent.compareTo(new BigDecimal("2.0")) > 0) return "HIGH";
        if (rangePercent.compareTo(new BigDecimal("0.5")) < 0) return "LOW";
        return "NORMAL";
    }

    /**
     * R/R = (resistance − price) / (price − support).
     * Returns 0.0 if price is at or below support.
     */
    private double calculateRiskReward(BigDecimal price, BigDecimal support, BigDecimal resistance) {
        BigDecimal distToResistance = resistance.subtract(price);
        BigDecimal distToSupport    = price.subtract(support);

        if (distToSupport.compareTo(BigDecimal.ZERO) <= 0) return 0.0;

        double raw = distToResistance.divide(distToSupport, 4, RoundingMode.HALF_UP).doubleValue();
        return Math.round(raw * 100.0) / 100.0;
    }

    /**
     * Weighted average across 3 components (35% RSI + 30% Volume + 35% R/R).
     * Score ≥ 8 requires all three gates simultaneously:
     *   RSI < 40 + Volume HIGH/AVERAGE + R/R > 2.0
     *
     * RSI scores  : <30→10 | 30-40→8.5 | 40-50→4 | 50-60→2.5 | 60-70→1.5 | ≥70→0.5
     * Volume scores: HIGH→10 | AVERAGE→8 | LOW→1
     * R/R scores  : ≥3→10 | ≥2→8 | ≥1.5→3 | ≥1→2 | <1→0.5
     */
    private double calculateOpportunityScore(BigDecimal rsi, String volumeProfile, double rrRatio) {
        double rsiVal = rsi.doubleValue();
        double rsiScore;
        if      (rsiVal < 30) rsiScore = 10.0;
        else if (rsiVal < 40) rsiScore = 8.5;
        else if (rsiVal < 50) rsiScore = 4.0;
        else if (rsiVal < 60) rsiScore = 2.5;
        else if (rsiVal < 70) rsiScore = 1.5;
        else                  rsiScore = 0.5;

        double volumeScore = switch (volumeProfile) {
            case "HIGH"    -> 10.0;
            case "AVERAGE" -> 8.0;
            default        -> 1.0;
        };

        double rrScore;
        if      (rrRatio >= 3.0) rrScore = 10.0;
        else if (rrRatio >= 2.0) rrScore = 8.0;
        else if (rrRatio >= 1.5) rrScore = 3.0;
        else if (rrRatio >= 1.0) rrScore = 2.0;
        else                     rrScore = 0.5;

        double total = (rsiScore * 0.35) + (volumeScore * 0.30) + (rrScore * 0.35);
        return Math.min(Math.round(total * 10.0) / 10.0, 10.0);
    }

    // ── Text builders ─────────────────────────────────────────────────────────

    private String buildSummary(double score, String volatility, List<String> signals,
                                BigDecimal rsi, String volumeProfile, double rrRatio) {
        if (!signals.isEmpty()) {
            String signalText = String.join(" | ", signals);
            String volNote = "HIGH".equals(volatility) ? " Elevated volatility increases risk." : "";
            return signalText + "." + volNote;
        }

        if (score >= 8.0) {
            return String.format(
                    "High value opportunity detected. RSI %.1f + Volume %s + R/R %.2f:1 — all three conditions aligned.",
                    rsi.doubleValue(), volumeProfile, rrRatio);
        }
        if (score >= 6.0) {
            return String.format(
                    "Setup partially aligned (score %.1f/10). One or more conditions below threshold.",
                    score);
        }
        return String.format(
                "No tradeable edge (score %.1f/10). Waiting for confluence.",
                score);
    }

    private String buildActionPlan(double score, String volatility, double rrRatio,
                                   BigDecimal rsi, String volumeProfile,
                                   BigDecimal support, BigDecimal resistance) {
        if (score >= 8.0) {
            String sizeNote = "HIGH".equals(volatility)
                    ? "HIGH volatility — use 50% of normal position size."
                    : "Volatility is normal — standard position size applies.";
            return String.format(
                    "ENTER: R/R of %.2f:1 justifies exposure. Scale long near %s. Stop-loss below %s. Target %s. %s",
                    rrRatio, formatPrice(support), formatPrice(support), formatPrice(resistance), sizeNote);
        }

        if (score >= 6.0) {
            String missingGate = identifyWeakestGate(rsi, volumeProfile, rrRatio);
            return String.format(
                    "WAIT: Score %.1f/10. %s Monitor for all three conditions to align before entering.",
                    score, missingGate);
        }

        if (score >= 4.0) {
            return String.format(
                    "REDUCE RISK: Score %.1f/10. R/R of %.2f:1 is insufficient. No new entries until R/R exceeds 2.0 and RSI drops below 40.",
                    score, rrRatio);
        }

        return String.format(
                "STAY OUT: Score %.1f/10. No edge detected. R/R of %.2f:1 does not justify exposure at current price.",
                score, rrRatio);
    }

    private String identifyWeakestGate(BigDecimal rsi, String volumeProfile, double rrRatio) {
        List<String> failing = new ArrayList<>();
        if (rsi.doubleValue() >= 40) {
            failing.add(String.format("RSI at %.1f (needs < 40)", rsi.doubleValue()));
        }
        if ("LOW".equals(volumeProfile)) {
            failing.add("volume is LOW (needs HIGH or AVERAGE)");
        }
        if (rrRatio < 2.0) {
            failing.add(String.format("R/R of %.2f:1 (needs > 2.0)", rrRatio));
        }
        return failing.isEmpty() ? "" : "Failing: " + String.join(", ", failing) + ".";
    }

    // ── Shared utilities ──────────────────────────────────────────────────────

    private boolean isNearLevel(BigDecimal price, BigDecimal level) {
        BigDecimal diff      = price.subtract(level).abs();
        BigDecimal threshold = level.multiply(PROXIMITY_THRESHOLD);
        return diff.compareTo(threshold) <= 0;
    }

    private List<BigDecimal> extractCloses(List<List<Object>> klines) {
        return klines.stream().map(k -> new BigDecimal(k.get(4).toString())).toList();
    }

    private List<BigDecimal> extractVolumes(List<List<Object>> klines) {
        return klines.stream().map(k -> new BigDecimal(k.get(5).toString())).toList();
    }

    private String formatPrice(BigDecimal price) {
        if (price.compareTo(new BigDecimal("1000")) >= 0) {
            return "$" + price.divide(new BigDecimal("1000"), 1, RoundingMode.HALF_UP).toPlainString() + "k";
        }
        return "$" + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
