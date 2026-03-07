package com.anthony.cryptointerpreter.controller;

import com.anthony.cryptointerpreter.dto.CryptoPriceDTO;
import com.anthony.cryptointerpreter.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/price")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/btc")
    public ResponseEntity<CryptoPriceDTO> getBitcoinPrice() {
        return ResponseEntity.ok(priceService.getBitcoinPrice());
    }
}