package com.anthony.cryptointerpreter.controller;

import com.anthony.cryptointerpreter.analysis.AnalysisService;
import com.anthony.cryptointerpreter.dto.ChartAnnotationDTO;
import com.anthony.cryptointerpreter.dto.DiagnosticReportDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/btc/annotations")
    public ResponseEntity<List<ChartAnnotationDTO>> getChartAnnotations() {
        return ResponseEntity.ok(analysisService.getChartAnnotations());
    }

    @GetMapping("/btc/diagnostic")
    public ResponseEntity<DiagnosticReportDTO> getDiagnosticReport() {
        return ResponseEntity.ok(analysisService.getDiagnosticReport());
    }
}
