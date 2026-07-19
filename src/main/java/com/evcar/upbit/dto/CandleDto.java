package com.evcar.upbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CandleDto(
        @JsonProperty("market") String market,
        @JsonProperty("candle_date_time_kst") String candleDateTimeKst,
        @JsonProperty("opening_price") double openingPrice,
        @JsonProperty("high_price") double highPrice,
        @JsonProperty("low_price") double lowPrice,
        @JsonProperty("trade_price") double tradePrice,
        @JsonProperty("candle_acc_trade_price") double accTradePrice,
        @JsonProperty("timestamp") long timestamp
) {
}
