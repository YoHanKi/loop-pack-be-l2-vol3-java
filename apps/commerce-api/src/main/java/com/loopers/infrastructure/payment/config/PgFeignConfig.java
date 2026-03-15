package com.loopers.infrastructure.payment.config;

import com.loopers.infrastructure.payment.pg.PgErrorDecoder;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class PgFeignConfig {

    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new PgErrorDecoder();
    }
}
