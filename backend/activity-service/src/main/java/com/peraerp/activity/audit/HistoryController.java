package com.peraerp.activity.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {
    private final AuditService auditService;

    public HistoryController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    Page<AuditEventResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredUntil,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return auditService.search(q, sourceService, eventType, action, resourceType, resourceId, outcome,
                occurredFrom, occurredUntil, page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredUntil) {
        byte[] csv = auditService.exportCsv(q, sourceService, eventType, action, resourceType, resourceId, outcome,
                occurredFrom, occurredUntil);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=pera-history.csv")
                .body(csv);
    }

    @GetMapping("/{id}")
    AuditEventResponse findById(@PathVariable UUID id) {
        return auditService.findById(id);
    }
}
