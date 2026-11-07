package com.peraerp.activity.error;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportMailerTest {
    private final ErrorReportService.Report report = new ErrorReportService.Report(UUID.randomUUID(), null, "Cristalería Ejemplo",
            "Ana", "SERVIDOR", "/ventas", "0.3.0", "MH7X-A1B", "No se pudo completar la operación.",
            "Petición: /api/v1/documents", "Estaba guardando una factura", 1, Instant.parse("2026-11-07T10:15:30Z"),
            Instant.parse("2026-11-07T10:15:30Z"), null, null, null);

    private static SupportMailer mailer(String recipient, String host, String user) {
        return new SupportMailer(recipient, host, 587, user, "p", "", "STARTTLS", "http://localhost:1", "key");
    }

    @Test
    void theEmailCarriesTheCompanyTheUserCommentAndTheErrorLikeDimproErrorMails() {
        // Cada aviso con su asunto (hora y número), para que el buzón no los agrupe en una conversación.
        assertThat(SupportMailer.subject(report)).isEqualTo("Error Cristalería Ejemplo · /ventas · 07/11 11:15:30 · #"
                + report.id().toString().substring(0, 8).toUpperCase() + " · PERA v0.3.0");
        String body = SupportMailer.body(report);
        assertThat(body).startsWith("Cristalería Ejemplo").contains("Comentario: Estaba guardando una factura",
                "Usuario: Ana", "Día y hora: 07/11/2026 11:15:30", "Versión: 0.3.0", "Pantalla: /ventas",
                "Código de error: MH7X-A1B", "No se pudo completar la operación.", "Petición: /api/v1/documents");
    }

    @Test
    void withoutCompanyMailNorInstallationAccountItSaysWhyAndSendsNothing() {
        SupportMailer withoutAccount = mailer("soporte@example.com", "", "");
        assertThat(withoutAccount.hasRecipient()).isTrue();
        assertThat(withoutAccount.installationAccount()).isFalse();
        assertThatThrownBy(() -> withoutAccount.send(report)).hasMessageContaining("config/pera.ini");
        assertThat(mailer("", "smtp.example.com", "pera@example.com").hasRecipient()).isFalse();
        assertThat(mailer("soporte@example.com", "smtp.example.com", "pera@example.com").installationAccount()).isTrue();
    }

    @Test
    void severalSupportAddressesSeparatedByCommasAllReceiveTheReport() {
        assertThat(mailer("raul@example.com, pedro@example.com;soporte@example.com", "", "").recipients())
                .containsExactly("raul@example.com", "pedro@example.com", "soporte@example.com");
    }
}
