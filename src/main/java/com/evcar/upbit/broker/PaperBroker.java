package com.evcar.upbit.broker;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.evcar.upbit.config.UpbitProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모의투자 브로커. 실제 주문 없이 가상 잔고로 체결을 시뮬레이션한다.
 * 업비트 수수료(0.05%)를 동일하게 반영한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperBroker implements Broker {

    public static final double FEE_RATE = 0.0005;

    private final UpbitProperties properties;

    private final List<TradeRecord> trades = Collections.synchronizedList(new ArrayList<>());
    private double krw;
    private double volume;
    private double avgBuyPrice;
    private String heldMarket;

    @PostConstruct
    public void reset() {
        krw = properties.getTrading().getPaperSeedKrw();
        volume = 0;
        avgBuyPrice = 0;
        heldMarket = null;
        trades.clear();
    }

    @Override
    public String modeName() {
        return "PAPER";
    }

    @Override
    public synchronized double krwBalance() {
        return krw;
    }

    @Override
    public synchronized double coinVolume(String market) {
        return market.equals(heldMarket) ? volume : 0;
    }

    @Override
    public synchronized double avgBuyPrice(String market) {
        return market.equals(heldMarket) ? avgBuyPrice : 0;
    }

    @Override
    public synchronized TradeRecord buy(String market, long krwAmount, double currentPrice, String reason) {
        double spend = Math.min(krwAmount, krw);
        if (spend < 5_000) {
            throw new IllegalStateException("모의투자 잔고 부족: 최소 주문 금액 5,000원 미만 (잔고 " + Math.round(krw) + "원)");
        }
        double fee = spend * FEE_RATE;
        double bought = (spend - fee) / currentPrice;

        double totalCost = avgBuyPrice * volume + (spend - fee);
        volume += bought;
        avgBuyPrice = totalCost / volume;
        heldMarket = market;
        krw -= spend;

        TradeRecord record = new TradeRecord(LocalDateTime.now(), market, "BUY",
                currentPrice, bought, spend, fee, null, reason);
        trades.add(record);
        log.info("[PAPER] 매수 {} {}개 @{} ({}원) - {}", market, bought, currentPrice, Math.round(spend), reason);
        return record;
    }

    @Override
    public synchronized TradeRecord sellAll(String market, double currentPrice, String reason) {
        if (!market.equals(heldMarket) || volume <= 0) {
            throw new IllegalStateException("매도할 보유 수량이 없습니다: " + market);
        }
        double gross = volume * currentPrice;
        double fee = gross * FEE_RATE;
        double net = gross - fee;
        double cost = avgBuyPrice * volume;
        double profit = net - cost;

        TradeRecord record = new TradeRecord(LocalDateTime.now(), market, "SELL",
                currentPrice, volume, net, fee, profit, reason);
        trades.add(record);
        log.info("[PAPER] 매도 {} {}개 @{} (손익 {}원) - {}", market, volume, currentPrice, Math.round(profit), reason);

        krw += net;
        volume = 0;
        avgBuyPrice = 0;
        heldMarket = null;
        return record;
    }

    @Override
    public List<TradeRecord> tradeHistory() {
        synchronized (trades) {
            return new ArrayList<>(trades);
        }
    }
}
