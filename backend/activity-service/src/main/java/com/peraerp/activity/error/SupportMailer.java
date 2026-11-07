package com.peraerp.activity.error;

import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Manda a soporte el correo de un aviso de error, como {@code ver_error.envio_email_chilkat} de DimproCristalWin:
 *
 * <ol>
 *   <li>Con el <strong>correo de la empresa</strong> (Conexiones › Correo, el mismo con el que manda las facturas),
 *       que guarda Ventas. Así el remitente es el cliente, igual que en Dimpro.</li>
 *   <li>Si la empresa no tiene correo activo, con la <strong>cuenta de la instalación</strong> ({@code [SMTP]} de
 *       {@code config/pera.ini}), que hace el papel de la cuenta de Dimpro para estos avisos.</li>
 * </ol>
 *
 * <p>El destinatario es siempre el de {@code [SOPORTE]}. El cuerpo lleva lo mismo que los correos de error de Dimpro:
 * empresa, comentario del usuario, quién, cuándo, versión, pantalla y el error.</p>
 */
@Component
public class SupportMailer {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.of("es", "ES"))
            .withZone(ZoneId.of("Europe/Madrid"));

    private static final String LOCAL_HOST = localHost();

    private final String recipient;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final String security;
    private final RestClient sales;
    private final String serviceKey;

    public SupportMailer(@Value("${pera.support.email:}") String recipient,
                         @Value("${pera.support.smtp.host:}") String host,
                         @Value("${pera.support.smtp.port:587}") int port,
                         @Value("${pera.support.smtp.user:}") String username,
                         @Value("${pera.support.smtp.password:}") String password,
                         @Value("${pera.support.smtp.from:}") String from,
                         @Value("${pera.support.smtp.security:STARTTLS}") String security,
                         @Value("${pera.services.sales-url:http://localhost:8083}") String salesUrl,
                         @Value("${pera.internal.service-key}") String serviceKey) {
        this.recipient = recipient.trim();
        this.host = host.trim();
        this.port = port;
        this.username = username.trim();
        this.password = password;
        this.from = from.isBlank() ? username.trim() : from.trim();
        this.security = security.trim().toUpperCase(Locale.ROOT);
        // Actividad no tiene el constructor de clientes HTTP de Spring: se crea aquí, solo para llamar a Ventas.
        this.sales = RestClient.builder().baseUrl(salesUrl).build();
        this.serviceKey = serviceKey;
    }

    /** Sin destinatario no se manda nada: los avisos solo se guardan. */
    public boolean hasRecipient() {
        return !recipient.isEmpty();
    }

    /** La cuenta de la instalación, la de reserva cuando la empresa no tiene correo. */
    boolean installationAccount() {
        return !host.isEmpty() && !from.isEmpty();
    }

    /** Manda el aviso y devuelve desde qué dirección ha salido. */
    String send(ErrorReportService.Report report) throws Exception {
        String companyProblem = null;
        if (report.companyId() != null) {
            try {
                String sender = sales.post().uri("/internal/v1/support-mail")
                        .header("X-PERA-SERVICE-KEY", serviceKey)
                        .body(Map.of("companyId", report.companyId(), "to", recipient, "subject", subject(report), "body", body(report)))
                        .retrieve().body(String.class);
                return sender == null || sender.isBlank() ? "el correo de la empresa" : sender;
            } catch (HttpStatusCodeException e) {
                HttpStatusCode status = e.getStatusCode();
                // 409: la empresa no tiene correo activo. Cualquier otro fallo también deja probar la cuenta de la instalación.
                companyProblem = status.value() == 409 ? "La empresa no tiene el correo activo en Conexiones."
                        : e.getResponseBodyAsString();
            } catch (RestClientException e) {
                companyProblem = "Ventas no responde para mandarlo con el correo de la empresa.";
            }
        }
        if (!installationAccount()) {
            throw new IllegalStateException((companyProblem == null ? "" : companyProblem + " ")
                    + "Tampoco hay cuenta de envío de la instalación ([SMTP] en config/pera.ini).");
        }
        sendWithInstallationAccount(report);
        return from;
    }

    private void sendWithInstallationAccount(ErrorReportService.Report report) throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!username.isEmpty()) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", String.valueOf(!username.isEmpty()));
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "15000");
        properties.setProperty("mail.smtp.writetimeout", "15000");
        // Como en Ventas: sin el nombre, JavaMail lo pregunta al DNS para el saludo (EHLO) y puede tardar segundos.
        properties.setProperty("mail.smtp.localhost", LOCAL_HOST);
        properties.setProperty("mail.from", from);
        properties.setProperty("mail.smtp.starttls.enable", String.valueOf(security.equals("STARTTLS")));
        properties.setProperty("mail.smtp.starttls.required", String.valueOf(security.equals("STARTTLS")));
        properties.setProperty("mail.smtp.ssl.enable", String.valueOf(security.equals("TLS")));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");

        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(new InternetAddress(from, report.companyName() == null ? "PERA ERP" : report.companyName(), "UTF-8"));
        helper.setTo(recipient);
        helper.setSubject(subject(report));
        helper.setText(body(report), false);
        sender.send(message);
    }

    private static final DateTimeFormatter SUBJECT_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss", Locale.of("es", "ES"))
            .withZone(ZoneId.of("Europe/Madrid"));

    /**
     * Como en Dimpro: «Error» y la empresa. Lleva además la hora y el número corto del aviso para que cada
     * aviso sea un asunto distinto: Proton y otros buzones agrupan en una conversación los de asunto igual.
     */
    static String subject(ErrorReportService.Report report) {
        return "Error " + (report.companyName() == null ? "en PERA" : report.companyName())
                + (report.screen() == null ? "" : " · " + report.screen())
                + " · " + SUBJECT_TIME.format(report.createdAt())
                + " · #" + shortId(report)
                + (report.appVersion() == null ? "" : " · PERA v" + report.appVersion());
    }

    /** Los primeros caracteres del aviso: lo bastante para distinguirlo y buscarlo en PERA. */
    static String shortId(ErrorReportService.Report report) {
        return report.id().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    static String body(ErrorReportService.Report report) {
        String line = "*".repeat(80);
        return nullToDash(report.companyName()) + "\n\n"
                + "Comentario: " + nullToDash(report.userComment()) + "\n\n"
                + "Usuario: " + nullToDash(report.userName()) + "\n"
                + "Día y hora: " + WHEN.format(report.createdAt()) + "\n"
                + "Versión: " + nullToDash(report.appVersion()) + "\n"
                + "Pantalla: " + nullToDash(report.screen()) + "\n"
                + "Origen: " + ("SERVIDOR".equals(report.source()) ? "el servidor contestó con un error interno"
                : "fallo en la pantalla del navegador") + "\n"
                + (report.errorId() == null ? "" : "Código de error: " + report.errorId()
                + " (búscalo en los registros de los servicios)\n")
                + "\nError:\n" + report.message() + "\n"
                + (report.detail() == null || report.detail().isBlank() ? "" : "\nDetalle:\n" + report.detail() + "\n")
                + "\n" + line + "\nAviso #" + shortId(report) + " (" + report.id() + ")\n";
    }

    private static String localHost() {
        try {
            String name = java.net.InetAddress.getLocalHost().getHostName();
            return name == null || name.isBlank() ? "localhost" : name;
        } catch (Exception e) {
            return "localhost";
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
