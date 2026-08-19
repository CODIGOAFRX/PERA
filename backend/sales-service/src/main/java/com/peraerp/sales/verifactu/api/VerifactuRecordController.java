package com.peraerp.sales.verifactu.api;

import com.peraerp.sales.verifactu.VerifactuRecordQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verifactu-records")
public class VerifactuRecordController {

    private final VerifactuRecordQueryService service;

    public VerifactuRecordController(VerifactuRecordQueryService service) {
        this.service = service;
    }

    @GetMapping
    List<VerifactuRecordResponse> findByDocument(@RequestParam UUID documentId) {
        return service.findByDocument(documentId);
    }
}
