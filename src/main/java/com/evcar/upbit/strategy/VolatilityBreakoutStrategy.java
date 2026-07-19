package com.evcar.upbit.strategy;

import java.util.List;

import com.evcar.upbit.dto.CandleDto;

import lombok.RequiredArgsConstructor;

/**
 * 변동성 돌파 전략 (래리 윌리엄스).
 * 목표가 = 당일 시가 + 전일 변동폭(고가-저가) * k
 * 현재가가 목표가를 돌파하면 매수, 보유 중 다음 날이 되면(당일 종료) 매도는 엔진에서 처리.
 */
@RequiredArgsConstructor
public class VolatilityBreakoutStrategy implements TradingStrategy {

    private final double k;

    @Override
    public String name() {
        return "변동성 돌파 (k=" + k + ")";
    }

    @Override
    public TradeSignal decide(List<CandleDto> dayCandles, double currentPrice, boolean holding) {
        if (dayCandles == null || dayCandles.size() < 2) {
            return TradeSignal.HOLD;
        }
        CandleDto today = dayCandles.get(0);
        CandleDto yesterday = dayCandles.get(1);

        double target = targetPrice(today.openingPrice(), yesterday);
        if (!holding && currentPrice >= target) {
            return TradeSignal.BUY;
        }
        return TradeSignal.HOLD;
    }

    public double targetPrice(double todayOpen, CandleDto yesterday) {
        double range = yesterday.highPrice() - yesterday.lowPrice();
        return todayOpen + range * k;
    }
}
