package com.peraerp.platform.web;

import com.peraerp.platform.domain.AuthenticationFailedException;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationFailedException.class)
    ProblemDetail handleAuthentication(AuthenticationFailedException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Autenticación fallida", exception.getMessage(), "authentication-failed");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage(), "resource-not-found");
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio incumplida", exception.getMessage(), "business-rule");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Petición no válida",
                "Uno o más campos no cumplen las reglas de validación.", "validation");
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            violations.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        detail.setProperty("violations", violations);
        return detail;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, ConstraintViolationException.class})
    ProblemDetail handleMalformedRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Petición no válida",
                "La petición no tiene el formato esperado.", "malformed-request");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOGGER.error("Error inesperado procesando la petición", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "No se pudo completar la operación. Inténtalo de nuevo.", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://pera-erp.local/problems/" + type));
        return problem;
    }
}
