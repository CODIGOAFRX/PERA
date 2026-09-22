package com.peraerp.sales.mail;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class MailConnectionService {
    public record Request(@NotBlank @Size(max=253) @Pattern(regexp="[A-Za-z0-9.-]+") String host,
                          @Min(1) @Max(65535) int port, @NotBlank @Size(max=254) String username,
                          @Size(max=1000) String password, @NotBlank @Email @Size(max=254) String senderEmail,
                          @NotBlank @Size(max=180) String senderName,
                          @NotBlank @Pattern(regexp="STARTTLS|TLS") String security, boolean enabled, boolean autoInvoices) {}
    public record View(boolean configured, boolean encryptionReady, String host, int port, String username,
                       String senderEmail, String senderName, String security, boolean enabled,
                       boolean autoInvoices, Instant verifiedAt, boolean passwordStored) {}
    public record Connection(UUID companyId, String host, int port, String username, String passwordCipher,
                             String senderEmail, String senderName, String security, boolean enabled,
                             boolean autoInvoices, Instant verifiedAt) {}
    private final JdbcTemplate jdbc;
    private final CurrentCompanyProvider company;
    private final MailSecretCipher cipher;
    private final SmtpTransport transport;
    public MailConnectionService(JdbcTemplate jdbc, CurrentCompanyProvider company, MailSecretCipher cipher, SmtpTransport transport) {
        this.jdbc=jdbc; this.company=company; this.cipher=cipher; this.transport=transport;
    }
    Connection find(UUID companyId) {
        return jdbc.query("SELECT * FROM mail_connections WHERE company_id=?", (rs,n) -> new Connection(companyId,
                rs.getString("host"), rs.getInt("port"), rs.getString("username"), rs.getString("password_cipher"),
                rs.getString("sender_email"), rs.getString("sender_name"), rs.getString("security"),
                rs.getBoolean("enabled"), rs.getBoolean("auto_invoices"),
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant()), companyId)
                .stream().findFirst().orElse(null);
    }
    public View view() {
        Connection c = find(company.requireCompanyId());
        return c == null ? new View(false,cipher.ready(),"",587,"","","","STARTTLS",false,false,null,false)
                : new View(true,cipher.ready(),c.host(),c.port(),c.username(),c.senderEmail(),c.senderName(),c.security(),
                c.enabled(),c.autoInvoices(),c.verifiedAt(),true);
    }
    @Transactional
    public View save(Request r) {
        UUID id = company.requireCompanyId();
        Connection old = find(id);
        rejectNewlines(r.senderName()); rejectNewlines(r.username()); rejectNewlines(r.senderEmail());
        String password = r.password() == null || r.password().isEmpty()
                ? old == null ? null : old.passwordCipher() : cipher.encrypt(id, r.password());
        if (password == null) throw new BusinessRuleException("Introduce la contraseña SMTP.");
        boolean changed = old == null || !old.host().equals(r.host()) || old.port()!=r.port()
                || !old.username().equals(r.username()) || !old.security().equals(r.security())
                || !old.senderEmail().equals(r.senderEmail()) || !old.passwordCipher().equals(password);
        Instant verified = changed ? null : old.verifiedAt();
        if (r.enabled() && verified == null) throw new BusinessRuleException("Guarda y comprueba la conexión antes de activar los envíos.");
        jdbc.update("""
                INSERT INTO mail_connections(company_id,host,port,username,password_cipher,sender_email,sender_name,security,enabled,auto_invoices,verified_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(company_id) DO UPDATE SET host=excluded.host,port=excluded.port,
                username=excluded.username,password_cipher=excluded.password_cipher,sender_email=excluded.sender_email,
                sender_name=excluded.sender_name,security=excluded.security,enabled=excluded.enabled,
                auto_invoices=excluded.auto_invoices,verified_at=excluded.verified_at,updated_at=now()
                """, id,r.host(),r.port(),r.username(),password,r.senderEmail(),r.senderName(),r.security(),r.enabled(),r.autoInvoices(),
                verified == null ? null : java.sql.Timestamp.from(verified));
        return view();
    }
    @Transactional
    public View test() {
        UUID id=company.requireCompanyId();
        Connection c=find(id);
        if (c==null) throw new BusinessRuleException("Guarda primero la conexión SMTP.");
        try { transport.test(c); }
        catch (Exception e) { throw new BusinessRuleException("No se pudo conectar o autenticar con SMTP. Revisa servidor, puerto, TLS y credenciales."); }
        jdbc.update("UPDATE mail_connections SET verified_at=now(),updated_at=now() WHERE company_id=?",id);
        return view();
    }
    public View disconnect() {
        jdbc.update("DELETE FROM mail_connections WHERE company_id=?",company.requireCompanyId());
        return view();
    }
    static void rejectNewlines(String value) {
        if (value != null && (value.contains("\r") || value.contains("\n"))) throw new BusinessRuleException("El campo no admite saltos de línea.");
    }
}
