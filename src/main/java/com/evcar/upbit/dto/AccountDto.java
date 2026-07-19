package com.evcar.upbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(
        @JsonProperty("currency") String currency,
        @JsonProperty("balance") String balance,
        @JsonProperty("locked") String locked,
        @JsonProperty("avg_buy_price") String avgBuyPrice,
        @JsonProperty("unit_currency") String unitCurrency
) {
}
