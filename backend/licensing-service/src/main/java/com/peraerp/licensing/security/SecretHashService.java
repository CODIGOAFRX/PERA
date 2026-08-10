package com.peraerp.licensing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecretHashService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SECRET_BYTES = 32;
    private static final byte[] DUMMY_HASH = new byte[32];

    private final byte[] pepper;
    private final SecureRandom secureRandom;

    public SecretHashService(@Value("${pera.license.hash-pepper}") String pepper, SecureRandom secureRandom) {
        if (pepper == null || pepper.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("pera.license.hash-pepper debe contener al menos 32 bytes.");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8).clone();
        this.secureRandom = secureRandom;
    }

    public String generateActivationCode() {
        return "PERA-" + randomSecret();
    }

    public String generateInstallationToken() {
        return "perat_" + randomSecret();
    }

    public byte[] hashActivationCode(String activationCode) {
        return hash("activation", activationCode);
    }

    public byte[] hashInstallationToken(String token) {
        return hash("token", token);
    }

    public byte[] hashInstallationFingerprint(String installationId) {
        return hash("installation", installationId);
    }

    public boolean activationCodeMatches(String candidate, byte[] expectedHash) {
        return matches(hashActivationCode(candidate), expectedHash);
    }

    public boolean installationTokenMatches(String candidate, byte[] expectedHash) {
        return matches(hashInstallationToken(candidate), expectedHash);
    }

    public boolean installationFingerprintMatches(String candidate, byte[] expectedHash) {
        return matches(hashInstallationFingerprint(candidate), expectedHash);
    }

    public void consumeDummyComparison(byte[] candidateHash) {
        MessageDigest.isEqual(candidateHash, DUMMY_HASH);
    }

    private String randomSecret() {
        byte[] random = new byte[SECRET_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private byte[] hash(String domain, String value) {
        if (value == null) {
            value = "";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            return mac.doFinal((domain + '\u0000' + value).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo inicializar el hash de secretos.", exception);
        }
    }

    private boolean matches(byte[] candidateHash, byte[] expectedHash) {
        byte[] safeExpected = expectedHash == null ? DUMMY_HASH : expectedHash;
        return MessageDigest.isEqual(candidateHash, safeExpected);
    }
}
