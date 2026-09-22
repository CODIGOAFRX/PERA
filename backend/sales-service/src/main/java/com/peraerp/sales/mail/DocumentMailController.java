package com.peraerp.sales.mail;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
public class DocumentMailController {
    private final DocumentMailService service;
    public DocumentMailController(DocumentMailService service) {this.service=service;}
    public record RetryRequest(boolean confirmedNotDelivered) {}
    @PostMapping("/api/v1/documents/{id}/email/retry") public DocumentMailService.Status retryInvoice(@PathVariable UUID id, @RequestBody RetryRequest request) {return service.retry(id,false,request.confirmedNotDelivered());}
    @PostMapping("/api/v1/quotes/{id}/email/retry") public DocumentMailService.Status retryQuote(@PathVariable UUID id, @RequestBody RetryRequest request) {return service.retry(id,true,request.confirmedNotDelivered());}
    @GetMapping("/api/v1/documents/{id}/email") public DocumentMailService.Status invoice(@PathVariable UUID id) {return service.status(id,false);}
    @PostMapping("/api/v1/documents/{id}/email") public DocumentMailService.Status sendInvoice(@PathVariable UUID id) {return service.queue(id,false);}
    @GetMapping("/api/v1/quotes/{id}/email") public DocumentMailService.Status quote(@PathVariable UUID id) {return service.status(id,true);}
    @PostMapping("/api/v1/quotes/{id}/email") public DocumentMailService.Status sendQuote(@PathVariable UUID id) {return service.queue(id,true);}
}
