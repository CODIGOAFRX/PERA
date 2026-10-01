package com.peraerp.finance.receivable;

import com.peraerp.finance.cash.CashService;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.receivable.ReceiptDtos.CollectReceiptRequest;
import com.peraerp.finance.receivable.ReceiptDtos.IssueReceiptsRequest;
import com.peraerp.finance.receivable.ReceiptDtos.ReceiptOperationResponse;
import com.peraerp.finance.receivable.ReceiptDtos.ReceiptResponse;
import com.peraerp.finance.receivable.ReceiptDtos.ReopenReceiptRequest;
import com.peraerp.finance.receivable.ReceiptDtos.ReturnReceiptRequest;
import com.peraerp.finance.remittance.RemittanceRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ReceiptService {

    private final ReceiptRepository repository;
    private final DocumentDueDateRepository dueDates;
    private final RemittanceRepository remittances;
    private final CollectionNumbering numbering;
    private final InvoiceCollectionTracker tracker;
    private final CashService cashService;
    private final CurrentCompanyProvider companyProvider;

    public ReceiptService(ReceiptRepository repository, DocumentDueDateRepository dueDates,
                          RemittanceRepository remittances, CollectionNumbering numbering,
                          InvoiceCollectionTracker tracker, CashService cashService,
                          CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.dueDates = dueDates;
        this.remittances = remittances;
        this.numbering = numbering;
        this.tracker = tracker;
        this.cashService = cashService;
        this.companyProvider = companyProvider;
    }

    /** Emite un recibo por cada vencimiento pendiente de la factura que todavía no lo tenga. */
    @Transactional
    public List<ReceiptResponse> issue(IssueReceiptsRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        List<DocumentDueDate> schedule = dueDates.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumber(
                companyId, request.documentId());
        if (schedule.isEmpty()) {
            throw new BusinessRuleException("Genera primero los vencimientos de la factura.");
        }
        int year = LocalDate.now().getYear();
        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        List<Receipt> issued = new ArrayList<>();
        for (DocumentDueDate dueDate : schedule) {
            if (dueDate.getStatus() != DueDateStatus.PENDING
                    || repository.existsByCompanyIdAndDueDateIdAndStatusNot(companyId, dueDate.getId(),
                    ReceiptStatus.CANCELLED)) {
                continue;
            }
            issued.add(repository.save(new Receipt(companyId, numbering.nextReceiptNumber(companyId, year), dueDate,
                    request.customerId(), normalize(request.customerCode()), request.customerName().trim(),
                    request.documentNumber().trim(), currency)));
        }
        if (issued.isEmpty()) {
            throw new BusinessRuleException("Todos los vencimientos de la factura ya tienen recibo.");
        }
        return issued.stream().map(ReceiptResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<ReceiptResponse> search(ReceiptStatus status, UUID customerId, LocalDate dueFrom, LocalDate dueTo,
                                        boolean available, String query, Pageable pageable) {
        if (dueFrom != null && dueTo != null && dueTo.isBefore(dueFrom)) {
            throw new BusinessRuleException("El final del intervalo de búsqueda no puede ser anterior al inicio.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalize(query);
        return repository.search(companyId, status != null, status, customerId != null, customerId,
                        dueFrom != null, dueFrom, dueTo != null, dueTo, available, normalizedQuery != null,
                        normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(ReceiptResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> findByDocument(UUID documentId) {
        return repository.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumberAsc(
                companyProvider.requireCompanyId(), documentId).stream().map(ReceiptResponse::from).toList();
    }

    @Transactional
    public ReceiptOperationResponse collect(UUID id, CollectReceiptRequest request) {
        if (request.collectionDate().isAfter(LocalDate.now().plusDays(1))) {
            throw new BusinessRuleException("La fecha de cobro no puede ser futura.");
        }
        if (request.cashSessionId() != null && request.method() != CollectionMethod.CASH) {
            throw new BusinessRuleException("Solo los cobros en efectivo se anotan en una caja.");
        }
        return change(id, receipt -> {
            receipt.collect(request.collectionDate(), request.method(), normalize(request.notes()));
            if (request.cashSessionId() != null) {
                cashService.recordReceiptCollection(receipt.getCompanyId(), request.cashSessionId(), receipt);
            }
        });
    }

    /** Devolución de un recibo cobrado o presentado al banco. La factura vuelve a estar pendiente. */
    @Transactional
    public ReceiptOperationResponse returnReceipt(UUID id, ReturnReceiptRequest request) {
        return change(id, receipt -> {
            receipt.markReturned(request.returnDate(), request.reason().trim());
            if (receipt.getRemittanceId() != null) {
                remittances.findByIdAndCompanyId(receipt.getRemittanceId(), receipt.getCompanyId())
                        .ifPresent(remittance -> remittance.registerReturn());
            }
        });
    }

    @Transactional
    public ReceiptOperationResponse reopen(UUID id, ReopenReceiptRequest request) {
        return change(id, receipt -> receipt.reopen(request == null ? null : request.newDueDate()));
    }

    @Transactional
    public ReceiptOperationResponse cancel(UUID id) {
        return change(id, Receipt::cancel);
    }

    private ReceiptOperationResponse change(UUID id, Consumer<Receipt> operation) {
        UUID companyId = companyProvider.requireCompanyId();
        Receipt receipt = repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo", id));
        try {
            operation.accept(receipt);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        tracker.refreshDueDate(receipt);
        boolean invoiceUpdated = tracker.refreshInvoices(companyId, List.of(receipt));
        return new ReceiptOperationResponse(List.of(ReceiptResponse.from(receipt)), invoiceUpdated);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
