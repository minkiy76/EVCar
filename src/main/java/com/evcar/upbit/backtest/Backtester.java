package com.evcar.upbit.backtest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.evcar.upbit.broker.PaperBroker;
import com.evcar.upbit.client.UpbitQuotationClient;
import com.evcar.upbit.config.UpbitProperties;
import com.evcar.upbit.config.UpbitProperties.StrategyType;
import com.evcar.upbit.dto.CandleDto;

import lombok.RequiredArgsConstructor;

/**
 * 과거 일봉 데이터로 전략 성과를 시뮬레이션한다. 수수료(0.05% 왕복) 반영.
 *
 * - 변동성 돌파: 당일 고가가 목표가(시가 + 전일 변동폭*k)를 넘으면 목표가에 매수, 당일 종가에 매도
 * - 이동평균 교차: 골든크로스 다음 날 시가 매수, 데드크로스 다음 날 시가 매도 (종가 기준 판정)
 */
@Component
@RequiredArgsConstructor
public class Backtester {

    private final UpbitQuotationClient quotationClient;
    private final UpbitProperties properties;

    public BacktestResult run(String market, int days, StrategyType strategyType) {
        int count = Math.min(Math.max(days + 25, 30), 200);
        List<CandleDto> candles = new ArrayList<>(quotationClient.getDayCandles(market, count));
        candles.sort(Comparator.comparing(CandleDto::candleDateTimeKst)); // 과거 -> 최신 순으로 정렬

        return switch (strategyType) {
            case VOLATILITY_BREAKOUT -> runVolatilityBreakout(market, candles, days);
            case MA_CROSS -> runMaCross(market, candles, days);
        };
    }

    private BacktestResult runVolatilityBreakout(String market, List<CandleDto> candles, int days) {
        double k = properties.getTrading().getBreakoutK();
        long seed = properties.getTrading().getPaperSeedKrw();
        double balance = seed;
        int trades = 0;
        int wins = 0;
        List<BacktestResult.DailyEquity> curve = new ArrayList<>();
        double peak = balance;
        double maxDrawdown = 0;

        int start = Math.max(1, candles.size() - days);
        for (int i = start; i < candles.size(); i++) {
            CandleDto today = candles.get(i);
            CandleDto yesterday = candles.get(i - 1);
            double target = today.openingPrice() + (yesterday.highPrice() - yesterday.lowPrice()) * k;

            Double dailyReturn = null;
            if (today.highPrice() >= target) {
                // 목표가 돌파 -> 목표가 매수, 종가 매도 (수수료 왕복 0.1%)
                double gross = today.tradePrice() / target;
                double net = gross * (1 - PaperBroker.FEE_RATE) * (1 - PaperBroker.FEE_RATE);
                balance *= net;
                trades++;
                if (net > 1) {
                    wins++;
                }
                dailyReturn = (net - 1) * 100;
            }
            peak = Math.max(peak, balance);
            maxDrawdown = Math.max(maxDrawdown, (peak - balance) / peak * 100);
            curve.add(new BacktestResult.DailyEquity(
                    today.candleDateTimeKst().substring(0, 10), Math.round(balance), dailyReturn));
        }

        return buildResult(market, "변동성 돌파 (k=" + k + ")", days, trades, wins,
                seed, balance, maxDrawdown, curve);
    }

    private BacktestResult runMaCross(String market, List<CandleDto> candles, int days) {
        int shortP = properties.getTrading().getMaShortPeriod();
        int longP = properties.getTrading().getMaLongPeriod();
        long seed = properties.getTrading().getPaperSeedKrw();
        double balance = seed;
        int trades = 0;
        int wins = 0;
        List<BacktestResult.DailyEquity> curve = new ArrayList<>();
        double peak = balance;
        double maxDrawdown = 0;

        boolean holding = false;
        double entryPrice = 0;

        int start = Math.max(longP, candles.size() - days);
        for (int i = start; i < candles.size(); i++) {
            CandleDto today = candles.get(i);
            double shortMa = closeAverage(candles, i, shortP);
            double longMa = closeAverage(candles, i, longP);

            Double dailyReturn = null;
            if (!holding && shortMa > longMa) {
                holding = true;
                entryPrice = today.tradePrice() * (1 + PaperBroker.FEE_RATE);
            } else if (holding && shortMa < longMa) {
                double exit = today.tradePrice() * (1 - PaperBroker.FEE_RATE);
                double net = exit / entryPrice;
                balance *= net;
                trades++;
                if (net > 1) {
                    wins++;
                }
                dailyReturn = (net - 1) * 100;
                holding = false;
            }
            double equity = holding ? balance * (today.tradePrice() / entryPrice) : balance;
            peak = Math.max(peak, equity);
            maxDrawdown = Math.max(maxDrawdown, (peak - equity) / peak * 100);
            curve.add(new BacktestResult.DailyEquity(
                    today.candleDateTimeKst().substring(0, 10), Math.round(equity), dailyReturn));
        }
        // 종료 시점에 보유 중이면 마지막 종가로 청산
        if (holding && !candles.isEmpty()) {
            double exit = candles.get(candles.size() - 1).tradePrice() * (1 - PaperBroker.FEE_RATE);
            double net = exit / entryPrice;
            balance *= net;
            trades++;
            if (net > 1) {
                wins++;
            }
        }

        return buildResult(market, "이동평균 교차 (" + shortP + "/" + longP + ")", days, trades, wins,
                seed, balance, maxDrawdown, curve);
    }

    private static double closeAverage(List<CandleDto> candles, int endInclusive, int period) {
        double sum = 0;
        for (int i = endInclusive - period + 1; i <= endInclusive; i++) {
            sum += candles.get(i).tradePrice();
        }
        return sum / period;
    }

    private static BacktestResult buildResult(String market, String strategyName, int days,
                                              int trades, int wins, long seed, double balance,
                                              double maxDrawdown, List<BacktestResult.DailyEquity> curve) {
        double winRate = trades > 0 ? (double) wins / trades * 100 : 0;
        double totalReturn = (balance / seed - 1) * 100;
        return new BacktestResult(market, strategyName, days, trades, wins,
                round2(winRate), round2(totalReturn), round2(maxDrawdown),
                seed, Math.round(balance), curve);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
