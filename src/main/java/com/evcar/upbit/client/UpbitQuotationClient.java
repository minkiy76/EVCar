package com.evcar.upbit.client;

import java.time.Duration;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.evcar.upbit.dto.CandleDto;
import com.evcar.upbit.dto.TickerDto;

import lombok.RequiredArgsConstructor;

/**
 * 업비트 시세(Quotation) API 클라이언트. 인증 없이 호출 가능.
 */
@Component
@RequiredArgsConstructor
public class UpbitQuotationClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient upbitWebClient;

    public TickerDto getTicker(String market) {
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

    /** 일봉 조회. 최신 캔들이 리스트의 첫 번째로 온다. count 최대 200. */
    public List<CandleDto> getDayCandles(String market, int count) {
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
