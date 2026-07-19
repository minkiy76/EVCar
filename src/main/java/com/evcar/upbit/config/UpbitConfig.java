package com.evcar.upbit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(UpbitProperties.class)
public class UpbitConfig {

    @Bean
    public WebClient upbitWebClient(UpbitProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .build();
    }
}
