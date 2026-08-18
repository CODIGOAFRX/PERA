package com.peraerp.identity.company;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.net.URI;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CompanyLogoUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleOversizedUpload() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "El logo no puede superar 2 MiB.");
        detail.setTitle("Archivo demasiado grande");
        detail.setType(URI.create("https://pera-erp.local/problems/logo-too-large"));
        return detail;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail handleMissingFile() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La parte multipart 'file' es obligatoria.");
        detail.setTitle("Petición no válida");
        detail.setType(URI.create("https://pera-erp.local/problems/missing-logo-file"));
        return detail;
    }
}
