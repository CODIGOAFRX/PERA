package com.peraerp.sales.mail;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Manda un aviso de error a soporte con el correo de la empresa (Conexiones › Correo), como hace
 * DimproCristalWin en {@code ver_error}: el remitente es la cuenta de correo del cliente. Lo llama el servicio
 * de Actividad, que guarda los avisos; si la empresa no tiene correo activo contesta 409 y Actividad usa la
 * cuenta de la instalación.
 */
@RestController
@RequestMapping("/internal/v1/support-mail")
public class InternalSupportMailController {
    public record Request(@NotNull UUID companyId, @NotBlank @Size(max = 1000) String to,
                          @NotBlank @Size(max = 300) String subject, @NotBlank @Size(max = 60000) String body) {}

    private final MailConnectionService connections;
    private final SmtpTransport transport;
    private final byte[] expectedKey;

    public InternalSupportMailController(MailConnectionService connections, SmtpTransport transport,
                                         @Value("${pera.internal.service-key}") String serviceKey) {
        this.connections = connections;
        this.transport = transport;
        this.expectedKey = serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    public ResponseEntity<String> send(@RequestHeader(value = "X-PERA-SERVICE-KEY", required = false) String key,
                                       @Valid @RequestBody Request request) {
        byte[] supplied = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave interna no válida.");
        }
        MailConnectionService.Connection connection = connections.find(request.companyId());
        if (connection == null || !connection.enabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("La empresa no tiene el correo activo en Conexiones.");
        }
        // Uno o varios destinatarios separados por comas: todo el equipo de soporte recibe el mismo aviso.
        String[] to = java.util.Arrays.stream(request.to().split("[,;]")).map(String::trim)
                .filter(value -> !value.isEmpty()).toArray(String[]::new);
        try {
            for (String address : to) new jakarta.mail.internet.InternetAddress(address, true).validate();
        } catch (jakarta.mail.internet.AddressException e) {
            return ResponseEntity.badRequest().body("Correo de soporte no válido: " + e.getRef());
        }
        try {
            transport.sendText(connection, to, request.subject(), request.body());
            return ResponseEntity.ok(connection.senderEmail());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("El correo de la empresa no ha podido enviarlo: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
