package com.anthony.cryptointerpreter.controller;

import com.anthony.cryptointerpreter.analysis.MarketScannerService;
import com.anthony.cryptointerpreter.dto.TopOpportunityDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Permite a conexão com o React
public class MarketController {

    private final MarketScannerService marketScannerService;

    @GetMapping("/opportunities")
    public List<TopOpportunityDTO> getOpportunities() {
        // Retorna o ranking calculado pelo Java e salvo no banco
        return marketScannerService.getTopOpportunities();
    }
}
