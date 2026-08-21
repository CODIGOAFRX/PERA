package com.peraerp.sales.print;

import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class InvoicePdfController {

    private final InvoicePdfService service;

    public InvoicePdfController(InvoicePdfService service) {
        this.service = service;
    }

    /**
     * Devuelve la factura en PDF.
     *
     * <p>Va como adjunto y no en línea porque es un entregable: lo normal es guardarlo o enviarlo,
     * no mirarlo en una pestaña. El nombre se manda en la forma simple de {@code Content-Disposition}
     * y no en la codificada: el servicio ya lo deja en ASCII, y la forma codificada obligaría al
     * navegador a interpretarla para algo que nunca la necesita.</p>
     */
    @GetMapping("/{id}/invoice.pdf")
    ResponseEntity<byte[]> invoicePdf(@PathVariable UUID id) {
        InvoicePdfService.InvoicePdf pdf = service.render(id);
        ContentDisposition disposition = ContentDisposition.attachment().filename(pdf.fileName()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", disposition.toString())
                .body(pdf.bytes());
    }
}
