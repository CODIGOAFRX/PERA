package com.peraerp.sales.document;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Returns the figures the UI needs to explain the risk and, when allowed, ask for confirmation. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class CreditRiskExceptionHandler {
    @ExceptionHandler(CreditRiskException.class)
    ProblemDetail handle(CreditRiskException exception) {
        CreditRiskService.Assessment risk = exception.assessment();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Riesgo de crédito del cliente");
        problem.setType(URI.create("https://pera-erp.local/problems/credit-risk"));
        problem.setProperty("policy", risk.policy().name());
        problem.setProperty("creditLimit", risk.creditLimit());
        problem.setProperty("outstanding", risk.outstanding());
        problem.setProperty("documentAmount", risk.documentAmount());
        problem.setProperty("projected", risk.projected());
        problem.setProperty("requiresAcknowledgement", risk.requiresAcknowledgement());
        problem.setProperty("blocked", risk.blocked());
        return problem;
    }
}
