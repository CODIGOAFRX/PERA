package com.peraerp.masterdata.importing;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class MasterDataImportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final MasterDataImportService service;

    public MasterDataImportController(MasterDataImportService service) {
        this.service = service;
    }

    @GetMapping("/customers/import-template") ResponseEntity<byte[]> customerTemplate() { return template(ImportKind.CUSTOMERS); }
    @GetMapping("/suppliers/import-template") ResponseEntity<byte[]> supplierTemplate() { return template(ImportKind.SUPPLIERS); }
    @GetMapping("/products/import-template") ResponseEntity<byte[]> productTemplate() { return template(ImportKind.PRODUCTS); }

    @PostMapping(value = "/customers/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportResult importCustomers(@RequestPart("file") MultipartFile file) { return service.importFile(ImportKind.CUSTOMERS, file); }

    @PostMapping(value = "/suppliers/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportResult importSuppliers(@RequestPart("file") MultipartFile file) { return service.importFile(ImportKind.SUPPLIERS, file); }

    @PostMapping(value = "/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportResult importProducts(@RequestPart("file") MultipartFile file) { return service.importFile(ImportKind.PRODUCTS, file); }

    private ResponseEntity<byte[]> template(ImportKind kind) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("plantilla-" + kind.fileStem() + ".xlsx").build().toString())
                .body(service.template(kind));
    }
}
