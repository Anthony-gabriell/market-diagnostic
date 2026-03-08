package com.anthony.cryptointerpreter.controller;

import com.anthony.cryptointerpreter.analysis.AnalysisService;
import com.anthony.cryptointerpreter.analysis.MarketScannerService;
import com.anthony.cryptointerpreter.dto.ChartAnnotationDTO;
import com.anthony.cryptointerpreter.dto.DiagnosticReportDTO;
import com.anthony.cryptointerpreter.dto.TopOpportunityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final MarketScannerService marketScannerService;

    @GetMapping("/{symbol}/annotations")
    public ResponseEntity<List<ChartAnnotationDTO>> getChartAnnotations(@PathVariable String symbol) {
        return ResponseEntity.ok(analysisService.getChartAnnotations(symbol));
    }

    @GetMapping("/{symbol}/diagnostic")
    public ResponseEntity<DiagnosticReportDTO> getDiagnosticReport(@PathVariable String symbol) {
        return ResponseEntity.ok(analysisService.getDiagnosticReport(symbol));
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
