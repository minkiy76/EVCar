package com.evcar.upbit.scanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.evcar.upbit.analysis.ChartIndicators;
import com.evcar.upbit.client.UpbitQuotationClient;
import com.evcar.upbit.config.UpbitProperties;
import com.evcar.upbit.dto.CandleDto;
import com.evcar.upbit.dto.MarketDto;
import com.evcar.upbit.dto.TickerDto;
import com.evcar.upbit.strategy.TradeSignal;
import com.evcar.upbit.strategy.TradingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 후보 종목들의 차트를 분석해 매수 대상을 고른다.
 *
 * 절차:
 * 1. 후보군 결정 — 설정된 종목 목록, 비어 있으면 KRW 마켓 거래대금 상위 N개
 * 2. 종목별 지표 계산 — 추세 MA, RSI(14), 변동성 돌파 목표가
 * 3. 전략 신호 + 필터(상승 추세, RSI 비과열) 통과 종목만 매수 후보
 * 4. 후보가 여럿이면 24시간 거래대금이 큰 종목 우선 (유동성)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketScanner {

    private static final long CANDLE_CACHE_TTL_MS = 5 * 60 * 1000;

    private final UpbitQuotationClient quotationClient;
    private final UpbitProperties properties;

    private final Map<String, CachedCandles> candleCache = new ConcurrentHashMap<>();
    private volatile Map<String, String> marketNames;

    private record CachedCandles(List<CandleDto> candles, long fetchedAt) {
    }

    /** 전 종목 스캔 결과 (매수 후보 우선, 이후 거래대금 순) */
    public List<MarketScanResult> scan(TradingStrategy strategy) {
        UpbitProperties.Scanner cfg = properties.getTrading().getScanner();
        List<String> candidates = resolveCandidates(cfg);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = koreanNames();
        List<TickerDto> tickers = quotationClient.getTickers(candidates);

        List<MarketScanResult> results = new ArrayList<>();
        for (TickerDto ticker : tickers) {
            try {
                results.add(analyze(ticker, names, strategy, cfg));
            } catch (Exception e) {
                log.warn("[Scanner] {} 분석 실패: {}", ticker.market(), e.getMessage());
            }
        }
        results.sort(Comparator
                .comparing((MarketScanResult r) -> r.signal() != TradeSignal.BUY)
                .thenComparing(MarketScanResult::tradeValue24h, Comparator.reverseOrder()));
        return results;
    }

    /** 매수 신호가 뜬 종목 중 최우선 후보 */
    public Optional<MarketScanResult> findBuyCandidate(TradingStrategy strategy) {
        return scan(strategy).stream()
                .filter(r -> r.signal() == TradeSignal.BUY)
                .findFirst();
    }

    private MarketScanResult analyze(TickerDto ticker, Map<String, String> names,
                                     TradingStrategy strategy, UpbitProperties.Scanner cfg) {
        String market = ticker.market();
        double price = ticker.tradePrice();
        List<CandleDto> candles = dayCandles(market);

        double trendMa = ChartIndicators.sma(candles, cfg.getTrendMaPeriod(), price);
        double rsi = ChartIndicators.rsi(candles, 14);
        double target = ChartIndicators.breakoutTarget(candles, properties.getTrading().getBreakoutK());
        boolean trendUp = price > trendMa;
        boolean overbought = rsi >= cfg.getRsiMax();

        TradeSignal raw = strategy.decide(candles, price, false);
        TradeSignal signal;
        String note;
        if (raw != TradeSignal.BUY) {
            signal = TradeSignal.HOLD;
            note = "신호 대기";
        } else if (!trendUp) {
            signal = TradeSignal.HOLD;
            note = "MA" + cfg.getTrendMaPeriod() + " 아래 (하락 추세 제외)";
        } else if (overbought) {
            signal = TradeSignal.HOLD;
            note = String.format("RSI 과열 (%.0f)", rsi);
        } else {
            signal = TradeSignal.BUY;
            note = "매수 신호";
        }
        return new MarketScanResult(market, names.getOrDefault(market, market), price,
                ticker.accTradePrice24h(), trendMa, Math.round(rsi * 10) / 10.0,
                target, trendUp, overbought, signal, note);
    }

    private List<String> resolveCandidates(UpbitProperties.Scanner cfg) {
        List<String> configured = cfg.getMarkets();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        // 동적 선정: KRW 마켓 전체에서 24시간 거래대금 상위 N개
        List<String> krwMarkets = quotationClient.getMarkets().stream()
                .map(MarketDto::market)
                .filter(m -> m.startsWith("KRW-"))
                .toList();
        return quotationClient.getTickers(krwMarkets).stream()
                .sorted(Comparator.comparing(TickerDto::accTradePrice24h, Comparator.reverseOrder()))
                .limit(cfg.getPoolSize())
                .map(TickerDto::market)
                .toList();
    }

    private List<CandleDto> dayCandles(String market) {
        long now = System.currentTimeMillis();
        CachedCandles cached = candleCache.get(market);
        if (cached != null && now - cached.fetchedAt() < CANDLE_CACHE_TTL_MS) {
            return cached.candles();
        }
        List<CandleDto> candles = quotationClient.getDayCandles(market, 30);
        candleCache.put(market, new CachedCandles(candles, now));
        return candles;
    }

    private Map<String, String> koreanNames() {
        Map<String, String> names = marketNames;
        if (names == null) {
            try {
                names = quotationClient.getMarkets().stream()
                        .collect(Collectors.toMap(MarketDto::market, MarketDto::koreanName, (a, b) -> a));
            } catch (Exception e) {
                log.warn("[Scanner] 마켓 목록 조회 실패: {}", e.getMessage());
                names = Map.of();
            }
            marketNames = names;
        }
        return names;
    }
}
