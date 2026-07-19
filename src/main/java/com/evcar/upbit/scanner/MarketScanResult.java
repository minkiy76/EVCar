package com.evcar.upbit.scanner;

import com.evcar.upbit.strategy.TradeSignal;

/**
 * 종목 하나에 대한 차트 분석 결과.
 */
public record MarketScanResult(
        String market,
        String koreanName,
        double price,
        double tradeValue24h,
        double trendMa,        // 추세 판단용 이동평균 (기본 MA20)
        double rsi14,
        double breakoutTarget, // 변동성 돌파 목표가
        boolean trendUp,       // 현재가 > 추세 MA
        boolean overbought,    // RSI >= 상한
        TradeSignal signal,    // 필터까지 통과한 최종 신호
        String note
) {
}
