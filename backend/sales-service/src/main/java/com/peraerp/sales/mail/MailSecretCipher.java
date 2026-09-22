package com.peraerp.sales.mail;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Component
public class MailSecretCipher {
    private final byte[] key;
    public MailSecretCipher(@Value("${pera.mail.encryption-key:}") String value) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(value); } catch (IllegalArgumentException e) { decoded = new byte[0]; }
        key = decoded;
    }
    public boolean ready() { return key.length == 32; }
    public String encrypt(UUID company, String secret) {
        if (!ready()) throw new BusinessRuleException("Configura PERA_MAIL_ENCRYPTION_KEY en el servidor antes de conectar el correo.");
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, company, nonce);
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception e) { throw new BusinessRuleException("No se pudo proteger la credencial SMTP."); }
    }
    public String decrypt(UUID company, String value) {
        if (!ready()) throw new BusinessRuleException("La clave de cifrado del correo no está configurada.");
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value));
            byte[] nonce = new byte[12]; buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, company, nonce).doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new BusinessRuleException("No se pudo abrir la credencial SMTP. Revisa la clave del servidor."); }
    }
    private Cipher cipher(int mode, UUID company, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(company.toString().getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
