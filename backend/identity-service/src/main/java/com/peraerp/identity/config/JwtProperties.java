package com.peraerp.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("pera.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {
}
