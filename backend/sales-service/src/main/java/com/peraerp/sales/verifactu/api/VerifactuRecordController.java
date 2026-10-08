package com.peraerp.sales.verifactu.api;

import com.peraerp.sales.verifactu.VerifactuRecordQueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** Registros de alta por estado, para la pantalla de seguimiento. */
    @GetMapping("/search")
    org.springframework.data.domain.Page<VerifactuRecordResponse> search(
            @RequestParam(required = false) List<com.peraerp.sales.verifactu.domain.VerifactuState> state,
            org.springframework.data.domain.Pageable pageable) {
        return service.search(state, pageable);
    }

    /** Situación de la remisión automática a la AEAT. */
    @GetMapping("/remission")
    VerifactuRemissionSummary remission() {
        return service.remission();
    }

    @GetMapping("/availability")
    List<UUID> findRegisteredDocuments(@RequestParam List<UUID> documentIds) {
        return service.findRegisteredDocuments(documentIds);
    }

    /**
     * XML del registro, en su propio recurso porque pesa y casi nunca se mira.
     *
     * <p>Se devuelve tal cual, sin envolver en JSON: es el documento que se remite a la AEAT y
     * tiene que poder guardarse en un fichero y validarse contra el esquema sin desescaparlo
     * antes.</p>
     */
    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8")
    String payloadXml(@PathVariable UUID id) {
        return service.payloadXml(id);
    }
}
