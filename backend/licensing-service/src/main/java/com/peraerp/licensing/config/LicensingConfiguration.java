package com.peraerp.licensing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
public class LicensingConfiguration {
    @Bean
    Clock licensingClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom licensingSecureRandom() {
        return new SecureRandom();
    }
}
