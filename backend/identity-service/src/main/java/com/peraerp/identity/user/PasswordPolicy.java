package com.peraerp.identity.user;

import com.peraerp.platform.domain.BusinessRuleException;

import java.nio.charset.StandardCharsets;

/** BCrypt only uses the first 72 bytes; longer passwords are rejected instead of being silently truncated. */
public final class PasswordPolicy {
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    public static void requireBcryptLength(String password) {
        if (!fitsBcrypt(password)) {
            throw new BusinessRuleException("La contraseña no puede superar 72 bytes. Las letras con tilde y la ñ ocupan dos.");
        }
    }
}
