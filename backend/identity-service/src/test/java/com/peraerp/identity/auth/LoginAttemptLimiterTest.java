package com.peraerp.identity.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-23T10:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    @Test
    void blocksAfterTooManyFailuresIgnoringCaseAndSpaces() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("Admin");
        }
        assertThat(limiter.isBlocked("admin")).isFalse();

        limiter.recordFailure(" ADMIN ");

        assertThat(limiter.isBlocked("admin")).isTrue();
        assertThat(limiter.isBlocked("other")).isFalse();
    }

    @Test
    void unblocksWhenTheWindowExpires() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("admin");
        }
        clock.advance(LoginAttemptLimiter.WINDOW);

        assertThat(limiter.isBlocked("admin")).isFalse();
        limiter.recordFailure("admin");
        assertThat(limiter.isBlocked("admin")).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        for (int i = 0; i < LoginAttemptLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("admin");
        }
        limiter.recordSuccess("admin");
        limiter.recordFailure("admin");

        assertThat(limiter.isBlocked("admin")).isFalse();
    }
}
