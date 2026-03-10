package com.anthony.cryptointerpreter.controller;

import com.anthony.cryptointerpreter.analysis.AnalysisService;
import com.anthony.cryptointerpreter.analysis.MarketScannerService;
import com.anthony.cryptointerpreter.dto.ChartAnnotationDTO;
import com.anthony.cryptointerpreter.dto.DiagnosticReportDTO;
import com.anthony.cryptointerpreter.dto.TopOpportunityDTO;
import com.anthony.cryptointerpreter.model.OpportunitySnapshot;
import com.anthony.cryptointerpreter.model.OpportunitySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final MarketScannerService marketScannerService;
    private final OpportunitySnapshotRepository snapshotRepository;

    @GetMapping("/{symbol}/annotations")
    public ResponseEntity<List<ChartAnnotationDTO>> getChartAnnotations(@PathVariable String symbol) {
        return ResponseEntity.ok(analysisService.getChartAnnotations(symbol));
    }

    @GetMapping("/{symbol}/diagnostic")
    public ResponseEntity<DiagnosticReportDTO> getDiagnosticReport(@PathVariable String symbol) {
        try {
            return ResponseEntity.ok(analysisService.getDiagnosticReport(symbol));
        } catch (Exception ex) {
            log.warn("Live diagnostic failed for {} ({}), falling back to cached snapshot", symbol, ex.getMessage());
            return snapshotRepository.findFirstBySymbolOrderByScannedAtDesc(symbol)
                    .map(AnalysisController::snapshotToDto)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
        }
    }

    private static DiagnosticReportDTO snapshotToDto(OpportunitySnapshot s) {
        return new DiagnosticReportDTO(
                null,                    // currentPrice — not stored in snapshot
                s.getRsi(),
                s.getVolumeProfile(),
                s.getVolatilityLevel(),
                s.getRiskRewardRatio(),
                s.getOpportunityScore(),
                List.of(),               // signals — not stored in snapshot
                s.getSummary(),
                null                     // actionPlan — not stored in snapshot
        );
    }

    @GetMapping("/scan")
    public ResponseEntity<List<TopOpportunityDTO>> runScan() {
        return ResponseEntity.ok(marketScannerService.runScan());
    }

    @GetMapping("/top-opportunities")
    public ResponseEntity<List<TopOpportunityDTO>> getTopOpportunities() {
        return ResponseEntity.ok(marketScannerService.getTopOpportunities());
    }

    // ── Error handling ────────────────────────────────────────────────────────

    /**
     * Converts an {@link IllegalArgumentException} thrown by {@code BinanceClient}
     * (e.g. when a B3 symbol like PETR4 is passed to a Binance-only endpoint)
     * into a 400 Bad Request with a descriptive JSON body instead of a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSymbol(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
