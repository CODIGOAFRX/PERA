package com.peraerp.activity.audit;

import com.peraerp.platform.domain.AuthenticationFailedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/audit-events")
public class AuditInternalController {
    private final AuditService auditService;
    private final InternalServiceKeyValidator keyValidator;

    public AuditInternalController(AuditService auditService, InternalServiceKeyValidator keyValidator) {
        this.auditService = auditService;
        this.keyValidator = keyValidator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AuditEventResponse ingest(@RequestHeader(value = "X-PERA-SERVICE-KEY", required = false) String serviceKey,
                              @Valid @RequestBody AuditEventRequest request) {
        if (!keyValidator.isValid(serviceKey)) {
            throw new AuthenticationFailedException("Credencial interna no válida.");
        }
        return auditService.ingest(request);
    }
}
