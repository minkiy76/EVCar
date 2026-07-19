package com.evcar.upbit.broker;

import java.time.LocalDateTime;

/**
 * 체결 내역 한 건. profitKrw는 매도 시에만 채워진다.
 */
public record TradeRecord(
        LocalDateTime executedAt,
        String market,
        String side,          // BUY / SELL
        double price,
        double volume,
        double amountKrw,
        double feeKrw,
        Double profitKrw,     // 매도 시 실현 손익 (수수료 반영)
        String reason         // 매매 사유 (전략 신호, 손절, 익절 등)
) {
}
