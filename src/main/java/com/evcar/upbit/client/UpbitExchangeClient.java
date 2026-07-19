package com.evcar.upbit.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.evcar.upbit.config.UpbitProperties;
import com.evcar.upbit.dto.AccountDto;
import com.evcar.upbit.dto.OrderResponseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 업비트 거래(Exchange) API 클라이언트. API 키(JWT 인증)가 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitExchangeClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient upbitWebClient;
    private final UpbitAuthTokenFactory tokenFactory;
    private final UpbitProperties properties;

    /** 전체 계좌(잔고) 조회 */
    public List<AccountDto> getAccounts() {
        return upbitWebClient.get()
                .uri("/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, tokenFactory.createToken())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<AccountDto>>() {})
                .block(TIMEOUT);
    }

    /**
     * 시장가 매수: KRW 금액을 지정해 즉시 체결 (ord_type=price)
     */
    public OrderResponseDto buyMarketOrder(String market, long krwAmount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("market", market);
        params.put("side", "bid");
        params.put("price", String.valueOf(krwAmount));
        params.put("ord_type", "price");
        return placeOrder(params);
    }

    /**
     * 시장가 매도: 코인 수량을 지정해 즉시 체결 (ord_type=market)
     */
    public OrderResponseDto sellMarketOrder(String market, double volume) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("market", market);
        params.put("side", "ask");
        params.put("volume", String.format("%.8f", volume));
        params.put("ord_type", "market");
        return placeOrder(params);
    }

    /**
     * 주문 실행. 주문은 실패 시 절대 자동 재시도하지 않는다 (중복 주문 위험).
     * SMP(자전거래 방지) 타입이 설정돼 있으면 주문에 포함한다.
     */
    private OrderResponseDto placeOrder(Map<String, String> params) {
        String smpType = properties.getTrading().getSmpType();
        if (smpType != null && !smpType.isBlank()) {
            params.put("smp_type", smpType);
        }
        OrderResponseDto response = upbitWebClient.post()
                .uri("/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, tokenFactory.createToken(params))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(OrderResponseDto.class)
                .block(TIMEOUT);
        if (response != null) {
            log.info("[Upbit] 주문 응답 uuid={} state={} market={} side={}",
                    response.uuid(), response.state(), response.market(), response.side());
        }
        return response;
    }
}
