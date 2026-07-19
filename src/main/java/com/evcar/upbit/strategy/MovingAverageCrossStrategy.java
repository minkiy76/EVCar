package com.evcar.upbit.strategy;

import java.util.List;

import com.evcar.upbit.dto.CandleDto;

import lombok.RequiredArgsConstructor;

/**
 * 이동평균 교차 전략.
 * 단기 이동평균이 장기 이동평균 위로 올라오면(골든크로스) 매수,
 * 아래로 내려가면(데드크로스) 매도.
 */
@RequiredArgsConstructor
public class MovingAverageCrossStrategy implements TradingStrategy {

    private final int shortPeriod;
    private final int longPeriod;

    @Override
    public String name() {
        return "이동평균 교차 (" + shortPeriod + "/" + longPeriod + ")";
    }

    @Override
    public TradeSignal decide(List<CandleDto> dayCandles, double currentPrice, boolean holding) {
        if (dayCandles == null || dayCandles.size() < longPeriod) {
            return TradeSignal.HOLD;
        }
        double shortMa = movingAverage(dayCandles, shortPeriod, currentPrice);
        double longMa = movingAverage(dayCandles, longPeriod, currentPrice);

        if (!holding && shortMa > longMa) {
            return TradeSignal.BUY;
        }
        if (holding && shortMa < longMa) {
            return TradeSignal.SELL;
        }
        return TradeSignal.HOLD;
    }

    /** 최신 캔들의 종가 대신 현재가를 사용해 period개 종가 평균을 구한다. */
    private double movingAverage(List<CandleDto> candles, int period, double currentPrice) {
        double sum = currentPrice;
        for (int i = 1; i < period; i++) {
            sum += candles.get(i).tradePrice();
        }
        return sum / period;
    }
}
