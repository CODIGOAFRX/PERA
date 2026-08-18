package com.peraerp.activity.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceKeyValidator {
    private final byte[] expected;

    public InternalServiceKeyValidator(@Value("${pera.internal.service-key}") String expected) {
        this.expected = expected.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String supplied) {
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8));
    }
}
