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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessRuleException rule) return handleBusinessRule(rule);
        }
        return problem(HttpStatus.BAD_REQUEST, "Petición no válida",
                "La petición no tiene el formato esperado.", "malformed-request");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permiso para realizar esta operación.", "forbidden");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail handleConcurrentModification(Exception exception) {
        LOGGER.info("Conflicto de concurrencia: {}", exception.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Conflicto de edición",
                "Otro usuario ha modificado estos datos. Recarga y vuelve a intentarlo.", "concurrent-modification");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        // The SQL message may include values; never expose it to the client.
        LOGGER.warn("Violación de integridad de datos: {}", exception.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Conflicto con datos existentes",
                "La operación entra en conflicto con datos existentes (duplicado o referencia en uso).", "data-conflict");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "Archivo demasiado grande",
                "El archivo supera el tamaño máximo permitido.", "upload-too-large");
    }

    /** Framework errors (unknown route, unsupported method or media type, ResponseStatusException...). */
    private ProblemDetail frameworkError(ErrorResponse error) {
        int code = error.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null || status.is5xxServerError()) return null;
        String detail = switch (status) {
            case NOT_FOUND -> "El recurso solicitado no existe.";
            case METHOD_NOT_ALLOWED -> "Método HTTP no admitido para este recurso.";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no admitido.";
            case NOT_ACCEPTABLE -> "Formato de respuesta no disponible.";
            case UNAUTHORIZED -> "Autenticación requerida.";
            case FORBIDDEN -> "No tienes permiso para realizar esta operación.";
            case BAD_REQUEST -> "La petición no tiene el formato esperado.";
            case TOO_MANY_REQUESTS -> "Demasiados intentos. Espera unos minutos y vuelve a intentarlo.";
            default -> status.getReasonPhrase();
        };
        return problem(status, status == HttpStatus.NOT_FOUND ? "Recurso no encontrado" : "Petición no válida",
                detail, "http-" + code);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse error) {
            ProblemDetail detail = frameworkError(error);
            if (detail != null) return detail;
        }
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
