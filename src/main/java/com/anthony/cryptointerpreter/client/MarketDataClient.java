package com.anthony.cryptointerpreter.client;

import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import java.util.List;

public interface MarketDataClient {
    List<Ticker24hDTO> fetchMarketData();
    String getMarketName();
}