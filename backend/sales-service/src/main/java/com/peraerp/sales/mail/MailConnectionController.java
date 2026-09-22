package com.peraerp.sales.mail;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/connections/email")
public class MailConnectionController {
    private final MailConnectionService service;
    public MailConnectionController(MailConnectionService service) { this.service=service; }
    @GetMapping public MailConnectionService.View get() { return service.view(); }
    @PutMapping public MailConnectionService.View save(@Valid @RequestBody MailConnectionService.Request request) { return service.save(request); }
    @PostMapping("/test") public MailConnectionService.View test() { return service.test(); }
    @DeleteMapping public MailConnectionService.View disconnect() { return service.disconnect(); }
}
