package com.evcar.upbit.strategy;

import java.util.List;

import com.evcar.upbit.dto.CandleDto;

public interface TradingStrategy {

    String name();

    /**
     * 매매 신호 판단.
     *
     * @param dayCandles   일봉 목록 (최신이 첫 번째)
     * @param currentPrice 현재가
     * @param holding      현재 코인을 보유 중인지
     */
    TradeSignal decide(List<CandleDto> dayCandles, double currentPrice, boolean holding);
}
