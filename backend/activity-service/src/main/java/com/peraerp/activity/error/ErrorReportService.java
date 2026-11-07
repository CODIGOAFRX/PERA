package com.peraerp.activity.error;

import com.peraerp.platform.domain.BusinessRuleException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Avisos de error del programa. Hace lo que en DimproCristalWin hacen {@code MisErrores} y el formulario
 * {@code ver_error}: guarda qué ha pasado (el {@code ERRORES.LOG}), deja al usuario escribir qué estaba haciendo
 * y se lo manda a soporte con el correo de la empresa o, si no tiene, con el de la instalación.
 *
 * <p>El aviso se manda cuando el usuario pulsa «Enviar a soporte». Si cierra la ventana sin enviarlo (o cierra el
 * navegador), sale solo pasados {@link #AUTO_SEND_AFTER_MINUTES} minutos. Cada error manda su correo, aunque se
 * repita (Raúl lo prefiere así); solo hay un tope de {@link #MAX_EMAILS_PER_HOUR} por usuario y hora para que un
 * fallo en bucle no mande miles.</p>
 */
@Service
public class ErrorReportService {
    private static final Logger log = LoggerFactory.getLogger(ErrorReportService.class);
    static final int MAX_EMAILS_PER_HOUR = 60;
    static final int AUTO_SEND_AFTER_MINUTES = 3;

    /** Lo que manda la pantalla. source: PANTALLA (fallo del navegador) o SERVIDOR (la API contestó con un error interno). */
    public record Request(@NotBlank @Pattern(regexp = "PANTALLA|SERVIDOR") String source, @Size(max = 180) String companyName,
                          @Size(max = 300) String screen, @Size(max = 30) String appVersion,
                          @Size(max = 40) String errorId, @NotBlank @Size(max = 1000) String message,
                          @Size(max = 20000) String detail, @Size(max = 400) String userAgent) {}

    public record SendRequest(@Size(max = 2000) String comment) {}

    public record Response(UUID id, boolean emailed, String message) {}

    public record Report(UUID id, UUID companyId, String companyName, String userName, String source, String screen,
                         String appVersion, String errorId, String message, String detail, String userComment,
                         int occurrences, Instant createdAt, Instant lastSeenAt, Instant emailedAt, String emailError,
                         String sentWith) {}

    /** Quién lo manda: sale del token, nunca del cuerpo de la petición. */
    public record Reporter(UUID companyId, String userId, String userName) {}

    private static final String COLUMNS = """
            id, company_id, company_name, user_name, source, screen, app_version, error_id, message, detail, user_comment,
            occurrences, created_at, last_seen_at, emailed_at, email_error, sent_with""";

    private final JdbcTemplate jdbc;
    private final SupportMailer mailer;

    public ErrorReportService(JdbcTemplate jdbc, SupportMailer mailer) {
        this.jdbc = jdbc;
        this.mailer = mailer;
    }

    /** Guarda el aviso. Se manda después, con el comentario del usuario (ver {@link #send}). */
    public Response report(Reporter reporter, Request request) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO error_reports (id, company_id, company_name, user_id, user_name, source, screen, app_version,
                    error_id, message, detail, user_agent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, reporter.companyId(), request.companyName(), reporter.userId(), reporter.userName(),
                request.source(), request.screen(), request.appVersion(), request.errorId(), request.message(),
                request.detail(), request.userAgent());
        return new Response(id, false, "Guardado. Escribe qué estabas haciendo y envíalo a soporte.");
    }

    /** «Enviar a soporte»: guarda el comentario y manda el correo ahora, diciendo si ha salido. */
    public Response send(Reporter reporter, UUID id, SendRequest request) {
        String comment = request == null || request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        // Solo quien lo ha generado lo manda, y una sola vez: si ya salió (o lo está mandando el envío automático), no se repite.
        int claimed = jdbc.update("""
                UPDATE error_reports SET user_comment = ?, send_attempted_at = now()
                WHERE id = ? AND user_id IS NOT DISTINCT FROM ? AND send_attempted_at IS NULL
                """, comment, id, reporter.userId());
        if (claimed == 0) {
            Report existing = find(id);
            if (existing == null) throw new BusinessRuleException("Aviso de error no encontrado.");
            if (existing.emailedAt() != null) return new Response(id, true, "Este aviso ya se había mandado a soporte.");
            return new Response(id, false, existing.emailError() != null
                    ? "Ya se intentó mandar y no salió: " + existing.emailError() : "Este aviso ya se está mandando.");
        }
        return deliver(id);
    }

    /** Los avisos que el usuario no llegó a enviar salen solos pasados unos minutos, sin comentario. */
    @Scheduled(fixedDelayString = "${pera.support.auto-send-poll-ms:60000}", initialDelayString = "${pera.support.auto-send-poll-ms:60000}")
    public void sendForgotten() {
        List<UUID> pending = jdbc.queryForList("""
                UPDATE error_reports SET send_attempted_at = now()
                WHERE send_attempted_at IS NULL AND created_at < now() - make_interval(mins => ?)
                RETURNING id
                """, UUID.class, AUTO_SEND_AFTER_MINUTES);
        for (UUID id : pending) {
            try {
                deliver(id);
            } catch (RuntimeException e) {
                log.warn("No se pudo mandar el aviso de error {}: {}", id, e.getMessage());
            }
        }
    }

    private Response deliver(UUID id) {
        Report report = find(id);
        if (report == null) throw new BusinessRuleException("Aviso de error no encontrado.");
        if (!mailer.hasRecipient()) {
            noteFailure(id, "La instalación no tiene correo de soporte ([SOPORTE] en config/pera.ini).");
            return new Response(id, false, "Guardado. Esta instalación no tiene correo de soporte configurado.");
        }
        Integer lastHour = jdbc.queryForObject("""
                SELECT count(*) FROM error_reports WHERE user_name IS NOT DISTINCT FROM ? AND emailed_at > now() - interval '1 hour'
                """, Integer.class, report.userName());
        if (lastHour != null && lastHour >= MAX_EMAILS_PER_HOUR) {
            noteFailure(id, "Más de " + MAX_EMAILS_PER_HOUR + " avisos en una hora.");
            return new Response(id, false, "Guardado. Se han mandado más de " + MAX_EMAILS_PER_HOUR
                    + " avisos en una hora; este no se manda por correo.");
        }
        try {
            String sender = mailer.send(report);
            jdbc.update("UPDATE error_reports SET emailed_at = now(), email_error = NULL, sent_with = ? WHERE id = ?", sender, id);
            return new Response(id, true, "Enviado a soporte desde " + sender + ".");
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("No se pudo mandar a soporte el aviso de error {}: {}", id, reason);
            noteFailure(id, reason);
            return new Response(id, false, "Guardado, pero no se ha podido mandar: " + reason);
        }
    }

    private void noteFailure(UUID id, String reason) {
        jdbc.update("UPDATE error_reports SET email_error = ? WHERE id = ?", abbreviate(reason, 500), id);
    }

    Report find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM error_reports WHERE id = ?", (rs, n) -> map(rs), id)
                .stream().findFirst().orElse(null);
    }

    /** Los últimos avisos de la empresa, para consultarlos desde PERA. */
    public List<Report> recent(UUID companyId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM error_reports WHERE company_id = ? ORDER BY last_seen_at DESC LIMIT ?",
                (rs, n) -> map(rs), companyId, Math.min(Math.max(limit, 1), 200));
    }

    private static Report map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Report(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("company_name"),
                rs.getString("user_name"), rs.getString("source"), rs.getString("screen"), rs.getString("app_version"),
                rs.getString("error_id"), rs.getString("message"), rs.getString("detail"), rs.getString("user_comment"),
                rs.getInt("occurrences"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant(),
                rs.getTimestamp("emailed_at") == null ? null : rs.getTimestamp("emailed_at").toInstant(),
                rs.getString("email_error"), rs.getString("sent_with"));
    }

    static String abbreviate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
