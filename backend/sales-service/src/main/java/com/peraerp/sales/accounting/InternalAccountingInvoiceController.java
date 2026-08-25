package com.peraerp.sales.accounting;

import com.peraerp.sales.document.CommercialDocumentRepository;
import com.peraerp.sales.document.DocumentStatus;
import com.peraerp.sales.document.DocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/accounting/invoices")
public class InternalAccountingInvoiceController {
    private static final List<DocumentType> INVOICE_TYPES = List.of(DocumentType.INVOICE, DocumentType.RECTIFYING_INVOICE);
    private static final List<DocumentStatus> VISIBLE_STATUSES = List.of(DocumentStatus.DRAFT, DocumentStatus.CONFIRMED,
            DocumentStatus.CONVERTED, DocumentStatus.CANCELLED);
    private final CommercialDocumentRepository documents;
    private final byte[] expectedKey;

    public InternalAccountingInvoiceController(CommercialDocumentRepository documents,
                                               @Value("${pera.internal.service-key}") String serviceKey) {
        this.documents = documents;
        this.expectedKey = serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AccountingInvoiceSnapshot> findAll(@RequestHeader(value = "X-PERA-SERVICE-KEY", required = false) String key,
                                                    @RequestParam UUID companyId) {
        byte[] supplied = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave interna no válida.");
        }
        return documents.findAllByCompanyIdAndTypeInAndStatusInOrderByIssueDateDesc(companyId, INVOICE_TYPES,
                VISIBLE_STATUSES).stream().map(AccountingInvoiceSnapshot::from).toList();
    }
}
