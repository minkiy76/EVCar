package com.evcar.upbit.analysis;

import java.util.List;

import com.evcar.upbit.dto.CandleDto;

/**
 * 차트 지표 계산 유틸. 캔들 리스트는 업비트 API 형식 그대로 최신이 첫 번째다.
 */
public final class ChartIndicators {

    private ChartIndicators() {
    }

    /** 단순 이동평균. 최신 캔들의 종가 대신 현재가를 사용한다. */
    public static double sma(List<CandleDto> newestFirst, int period, double currentPrice) {
        double sum = currentPrice;
        for (int i = 1; i < period; i++) {
            sum += newestFirst.get(i).tradePrice();
        }
        return sum / period;
    }

    /** RSI (Cutler 방식, 단순 평균). 0~100. 데이터 부족 시 50 반환. */
    public static double rsi(List<CandleDto> newestFirst, int period) {
        if (newestFirst.size() < period + 1) {
            return 50;
        }
        double gain = 0;
        double loss = 0;
        for (int i = period; i >= 1; i--) {
            double diff = newestFirst.get(i - 1).tradePrice() - newestFirst.get(i).tradePrice();
            if (diff > 0) {
                gain += diff;
            } else {
                loss -= diff;
            }
        }
        if (loss == 0) {
            return 100;
        }
        double rs = gain / loss;
        return 100 - 100 / (1 + rs);
    }

    /** 변동성 돌파 목표가 = 당일 시가 + 전일 변동폭 * k */
    public static double breakoutTarget(List<CandleDto> newestFirst, double k) {
        if (newestFirst.size() < 2) {
            return Double.MAX_VALUE;
        }
        CandleDto today = newestFirst.get(0);
        CandleDto yesterday = newestFirst.get(1);
        return today.openingPrice() + (yesterday.highPrice() - yesterday.lowPrice()) * k;
    }
}
