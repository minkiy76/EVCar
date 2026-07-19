package com.evcar.upbit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponseDto(
        @JsonProperty("uuid") String uuid,
        @JsonProperty("side") String side,
        @JsonProperty("ord_type") String ordType,
        @JsonProperty("price") String price,
        @JsonProperty("volume") String volume,
        @JsonProperty("state") String state,
        @JsonProperty("market") String market,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("executed_volume") String executedVolume,
        @JsonProperty("paid_fee") String paidFee
) {
}
