package com.peraerp.activity.error;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Avisos de error: cualquier usuario con sesión los manda; los consulta quien puede ver el historial. */
@RestController
@RequestMapping("/api/v1/error-reports")
public class ErrorReportController {
    private final ErrorReportService service;

    public ErrorReportController(ErrorReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ErrorReportService.Response report(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ErrorReportService.Request request) {
        return service.report(reporter(jwt), request);
    }

    /** «Enviar a soporte» desde la ventana de error, con el comentario de qué estaba haciendo el usuario. */
    @PostMapping("/{id}/send")
    ErrorReportService.Response send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody(required = false) ErrorReportService.SendRequest request) {
        return service.send(reporter(jwt), id, request);
    }

    @GetMapping
    List<ErrorReportService.Report> recent(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "50") int limit) {
        UUID company = reporter(jwt).companyId();
        return company == null ? List.of() : service.recent(company, limit);
    }

    private static ErrorReportService.Reporter reporter(Jwt jwt) {
        String company = jwt.getClaimAsString("company_id");
        String name = null;
        for (String claim : new String[]{"display_name", "username", "name", "preferred_username"}) {
            name = jwt.getClaimAsString(claim);
            if (name != null && !name.isBlank()) break;
        }
        UUID companyId = null;
        try {
            companyId = company == null ? null : UUID.fromString(company);
        } catch (IllegalArgumentException ignored) {
            // Un token sin empresa válida manda el aviso igual, sin empresa.
        }
        return new ErrorReportService.Reporter(companyId, jwt.getSubject(), name);
    }
}
