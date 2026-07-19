package com.evcar.upbit.broker;

import java.util.List;

/**
 * 주문 실행 추상화. PAPER(모의)와 LIVE(실거래) 두 구현이 있다.
 */
public interface Broker {

    String modeName();

    /** 주문 가능한 KRW 잔고 */
    double krwBalance();

    /** 보유 코인 수량 */
    double coinVolume(String market);

    /** 평균 매수가 (미보유 시 0) */
    double avgBuyPrice(String market);

    /** 시장가 매수. krwAmount는 수수료 포함 총 사용 금액. */
    TradeRecord buy(String market, long krwAmount, double currentPrice, String reason);

    /** 보유 수량 전량 시장가 매도 */
    TradeRecord sellAll(String market, double currentPrice, String reason);

    List<TradeRecord> tradeHistory();
}
