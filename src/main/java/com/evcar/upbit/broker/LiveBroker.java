package com.evcar.upbit.broker;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.evcar.upbit.client.UpbitAuthTokenFactory;
import com.evcar.upbit.client.UpbitExchangeClient;
import com.evcar.upbit.config.UpbitProperties;
import com.evcar.upbit.dto.AccountDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실거래 브로커. 업비트 거래 API로 실제 시장가 주문을 낸다.
 *
 * 안전장치:
 * - upbit.trading.mode=LIVE 가 아니면 모든 주문 거부
 * - 1회 매수 금액이 max-order-krw 를 넘으면 거부
 * - API 키 미설정 시 거부
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveBroker implements Broker {

    private final UpbitProperties properties;
    private final UpbitExchangeClient exchangeClient;
    private final UpbitAuthTokenFactory tokenFactory;

    private final List<TradeRecord> trades = Collections.synchronizedList(new ArrayList<>());

    @Override
    public String modeName() {
        return "LIVE";
    }

    @Override
    public double krwBalance() {
        return findAccount("KRW").map(a -> Double.parseDouble(a.balance())).orElse(0.0);
    }

    @Override
    public double coinVolume(String market) {
        return findAccount(currencyOf(market)).map(a -> Double.parseDouble(a.balance())).orElse(0.0);
    }

    @Override
    public double avgBuyPrice(String market) {
        return findAccount(currencyOf(market)).map(a -> Double.parseDouble(a.avgBuyPrice())).orElse(0.0);
    }

    @Override
    public TradeRecord buy(String market, long krwAmount, double currentPrice, String reason) {
        assertLiveAllowed();
        long capped = Math.min(krwAmount, properties.getTrading().getMaxOrderKrw());
        if (capped < 5_000) {
            throw new IllegalStateException("업비트 최소 주문 금액(5,000원) 미만입니다: " + capped);
        }
        double available = krwBalance();
        if (available < capped) {
            throw new IllegalStateException("KRW 잔고 부족: 필요 " + capped + "원, 보유 " + Math.round(available) + "원");
        }

        exchangeClient.buyMarketOrder(market, capped);
        double fee = capped * PaperBroker.FEE_RATE;
        double estimatedVolume = (capped - fee) / currentPrice;

        TradeRecord record = new TradeRecord(LocalDateTime.now(), market, "BUY",
                currentPrice, estimatedVolume, capped, fee, null, reason);
        trades.add(record);
        log.warn("[LIVE] 실제 매수 주문 실행: {} {}원 - {}", market, capped, reason);
        return record;
    }

    @Override
    public TradeRecord sellAll(String market, double currentPrice, String reason) {
        assertLiveAllowed();
        double volume = coinVolume(market);
        if (volume <= 0) {
            throw new IllegalStateException("매도할 보유 수량이 없습니다: " + market);
        }
        double avgBuy = avgBuyPrice(market);

        exchangeClient.sellMarketOrder(market, volume);
        double gross = volume * currentPrice;
        double fee = gross * PaperBroker.FEE_RATE;
        double profit = (gross - fee) - avgBuy * volume;

        TradeRecord record = new TradeRecord(LocalDateTime.now(), market, "SELL",
                currentPrice, volume, gross - fee, fee, profit, reason);
        trades.add(record);
        log.warn("[LIVE] 실제 매도 주문 실행: {} {}개 (추정 손익 {}원) - {}", market, volume, Math.round(profit), reason);
        return record;
    }

    @Override
    public List<TradeRecord> tradeHistory() {
        synchronized (trades) {
            return new ArrayList<>(trades);
        }
    }

    private void assertLiveAllowed() {
        if (properties.getTrading().getMode() != UpbitProperties.TradingMode.LIVE) {
            throw new IllegalStateException("LIVE 모드가 아닙니다. 실거래를 원하면 upbit.trading.mode=LIVE 로 설정하세요.");
        }
        if (!tokenFactory.hasKeys()) {
            throw new IllegalStateException("업비트 API 키가 설정되지 않았습니다.");
        }
    }

    private java.util.Optional<AccountDto> findAccount(String currency) {
        List<AccountDto> accounts = exchangeClient.getAccounts();
        if (accounts == null) {
            return java.util.Optional.empty();
        }
        return accounts.stream().filter(a -> currency.equals(a.currency())).findFirst();
    }

    /** "KRW-BTC" -> "BTC" */
    private static String currencyOf(String market) {
        int idx = market.indexOf('-');
        return idx >= 0 ? market.substring(idx + 1) : market;
    }
}
