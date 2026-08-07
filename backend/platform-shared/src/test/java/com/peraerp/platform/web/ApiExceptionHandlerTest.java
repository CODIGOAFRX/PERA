package com.peraerp.platform.web;

import com.peraerp.platform.domain.AuthenticationFailedException;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsAuthenticationErrorsToUnauthorizedProblemDetails() {
        ProblemDetail detail = handler.handleAuthentication(new AuthenticationFailedException("Credenciales incorrectas"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(detail.getTitle()).isEqualTo("Autenticación fallida");
        assertThat(detail.getDetail()).isEqualTo("Credenciales incorrectas");
        assertThat(detail.getType().toString()).endsWith("/authentication-failed");
    }

    @Test
    void mapsMissingResourcesToNotFound() {
        UUID id = UUID.randomUUID();
        ProblemDetail detail = handler.handleNotFound(new ResourceNotFoundException("Cliente", id));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(detail.getDetail()).contains("Cliente", id.toString());
    }

    @Test
    void mapsBusinessRulesToUnprocessableEntity() {
        ProblemDetail detail = handler.handleBusinessRule(new BusinessRuleException("Código duplicado"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(detail.getTitle()).isEqualTo("Regla de negocio incumplida");
    }

    @Test
    void hidesImplementationDetailsForUnexpectedErrors() {
        ProblemDetail detail = handler.handleUnexpected(new IllegalStateException("internal diagnostic"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(detail.getTitle()).isEqualTo("Error interno");
        assertThat(detail.getDetail()).doesNotContain("password", "database");
        assertThat(detail.getType().toString()).endsWith("/internal-error");
    }

    @Test
    void mapsMalformedRequestsToBadRequest() {
        ProblemDetail detail = handler.handleMalformedRequest(new IllegalArgumentException("parser detail"));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(detail.getDetail()).doesNotContain("parser detail");
        assertThat(detail.getType().toString()).endsWith("/malformed-request");
    }
}
