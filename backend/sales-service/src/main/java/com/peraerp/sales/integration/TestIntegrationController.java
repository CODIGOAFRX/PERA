package com.peraerp.sales.integration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class TestIntegrationController {
    private final TestIntegrationService service;
    public TestIntegrationController(TestIntegrationService service) { this.service=service; }
    @GetMapping("/api/v1/connections/fiscal/{provider}") public TestIntegrationService.View view(@PathVariable String provider) { return service.view(provider); }
    @PutMapping("/api/v1/connections/fiscal") public TestIntegrationService.View save(@RequestBody @Valid TestIntegrationService.Config request) { return service.save(request); }
    @PostMapping("/api/v1/connections/fiscal/{provider}/test") public Map<String,String> test(@PathVariable String provider) { return service.test(provider); }
    @GetMapping("/api/v1/verifactu-records/{id}/delivery") public TestIntegrationService.Delivery aeatStatus(@PathVariable UUID id) { return service.status("AEAT",id); }
    @PostMapping("/api/v1/verifactu-records/{id}/delivery") public TestIntegrationService.Delivery aeatSend(@PathVariable UUID id) { return service.sendAeat(id); }
    public record B2bRequest(@Min(1) long contactId) {}
    @GetMapping("/api/v1/documents/{id}/b2b") public TestIntegrationService.Delivery b2bStatus(@PathVariable UUID id) { return service.status("B2B",id); }
    @PostMapping("/api/v1/documents/{id}/b2b") public TestIntegrationService.Delivery b2bSend(@PathVariable UUID id,@RequestBody @Valid B2bRequest request) { return service.sendB2b(id,request.contactId()); }
    @PostMapping("/api/v1/documents/{id}/b2b/refresh") public TestIntegrationService.Delivery b2bRefresh(@PathVariable UUID id) { return service.refreshB2b(id); }
}
