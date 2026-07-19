package com.evcar.upbit.backtest;

import java.util.List;

public record BacktestResult(
        String market,
        String strategy,
        int days,
        int tradeCount,
        int winCount,
        double winRatePct,
        double totalReturnPct,
        double maxDrawdownPct,
        long startBalanceKrw,
        long endBalanceKrw,
        List<DailyEquity> equityCurve
) {
    public record DailyEquity(String date, long balanceKrw, Double dailyReturnPct) {
    }
}
