package com.anthony.cryptointerpreter.service;

import com.anthony.cryptointerpreter.client.CoinGeckoClient;
import com.anthony.cryptointerpreter.dto.CryptoPriceDTO;
import com.anthony.cryptointerpreter.model.CryptoPrice;
import com.anthony.cryptointerpreter.model.CryptoPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PriceService {

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoPriceRepository cryptoPriceRepository;

    public CryptoPriceDTO getBitcoinPrice() {
        CryptoPriceDTO dto = coinGeckoClient.fetchPrice("BTCUSDT");

        CryptoPrice record = CryptoPrice.builder()
                .symbol(dto.symbol())
                .price(new BigDecimal(dto.price()))
                .timestamp(LocalDateTime.now())
                .build();

        cryptoPriceRepository.save(record);

        return dto;
    }
}