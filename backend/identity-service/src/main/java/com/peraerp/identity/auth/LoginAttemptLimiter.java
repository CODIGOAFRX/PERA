package com.peraerp.identity.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits failed logins per username to slow down password guessing. In-memory by design:
 * a restart clears the counters, which is acceptable for a single identity-service instance.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private record Attempts(int failures, Instant firstFailure) {
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String username) {
        Attempts current = attempts.get(key(username));
        if (current == null) {
            return false;
        }
        if (expired(current)) {
            attempts.remove(key(username), current);
            return false;
        }
        return current.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String username) {
        if (attempts.size() >= MAX_TRACKED_USERNAMES) {
            attempts.values().removeIf(this::expired);
        }
        Instant now = clock.instant();
        attempts.merge(key(username), new Attempts(1, now), (previous, ignored) -> expired(previous)
                ? new Attempts(1, now)
                : new Attempts(previous.failures() + 1, previous.firstFailure()));
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private boolean expired(Attempts value) {
        return !value.firstFailure().plus(WINDOW).isAfter(clock.instant());
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
