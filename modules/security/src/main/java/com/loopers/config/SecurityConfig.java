package com.loopers.config;

import com.loopers.security.BCryptPasswordHasher;
import com.loopers.security.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }
}
