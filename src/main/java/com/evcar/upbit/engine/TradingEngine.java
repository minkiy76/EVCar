package com.evcar.upbit.engine;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.evcar.upbit.broker.Broker;
import com.evcar.upbit.broker.LiveBroker;
import com.evcar.upbit.broker.PaperBroker;
import com.evcar.upbit.broker.TradeRecord;
import com.evcar.upbit.client.UpbitQuotationClient;
import com.evcar.upbit.config.UpbitProperties;
import com.evcar.upbit.config.UpbitProperties.StrategyType;
import com.evcar.upbit.config.UpbitProperties.TradingMode;
import com.evcar.upbit.dto.CandleDto;
import com.evcar.upbit.dto.TickerDto;
import com.evcar.upbit.strategy.MovingAverageCrossStrategy;
import com.evcar.upbit.strategy.TradeSignal;
import com.evcar.upbit.strategy.TradingStrategy;
import com.evcar.upbit.strategy.VolatilityBreakoutStrategy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동매매 엔진. 주기적으로 시세를 확인해 전략 신호에 따라 매매한다.
 *
 * 리스크 관리 (전략과 무관하게 항상 적용):
 * - 손절: 평균 매수가 대비 stop-loss-pct 하락 시 전량 매도
 * - 익절: take-profit-pct 상승 시 전량 매도
 * - 일일 손실 한도: 당일 실현 손실이 daily-loss-limit-krw 초과 시 엔진 자동 정지
 * - 변동성 돌파 전략은 날짜(KST)가 바뀌면 보유분을 정리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingEngine {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UpbitProperties properties;
    private final UpbitQuotationClient quotationClient;
    private final PaperBroker paperBroker;
    private final LiveBroker liveBroker;

    private volatile boolean running = false;
    private volatile String lastMessage = "대기 중";
    private volatile String lastCheckedAt = "-";
    private TradingStrategy strategy;
    private LocalDate entryDate;

    @PostConstruct
    void init() {
        strategy = buildStrategy(properties.getTrading().getStrategy());
        if (properties.getTrading().isAutoStart()) {
            start();
        }
    }

    public synchronized void start() {
        strategy = buildStrategy(properties.getTrading().getStrategy());
        running = true;
        lastMessage = "엔진 시작 (" + broker().modeName() + " 모드, " + strategy.name() + ")";
        log.info("[Trading] {}", lastMessage);
    }

    public synchronized void stop() {
        running = false;
        lastMessage = "엔진 정지";
        log.info("[Trading] 엔진 정지");
    }

    @Scheduled(fixedDelayString = "${upbit.trading.interval-ms:60000}")
    public void tick() {
        if (!running) {
            return;
        }
        String market = properties.getTrading().getMarket();
        try {
            lastCheckedAt = java.time.LocalDateTime.now(KST).toString();

            if (dailyRealizedLoss() >= properties.getTrading().getDailyLossLimitKrw()) {
                stop();
                lastMessage = "일일 손실 한도 초과로 자동 정지 (한도 "
                        + properties.getTrading().getDailyLossLimitKrw() + "원)";
                log.warn("[Trading] {}", lastMessage);
                return;
            }

            TickerDto ticker = quotationClient.getTicker(market);
            List<CandleDto> dayCandles = quotationClient.getDayCandles(market, 30);
            double price = ticker.tradePrice();
            Broker broker = broker();
            boolean holding = broker.coinVolume(market) > 0;

            // 1) 리스크 관리 우선
            if (holding && applyRiskExit(broker, market, price)) {
                return;
            }

            // 2) 변동성 돌파: 날짜가 바뀌면 보유분 정리 (당일 청산 전략)
            if (holding && strategy instanceof VolatilityBreakoutStrategy
                    && entryDate != null && !LocalDate.now(KST).equals(entryDate)) {
                broker.sellAll(market, price, "일 변경 청산 (변동성 돌파)");
                entryDate = null;
                lastMessage = "일 변경 청산 매도 @" + price;
                return;
            }

            // 3) 전략 신호
            TradeSignal signal = strategy.decide(dayCandles, price, holding);
            switch (signal) {
                case BUY -> {
                    broker.buy(market, properties.getTrading().getOrderKrw(), price, strategy.name() + " 매수 신호");
                    entryDate = LocalDate.now(KST);
                    lastMessage = "매수 체결 @" + price;
                }
                case SELL -> {
                    broker.sellAll(market, price, strategy.name() + " 매도 신호");
                    entryDate = null;
                    lastMessage = "매도 체결 @" + price;
                }
                case HOLD -> lastMessage = holding
                        ? "보유 유지 (현재가 " + price + ")"
                        : "매수 신호 대기 (현재가 " + price + ")";
            }
        } catch (Exception e) {
            lastMessage = "오류: " + e.getMessage();
            log.error("[Trading] tick 실패", e);
        }
    }

    /** 손절/익절 처리. 매도했으면 true */
    private boolean applyRiskExit(Broker broker, String market, double price) {
        double avgBuy = broker.avgBuyPrice(market);
        if (avgBuy <= 0) {
            return false;
        }
        double changePct = (price - avgBuy) / avgBuy * 100.0;
        if (changePct <= -properties.getTrading().getStopLossPct()) {
            broker.sellAll(market, price, String.format("손절 (%.2f%%)", changePct));
            entryDate = null;
            lastMessage = "손절 매도 @" + price;
            return true;
        }
        if (changePct >= properties.getTrading().getTakeProfitPct()) {
            broker.sellAll(market, price, String.format("익절 (+%.2f%%)", changePct));
            entryDate = null;
            lastMessage = "익절 매도 @" + price;
            return true;
        }
        return false;
    }

    /** 당일(KST) 실현 손실 합계 (손실만 양수로 집계) */
    private double dailyRealizedLoss() {
        LocalDate today = LocalDate.now(KST);
        return broker().tradeHistory().stream()
                .filter(t -> t.profitKrw() != null && t.profitKrw() < 0)
                .filter(t -> t.executedAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(KST)
                        .toLocalDate().equals(today))
                .mapToDouble(t -> -t.profitKrw())
                .sum();
    }

    public Broker broker() {
        return properties.getTrading().getMode() == TradingMode.LIVE ? liveBroker : paperBroker;
    }

    private TradingStrategy buildStrategy(StrategyType type) {
        UpbitProperties.Trading t = properties.getTrading();
        return switch (type) {
            case VOLATILITY_BREAKOUT -> new VolatilityBreakoutStrategy(t.getBreakoutK());
            case MA_CROSS -> new MovingAverageCrossStrategy(t.getMaShortPeriod(), t.getMaLongPeriod());
        };
    }

    public Map<String, Object> status() {
        String market = properties.getTrading().getMarket();
        Broker broker = broker();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running);
        status.put("mode", broker.modeName());
        status.put("market", market);
        status.put("strategy", strategy.name());
        status.put("lastMessage", lastMessage);
        status.put("lastCheckedAt", lastCheckedAt);
        try {
            status.put("krwBalance", Math.round(broker.krwBalance()));
            status.put("coinVolume", broker.coinVolume(market));
            status.put("avgBuyPrice", broker.avgBuyPrice(market));
        } catch (Exception e) {
            status.put("balanceError", e.getMessage());
        }
        List<TradeRecord> history = broker.tradeHistory();
        double realized = history.stream()
                .filter(t -> t.profitKrw() != null)
                .mapToDouble(TradeRecord::profitKrw)
                .sum();
        status.put("tradeCount", history.size());
        status.put("realizedProfitKrw", Math.round(realized));
        return status;
    }
}
