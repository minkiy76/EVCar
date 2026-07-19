package com.evcar.upbit.client;

import java.time.Duration;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.evcar.upbit.dto.CandleDto;
import com.evcar.upbit.dto.MarketDto;
import com.evcar.upbit.dto.TickerDto;

import lombok.RequiredArgsConstructor;

/**
 * 업비트 시세(Quotation) API 클라이언트. 인증 없이 호출 가능.
 */
@Component
@RequiredArgsConstructor
public class UpbitQuotationClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** 시세 API는 초당 10회 제한 — 호출 간 최소 간격을 둬서 초과(429)를 예방한다 */
    private static final long MIN_REQUEST_GAP_MS = 150;

    private final WebClient upbitWebClient;
    private long lastRequestAt = 0;

    private synchronized void throttle() {
        long wait = lastRequestAt + MIN_REQUEST_GAP_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    public TickerDto getTicker(String market) {
        throttle();
        List<TickerDto> tickers = upbitWebClient.get()
                .uri(uri -> uri.path("/v1/ticker").queryParam("markets", market).build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<TickerDto>>() {})
                .block(TIMEOUT);
        if (tickers == null || tickers.isEmpty()) {
            throw new IllegalStateException("업비트 현재가 조회 실패: " + market);
        }
        return tickers.get(0);
    }

    /** 복수 종목 현재가 일괄 조회 (1회 호출) */
    public List<TickerDto> getTickers(List<String> markets) {
        throttle();
        String joined = String.join(",", markets);
        List<TickerDto> tickers = upbitWebClient.get()
                .uri(uri -> uri.path("/v1/ticker").queryParam("markets", joined).build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<TickerDto>>() {})
                .block(TIMEOUT);
        return tickers != null ? tickers : List.of();
    }

    /** 거래 가능한 전체 마켓 목록 */
    public List<MarketDto> getMarkets() {
        throttle();
        List<MarketDto> markets = upbitWebClient.get()
                .uri("/v1/market/all")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<MarketDto>>() {})
                .block(TIMEOUT);
        return markets != null ? markets : List.of();
    }

    /** 일봉 조회. 최신 캔들이 리스트의 첫 번째로 온다. count 최대 200. */
    public List<CandleDto> getDayCandles(String market, int count) {
        throttle();
        return upbitWebClient.get()
                .uri(uri -> uri.path("/v1/candles/days")
                        .queryParam("market", market)
                        .queryParam("count", count)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<CandleDto>>() {})
                .block(TIMEOUT);
    }

    /** 분봉 조회. unit: 1, 3, 5, 15, 30, 60, 240 */
    public List<CandleDto> getMinuteCandles(String market, int unit, int count) {
        throttle();
        return upbitWebClient.get()
                .uri(uri -> uri.path("/v1/candles/minutes/" + unit)
                        .queryParam("market", market)
                        .queryParam("count", count)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<CandleDto>>() {})
                .block(TIMEOUT);
    }
}
