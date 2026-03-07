package com.anthony.cryptointerpreter.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CryptoPriceRepository extends JpaRepository<CryptoPrice, Long> {

    List<CryptoPrice> findTop10BySymbolOrderByTimestampDesc(String symbol);
}