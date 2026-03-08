package com.anthony.cryptointerpreter.analysis;

import com.anthony.cryptointerpreter.client.BinanceClient;
import com.anthony.cryptointerpreter.client.BrapiClient; // <--- Trocamos o import
import com.anthony.cryptointerpreter.dto.DiagnosticReportDTO;
import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import com.anthony.cryptointerpreter.dto.TopOpportunityDTO;
import com.anthony.cryptointerpreter.model.OpportunitySnapshot;
import com.anthony.cryptointerpreter.model.OpportunitySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketScannerService {

    private final AnalysisService analysisService;
    private final BinanceClient binanceClient;
    private final BrapiClient b3Client; // <--- Mudamos de HGBrasilClient para BrapiClient
    private final OpportunitySnapshotRepository snapshotRepository;

    public List<TopOpportunityDTO> runScan() {
        List<OpportunitySnapshot> snapshots = new ArrayList<>();
        snapshots.addAll(scanCrypto());
        snapshots.addAll(scanStocks());

        snapshotRepository.deleteAll();
        snapshotRepository.saveAll(snapshots);

        return toDTO(snapshotRepository.findAllByOrderByOpportunityScoreDesc());
    }

    /**
     * Retorna os últimos resultados salvos no banco,
     * ordenados pela pontuação de oportunidade (do maior para o menor).
     */
    public List<TopOpportunityDTO> getTopOpportunities() {
        return toDTO(snapshotRepository.findAllByOrderByOpportunityScoreDesc());
    }

    private List<OpportunitySnapshot> scanCrypto() {
        List<OpportunitySnapshot> snapshots = new ArrayList<>();
        List<Ticker24hDTO> tickers = binanceClient.fetchMarketData();

        for (Ticker24hDTO ticker : tickers) {
            try {
                List<BigDecimal> closes  = binanceClient.getCloseHistory(ticker.symbol());
                List<BigDecimal> volumes = binanceClient.getVolumeHistory(ticker.symbol());
                DiagnosticReportDTO report = analysisService.getDiagnosticReport(ticker, closes, volumes);
                snapshots.add(buildSnapshot(ticker.symbol(), report));
            } catch (Exception e) {
                log.warn("Skipping crypto {}: {}", ticker.symbol(), e.getMessage());
            }
        }
        return snapshots;
    }

    /**
     * NOVA ESTRATÉGIA PARA STOCKS (BRAPI)
     */
    private List<OpportunitySnapshot> scanStocks() {
        List<OpportunitySnapshot> snapshots = new ArrayList<>();

        // A BrapiClient agora retorna os dados e já deixa o histórico pronto
        List<Ticker24hDTO> tickers = b3Client.fetchMarketData();
        log.info("Brapi returned {} stocks for analysis", tickers.size());

        for (Ticker24hDTO ticker : tickers) {
            try {
                // IMPORTANTE: A BrapiClient precisa ter esses métodos implementados!
                List<BigDecimal> closes  = b3Client.getCloseHistory(ticker.symbol());
                List<BigDecimal> volumes = b3Client.getVolumeHistory(ticker.symbol());

                DiagnosticReportDTO report = analysisService.getDiagnosticReport(ticker, closes, volumes);

                snapshots.add(buildSnapshot(ticker.symbol(), report));
                log.debug("Stock scan OK — {} RSI={}", ticker.symbol(), report.rsi());

            } catch (Exception e) {
                log.warn("Skipping stock {}: {}", ticker.symbol(), e.getMessage());
            }
        }
        return snapshots;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OpportunitySnapshot buildSnapshot(String symbol, DiagnosticReportDTO report) {
        return OpportunitySnapshot.builder()
                .symbol(symbol)
                .opportunityScore(report.opportunityScore())
                .rsi(report.rsi())
                .volumeProfile(report.volumeProfile())
                .riskRewardRatio(report.riskRewardRatio())
                .volatilityLevel(report.volatilityLevel())
                .summary(report.summary())
                .scannedAt(LocalDateTime.now())
                .build();
    }

    private List<TopOpportunityDTO> toDTO(List<OpportunitySnapshot> snapshots) {
        return snapshots.stream()
                .map(s -> new TopOpportunityDTO(
                        s.getSymbol(),
                        s.getOpportunityScore(),
                        s.getRsi(),
                        s.getVolumeProfile(),
                        s.getRiskRewardRatio(),
                        s.getVolatilityLevel(),
                        s.getSummary(),
                        s.getScannedAt()))
                .toList();
    }

}
