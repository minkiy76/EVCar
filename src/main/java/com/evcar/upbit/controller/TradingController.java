package com.evcar.upbit.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.evcar.upbit.backtest.BacktestResult;
import com.evcar.upbit.backtest.Backtester;
import com.evcar.upbit.broker.TradeRecord;
import com.evcar.upbit.client.UpbitQuotationClient;
import com.evcar.upbit.config.UpbitProperties;
import com.evcar.upbit.config.UpbitProperties.StrategyType;
import com.evcar.upbit.dto.TickerDto;
import com.evcar.upbit.engine.TradingEngine;
import com.evcar.upbit.scanner.MarketScanResult;
import com.evcar.upbit.scanner.MarketScanner;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class TradingController {

    private final TradingEngine engine;
    private final Backtester backtester;
    private final MarketScanner scanner;
    private final UpbitQuotationClient quotationClient;
    private final UpbitProperties properties;

    @GetMapping("/trading")
    public String dashboard() {
        return "trading/dashboard";
    }

    @GetMapping("/api/trading/status")
    @ResponseBody
    public Map<String, Object> status() {
        return engine.status();
    }

    @PostMapping("/api/trading/start")
    @ResponseBody
    public Map<String, Object> start() {
        engine.start();
        return engine.status();
    }

    @PostMapping("/api/trading/stop")
    @ResponseBody
    public Map<String, Object> stop() {
        engine.stop();
        return engine.status();
    }

    @GetMapping("/api/trading/trades")
    @ResponseBody
    public List<TradeRecord> trades() {
        return engine.broker().tradeHistory();
    }

    /** 후보 종목 차트 분석 결과 (매수 후보 우선 정렬) */
    @GetMapping("/api/trading/scan")
    @ResponseBody
    public List<MarketScanResult> scan() {
        return scanner.scan(engine.currentStrategy());
    }

    @GetMapping("/api/trading/ticker")
    @ResponseBody
    public TickerDto ticker(@RequestParam(required = false) String market) {
        String target = market != null ? market : properties.getTrading().getMarket();
        return quotationClient.getTicker(target);
    }

    @GetMapping("/api/trading/backtest")
    @ResponseBody
    public ResponseEntity<BacktestResult> backtest(
            @RequestParam(required = false) String market,
            @RequestParam(defaultValue = "100") int days,
            @RequestParam(required = false) StrategyType strategy) {
        String targetMarket = market != null ? market : properties.getTrading().getMarket();
        StrategyType targetStrategy = strategy != null ? strategy : properties.getTrading().getStrategy();
        int cappedDays = Math.min(Math.max(days, 10), 180);
        return ResponseEntity.ok(backtester.run(targetMarket, cappedDays, targetStrategy));
    }
}
