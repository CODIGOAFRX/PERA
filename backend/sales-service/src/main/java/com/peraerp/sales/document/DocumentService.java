package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.outbox.DomainEventRecorder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {
    private final CommercialDocumentRepository repository;
    private final DocumentNumberGenerator numberGenerator;
    private final DocumentAmountsCalculator calculator;
    private final CurrentCompanyProvider companyProvider;
    private final DomainEventRecorder events;

    public DocumentService(CommercialDocumentRepository repository, DocumentNumberGenerator numberGenerator,
                           DocumentAmountsCalculator calculator, CurrentCompanyProvider companyProvider,
                           DomainEventRecorder events) {
        this.repository=repository; this.numberGenerator=numberGenerator; this.calculator=calculator;
        this.companyProvider=companyProvider; this.events=events;
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String number = numberGenerator.next(companyId, request.type(), request.issueDate().getYear());
        CommercialDocument document = new CommercialDocument(companyId, number, request.type(), request.customerId(),
                request.customerCode(), request.customerName(), request.issueDate(), request.dueDate(), request.currency(),
                null, request.paymentMethodId(), request.notes());
        for (DocumentLineRequest line : request.lines()) {
            document.addLine(toLine(line));
        }
        document.recalculate(calculator);
        if (request.confirm()) document.confirm();
        document = repository.save(document);
        events.record("CommercialDocument", document.getId(), "DocumentCreated",
                Map.of("documentId", document.getId(), "number", document.getDocumentNumber(), "type", document.getType(),
                        "total", document.getTotalAmount(), "companyId", companyId));
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(UUID id) { return DocumentResponse.from(requireDocument(id)); }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(DocumentType type, DocumentStatus status, UUID customerId,
                                         LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        return repository.search(companyProvider.requireCompanyId(), type, status, customerId, fromDate, toDate, pageable)
                .map(DocumentResponse::from);
    }

    @Transactional
    public DocumentResponse convert(UUID sourceId) {
        UUID companyId = companyProvider.requireCompanyId();
        CommercialDocument source = requireDocument(sourceId);
        if (source.getStatus() != DocumentStatus.CONFIRMED) {
            throw new BusinessRuleException("Solo se pueden convertir documentos confirmados.");
        }
        DocumentType targetType = switch (source.getType()) {
            case QUOTE -> DocumentType.DELIVERY_NOTE;
            case DELIVERY_NOTE -> DocumentType.INVOICE;
            default -> throw new BusinessRuleException("El tipo de documento no admite conversión.");
        };
        LocalDate issueDate = LocalDate.now();
        CommercialDocument target = new CommercialDocument(companyId,
                numberGenerator.next(companyId, targetType, issueDate.getYear()), targetType, source.getCustomerId(),
                source.getCustomerCodeSnapshot(), source.getCustomerNameSnapshot(), issueDate, source.getDueDate(),
                source.getCurrency(), source.getId(), source.getPaymentMethodId(), source.getNotes());
        for (DocumentLine line : source.getLines()) {
            target.addLine(new DocumentLine(line.getProductId(), line.getProductCodeSnapshot(),
                    line.getDescription(), line.getQuantity(), line.getUnitPrice(),
                    line.getDiscountPercentage(), line.getTaxPercentage()));
        }
        target.recalculate(calculator);
        target.confirm();
        source.markConverted();
        target = repository.save(target);
        events.record("CommercialDocument", target.getId(), "DocumentConverted",
                Map.of("sourceDocumentId", sourceId, "targetDocumentId", target.getId(), "targetType", targetType,
                        "companyId", companyId, "total", target.getTotalAmount()));
        return DocumentResponse.from(target);
    }

    @Transactional
    public DocumentResponse updatePaymentStatus(UUID id, PaymentStatus status) {
        CommercialDocument document = requireDocument(id);
        if (document.getType() != DocumentType.INVOICE) {
            throw new BusinessRuleException("El estado de cobro solo se aplica a facturas.");
        }
        document.updatePaymentStatus(status);
        events.record("CommercialDocument", document.getId(), "InvoicePaymentStatusChanged",
                Map.of("documentId", document.getId(), "status", status, "companyId", document.getCompanyId()));
        return DocumentResponse.from(document);
    }

    private CommercialDocument requireDocument(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Documento", id));
    }
    private DocumentLine toLine(DocumentLineRequest line) {
        return new DocumentLine(line.productId(), line.productCode(), line.description(), line.quantity(),
                line.unitPrice(), line.discountPercentage(), line.taxPercentage());
    }
}
