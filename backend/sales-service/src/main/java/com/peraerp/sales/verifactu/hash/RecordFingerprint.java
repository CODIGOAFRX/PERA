package com.peraerp.sales.verifactu.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Huella («hash») de un registro de facturación Veri*Factu: SHA-256 sobre la cadena canónica,
 * expresada en hexadecimal con letras en mayúsculas (64 caracteres).
 */
public final class RecordFingerprint {

    /** Longitud de la representación hexadecimal de un SHA-256. */
    public static final int LENGTH = 64;

    private RecordFingerprint() {
    }

    public static String of(FingerprintInput input) {
        if (input == null) {
            throw new IllegalArgumentException("La entrada de la huella es obligatoria.");
        }
        return of(input.asString());
    }

    public static String of(String canonicalInput) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno está roto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", e);
        }
        byte[] hash = digest.digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().withUpperCase().formatHex(hash);
    }
}
