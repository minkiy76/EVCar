package com.evcar.upbit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "upbit")
public class UpbitProperties {

    private Api api = new Api();
    private Trading trading = new Trading();

    @Getter
    @Setter
    public static class Api {
        private String baseUrl = "https://api.upbit.com";
        private String accessKey = "";
        private String secretKey = "";
    }

    @Getter
    @Setter
    public static class Trading {
        /** PAPER: 가상 잔고 모의투자, LIVE: 실제 주문 */
        private TradingMode mode = TradingMode.PAPER;
        private String market = "KRW-BTC";
        private StrategyType strategy = StrategyType.VOLATILITY_BREAKOUT;
        private long intervalMs = 60_000;
        private boolean autoStart = false;

        /** 1회 매수 금액 (KRW) */
        private long orderKrw = 10_000;
        /** 1회 매수 금액 상한 — LIVE 모드에서 이 금액을 넘는 주문은 거부 */
        private long maxOrderKrw = 50_000;
        /** 모의투자 시작 잔고 */
        private long paperSeedKrw = 1_000_000;

        /** 손절 기준 (%) — 평균 매수가 대비 하락률 */
        private double stopLossPct = 3.0;
        /** 익절 기준 (%) */
        private double takeProfitPct = 6.0;
        /** 하루 누적 손실이 이 금액을 넘으면 엔진 자동 정지 (KRW) */
        private long dailyLossLimitKrw = 30_000;

        /** 변동성 돌파 계수 k */
        private double breakoutK = 0.5;
        /** 이동평균 교차: 단기/장기 기간 */
        private int maShortPeriod = 5;
        private int maLongPeriod = 20;

        /** true면 차트 분석 스캔으로 종목을 자동 선택, false면 market 고정 */
        private boolean autoSelect = true;
        private Scanner scanner = new Scanner();
    }

    @Getter
    @Setter
    public static class Scanner {
        /**
         * 스캔 후보 종목. 비워두면 KRW 마켓 전체에서 24시간 거래대금 상위 pool-size개를 자동 선정.
         * 기본값은 급등락 위험이 상대적으로 낮은 메이저 코인.
         */
        private java.util.List<String> markets = java.util.List.of(
                "KRW-BTC", "KRW-ETH", "KRW-SOL", "KRW-XRP", "KRW-ADA",
                "KRW-DOGE", "KRW-AVAX", "KRW-LINK", "KRW-DOT", "KRW-TRX");
        /** 동적 선정 시 후보 수 */
        private int poolSize = 10;
        /** RSI가 이 값 이상이면 과열로 보고 매수 제외 */
        private double rsiMax = 70;
        /** 추세 필터 이동평균 기간 (현재가가 이 MA 위에 있어야 매수) */
        private int trendMaPeriod = 20;
    }

    public enum TradingMode { PAPER, LIVE }

    public enum StrategyType { VOLATILITY_BREAKOUT, MA_CROSS }
}
