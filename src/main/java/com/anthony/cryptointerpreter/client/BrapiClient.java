package com.anthony.cryptointerpreter.client;

import com.anthony.cryptointerpreter.dto.Ticker24hDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BrapiClient implements MarketDataClient {

    @Value("${brapi.api.token}")
    private String apiToken;

    private final RestTemplate restTemplate = new RestTemplate();

    // Cache interno para os métodos de histórico funcionarem
    private final Map<String, List<BigDecimal>> closeHistoryMap = new HashMap<>();
    private final Map<String, List<BigDecimal>> volumeHistoryMap = new HashMap<>();

    private static final List<String> SYMBOLS = List.of(
            // Blue chips
            "PETR4", "VALE3", "ITUB4", "BBDC4", "BBAS3",
            "ABEV3", "WEGE3", "PRIO3", "BPAC11", "GGBR4",
            // Large caps
            "RENT3", "SUZB3", "RAIL3", "EQTL3", "VBBR3",
            "ELET3", "ELET6", "JBSS3", "LREN3", "SBSP3",
            // Mid caps
            "CSNA3", "CPLE6", "EMBR3", "MGLU3", "TOTS3",
            "HAPV3", "COGN3", "CYRE3", "MRVE3", "CCRO3",
            // Finance & insurance
            "FLRY3", "BRFS3", "NTCO3", "IRBR3", "TAEE11",
            "CSAN3", "CPFE3", "UGPA3", "RDOR3", "VIVT3",
            // Mixed / growth
            "KLBN11", "PETZ3", "YDUQ3", "MULT3", "BEEF3",
            "EGIE3",  "TIMS3", "ENEV3", "CMIN3", "CMIG4"
    );

    @Override
    public List<Ticker24hDTO> fetchMarketData() {
        List<Ticker24hDTO> allTickers = new ArrayList<>();
        closeHistoryMap.clear();
        volumeHistoryMap.clear();

        for (String symbol : SYMBOLS) {
            String url = UriComponentsBuilder.fromHttpUrl("https://brapi.dev/api/quote/" + symbol)
                    .queryParam("token", apiToken)
                    .queryParam("range", "1mo")
                    .queryParam("interval", "1d")
                    .toUriString();

            try {
                BrapiResponse response = restTemplate.getForObject(url, BrapiResponse.class);
                if (response != null && response.getResults() != null) {
                    for (StockResult res : response.getResults()) {
                        allTickers.add(mapToDto(res));

                        if (res.getHistoricalData() != null) {
                            List<BigDecimal> closes = res.getHistoricalData().stream()
                                    .map(h -> BigDecimal.valueOf(h.getClose())).toList();
                            List<BigDecimal> volumes = res.getHistoricalData().stream()
                                    .map(h -> BigDecimal.valueOf(h.getVolume())).toList();

                            closeHistoryMap.put(res.getSymbol(), closes);
                            volumeHistoryMap.put(res.getSymbol(), volumes);
                        }
                    }
                }

                Thread.sleep(100);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("BrapiClient interrupted during sleep");
                break;
            } catch (Exception e) {
                log.error("Erro ao buscar {}: {}", symbol, e.getMessage());
            }
        }
        log.info("Brapi fetch concluído: {} ações carregadas", allTickers.size());
        return allTickers;
    }

    // --- Métodos que o MarketScannerService estava pedindo ---

    public List<BigDecimal> getCloseHistory(String symbol) {
        return closeHistoryMap.getOrDefault(symbol, List.of());
    }

    public List<BigDecimal> getVolumeHistory(String symbol) {
        return volumeHistoryMap.getOrDefault(symbol, List.of());
    }

    private Ticker24hDTO mapToDto(StockResult res) {
        return new Ticker24hDTO(
                res.getSymbol(),
                String.valueOf(res.getRegularMarketDayHigh()),
                String.valueOf(res.getRegularMarketDayLow()),
                String.valueOf(res.getRegularMarketPrice()),
                String.valueOf(res.getRegularMarketVolume())
        );
    }

    @Override
    public String getMarketName() { return "B3"; }

    // --- DTOs Internos para o Jackson ---

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BrapiResponse {
        private List<StockResult> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class StockResult {
        private String symbol;
        private double regularMarketPrice;
        private double regularMarketDayHigh;
        private double regularMarketDayLow;
        private double regularMarketVolume;

        @JsonProperty("historicalDataPrice")
        private List<HistoryItem> historicalData;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HistoryItem {
        private double close;
        private double volume;
    }
}