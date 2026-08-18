package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.currency.DocumentCurrencyService;
import com.peraerp.sales.currency.DocumentCurrencySnapshot;
import com.peraerp.sales.outbox.DomainEventRecorder;
import com.peraerp.sales.masterdata.CustomerSnapshot;
import com.peraerp.sales.masterdata.ResolvedDocumentLine;
import com.peraerp.sales.masterdata.SalesMasterDataService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentService {
    private final CommercialDocumentRepository repository;
    private final DocumentNumberGenerator numberGenerator;
    private final DocumentAmountsCalculator calculator;
    private final CurrentCompanyProvider companyProvider;
    private final DomainEventRecorder events;
    private final DocumentCurrencyService currencyService;
    private final SalesMasterDataService masterDataService;

    public DocumentService(CommercialDocumentRepository repository, DocumentNumberGenerator numberGenerator,
                           DocumentAmountsCalculator calculator, CurrentCompanyProvider companyProvider,
                           DomainEventRecorder events, DocumentCurrencyService currencyService,
                           SalesMasterDataService masterDataService) {
        this.repository=repository; this.numberGenerator=numberGenerator; this.calculator=calculator;
        this.companyProvider=companyProvider; this.events=events; this.currencyService=currencyService;
        this.masterDataService=masterDataService;
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        CustomerSnapshot customer = masterDataService.requireActiveCustomer(request.customerId());
        String currency = request.currency() == null ? "EUR" : request.currency().trim().toUpperCase(Locale.ROOT);
        List<ResolvedDocumentLine> resolvedLines = request.lines().stream()
                .map(line -> masterDataService.resolveLine(customer.id(), line, request.issueDate(), currency))
                .toList();
        String number = numberGenerator.next(companyId, request.type(), request.issueDate(), request.numberingSchemeId());
        CommercialDocument document = new CommercialDocument(companyId, number, request.type(), customer.id(),
                customer.code(), customer.legalName(), request.issueDate(), request.dueDate(), currency,
                null, request.paymentMethodId(), request.notes());
        for (ResolvedDocumentLine line : resolvedLines) {
            document.addLine(toLine(line));
        }
        document.recalculate(calculator);
        DocumentCurrencySnapshot currencySnapshot = currencyService.resolve(document.getCurrency(), request.issueDate());
        document.applyCurrencySnapshot(currencySnapshot.baseCurrency(), currencySnapshot.exchangeRate(),
                currencySnapshot.rateDate(), currencySnapshot.source());
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
        if (source.getType() == DocumentType.QUOTE) {
            source.expireQuoteIfDue(LocalDate.now());
        }
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
                numberGenerator.next(companyId, targetType, issueDate, null), targetType, source.getCustomerId(),
                source.getCustomerCodeSnapshot(), source.getCustomerNameSnapshot(), issueDate, source.getDueDate(),
                source.getCurrency(), source.getId(), source.getPaymentMethodId(), source.getNotes());
        for (DocumentLine line : source.getLines()) {
            target.addLine(line.copySnapshot());
        }
        target.recalculate(calculator);
        target.applyCurrencySnapshot(source.getBaseCurrency(), source.getExchangeRate(), source.getExchangeRateDate(),
                source.getExchangeRateSource());
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
        if (status == PaymentStatus.NOT_APPLICABLE) {
            throw new BusinessRuleException("Una factura debe tener un estado de cobro aplicable.");
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
    private DocumentLine toLine(ResolvedDocumentLine line) {
        return new DocumentLine(line.productId(), line.productCode(), line.description(), line.requestedQuantity(),
                line.billedQuantity(), line.displayUnitPrice(), line.discountPercentage(), line.taxPercentage(),
                line.tariffId(), line.tariffCode(), line.pricingResolvedAmount(), line.pricingTraceJson(),
                line.taxCodeId(), line.taxCode(), line.taxCountryCode(), line.taxName(), line.taxExempt());
    }
}
