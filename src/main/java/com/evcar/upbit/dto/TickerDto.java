package com.evcar.upbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TickerDto(
        @JsonProperty("market") String market,
        @JsonProperty("trade_price") double tradePrice,
        @JsonProperty("opening_price") double openingPrice,
        @JsonProperty("high_price") double highPrice,
        @JsonProperty("low_price") double lowPrice,
        @JsonProperty("prev_closing_price") double prevClosingPrice,
        @JsonProperty("signed_change_rate") double signedChangeRate,
        @JsonProperty("acc_trade_price_24h") double accTradePrice24h,
        @JsonProperty("timestamp") long timestamp
) {
}
