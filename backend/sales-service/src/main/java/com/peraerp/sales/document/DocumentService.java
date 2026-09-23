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
import com.peraerp.sales.verifactu.VerifactuIssuanceService;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
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
    private final VerifactuIssuanceService verifactuIssuance;
    private final CreditRiskService creditRisk;

    public DocumentService(CommercialDocumentRepository repository, DocumentNumberGenerator numberGenerator,
                           DocumentAmountsCalculator calculator, CurrentCompanyProvider companyProvider,
                           DomainEventRecorder events, DocumentCurrencyService currencyService,
                           SalesMasterDataService masterDataService, VerifactuIssuanceService verifactuIssuance,
                           CreditRiskService creditRisk) {
        this.repository=repository; this.numberGenerator=numberGenerator; this.calculator=calculator;
        this.companyProvider=companyProvider; this.events=events; this.currencyService=currencyService;
        this.masterDataService=masterDataService; this.verifactuIssuance=verifactuIssuance; this.creditRisk=creditRisk;
    }

    @Transactional
    public DocumentResponse create(CreateDocumentRequest request) { return create(request, false); }

    /** {@code riskAcknowledged}: the user has seen and accepted the customer's credit risk warning. */
    @Transactional
    public DocumentResponse create(CreateDocumentRequest request, boolean riskAcknowledged) {
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
        document.applyCustomerContactSnapshot(customer.email(), customer.details() == null ? null : customer.details().postalAddress());
        document.applyCustomerTaxSnapshot(customer.taxId(), customer.taxIdentificationType(),
                customer.taxCountryCode());
        DocumentCurrencySnapshot currencySnapshot = currencyService.resolve(document.getCurrency(), request.issueDate());
        document.applyCurrencySnapshot(currencySnapshot.baseCurrency(), currencySnapshot.exchangeRate(),
                currencySnapshot.rateDate(), currencySnapshot.source());
        // A draft commits nothing yet: its risk is checked when it is confirmed.
        if (request.confirm() && CreditRiskService.CHECKED_TYPES.contains(request.type())) {
            creditRisk.enforce(creditRisk.assess(companyId, customer, document.getBaseTotalAmount()), riskAcknowledged);
        }
        if (request.type().isInvoice()) {
            applyFiscalClassification(document, request, companyId);
        }
        if (request.confirm()) document.confirm();
        document = repository.save(document);
        if (document.isIssued()) {
            verifactuIssuance.recordIssuance(document);
        }
        events.record("CommercialDocument", document.getId(), "DocumentCreated",
                Map.of("documentId", document.getId(), "number", document.getDocumentNumber(), "type", document.getType(),
                        "total", document.getTotalAmount(), "companyId", companyId));
        return DocumentResponse.from(document);
    }

    /** Preview for the document form; the amount is in the document currency. */
    @Transactional(readOnly = true)
    public CreditRiskService.Assessment previewCreditRisk(UUID customerId, BigDecimal amount, String currency, LocalDate date) {
        UUID companyId = companyProvider.requireCompanyId();
        CustomerSnapshot customer = masterDataService.requireActiveCustomer(customerId);
        String code = currency == null || currency.isBlank() ? "EUR" : currency.trim().toUpperCase(Locale.ROOT);
        DocumentCurrencySnapshot rate = currencyService.resolve(code, date == null ? LocalDate.now() : date);
        BigDecimal base = MonetaryRounding.round((amount == null ? BigDecimal.ZERO : amount).multiply(rate.exchangeRate()));
        return creditRisk.assess(companyId, customer, base);
    }

    @Transactional(readOnly = true)
    public DocumentResponse findById(UUID id) { return DocumentResponse.from(requireDocument(id)); }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(String query, DocumentType type, DocumentStatus status, UUID customerId,
                                          LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim();
        return repository.search(companyProvider.requireCompanyId(), normalizedQuery, type, status, customerId,
                        fromDate, toDate, pageable)
                .map(DocumentResponse::from);
    }

    /**
     * Confirms a draft saved without "confirm on save". An invoice is issued at this point and gets its
     * Veri*Factu record, exactly as when it is confirmed on creation. Quotes are sent from the quotes flow.
     */
    @Transactional
    public DocumentResponse confirmDraft(UUID id, boolean riskAcknowledged) {
        UUID companyId = companyProvider.requireCompanyId();
        CommercialDocument document = requireDocument(id);
        if (document.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessRuleException("Solo se pueden confirmar documentos en borrador.");
        }
        if (document.getType() == DocumentType.QUOTE) {
            throw new BusinessRuleException("Los presupuestos se envían desde Presupuestos.");
        }
        if (CreditRiskService.CHECKED_TYPES.contains(document.getType())) {
            CustomerSnapshot customer = masterDataService.requireActiveCustomer(document.getCustomerId());
            creditRisk.enforce(creditRisk.assess(companyId, customer, document.getBaseTotalAmount()), riskAcknowledged);
        }
        document.confirm();
        document = repository.save(document);
        if (document.isIssued()) {
            verifactuIssuance.recordIssuance(document);
        }
        events.record("CommercialDocument", document.getId(), "DocumentConfirmed",
                Map.of("documentId", document.getId(), "number", document.getDocumentNumber(), "type", document.getType(),
                        "total", document.getTotalAmount(), "companyId", companyId));
        return DocumentResponse.from(document);
    }

    @Transactional
    public DocumentResponse convert(UUID sourceId) { return convert(sourceId, false); }

    @Transactional
    public DocumentResponse convert(UUID sourceId, boolean riskAcknowledged) {
        UUID companyId = companyProvider.requireCompanyId();
        CommercialDocument source = requireDocument(sourceId);
        if (source.getType() == DocumentType.QUOTE) {
            source.expireQuoteIfDue(LocalDate.now());
        }
        if (source.getStatus() != DocumentStatus.CONFIRMED) {
            throw new BusinessRuleException("Solo se pueden convertir documentos confirmados.");
        }
        if (source.getType() == DocumentType.QUOTE && source.getQuoteStatus() != QuoteStatus.ACCEPTED) {
            throw new BusinessRuleException("El presupuesto debe estar aceptado antes de convertirlo.");
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
        target.applyCustomerContactSnapshot(source.getCustomerEmailSnapshot(), source.getCustomerAddressSnapshot());
        target.applyCustomerTaxSnapshot(source.getCustomerTaxIdSnapshot(),
                source.getCustomerTaxIdentificationTypeSnapshot(), source.getCustomerTaxCountrySnapshot());
        target.applyCurrencySnapshot(source.getBaseCurrency(), source.getExchangeRate(), source.getExchangeRateDate(),
                source.getExchangeRateSource());
        // A quote becomes a new commitment; a delivery note was already checked when it was created.
        if (source.getType() == DocumentType.QUOTE) {
            CustomerSnapshot customer = masterDataService.requireActiveCustomer(source.getCustomerId());
            creditRisk.enforce(creditRisk.assess(companyId, customer, target.getBaseTotalAmount()), riskAcknowledged);
        }
        target.confirm();
        source.markConverted();
        target = repository.save(target);
        if (target.isIssued()) {
            verifactuIssuance.recordIssuance(target);
        }
        events.record("CommercialDocument", target.getId(), "DocumentConverted",
                Map.of("sourceDocumentId", sourceId, "targetDocumentId", target.getId(), "targetType", targetType,
                        "companyId", companyId, "total", target.getTotalAmount()));
        return DocumentResponse.from(target);
    }

    @Transactional
    public DocumentResponse updatePaymentStatus(UUID id, PaymentStatus status) {
        CommercialDocument document = requireDocument(id);
        if (!document.getType().isInvoice()) {
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

    /**
     * Traslada al documento el tipo fiscal solicitado y, si es una rectificativa, congela los datos
     * de la factura rectificada.
     *
     * <p>El número y la fecha de la factura rectificada se copian aquí y no se vuelven a leer: el
     * registro que se remita a la AEAT tiene que poder reconstruirse años después aunque la
     * factura original haya cambiado de estado.</p>
     */
    private void applyFiscalClassification(CommercialDocument document, CreateDocumentRequest request, UUID companyId) {
        InvoiceKind kind = request.invoiceKind() != null ? request.invoiceKind() : document.getInvoiceKind();
        if (request.type() == DocumentType.RECTIFYING_INVOICE && kind == null) {
            throw new BusinessRuleException(
                    "Indica el motivo de la rectificación (R1 a R5) para emitir una factura rectificativa.");
        }
        CommercialDocument rectified = null;
        if (request.rectifiedDocumentId() != null) {
            rectified = repository.findByIdAndCompanyId(request.rectifiedDocumentId(), companyId)
                    .orElseThrow(() -> new BusinessRuleException(
                            "La factura que se pretende rectificar no existe en la empresa activa."));
            if (!rectified.getType().isInvoice()) {
                throw new BusinessRuleException("Solo se pueden rectificar facturas.");
            }
            if (!rectified.isIssued()) {
                throw new BusinessRuleException("Solo se pueden rectificar facturas expedidas, no borradores.");
            }
        }
        document.classify(kind, request.rectificationType(),
                rectified == null ? null : rectified.getId(),
                rectified == null ? null : rectified.getDocumentNumber(),
                rectified == null ? null : rectified.getIssueDate());
    }

    private CommercialDocument requireDocument(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Documento", id));
    }
    private DocumentLine toLine(ResolvedDocumentLine line) {
        DocumentLine documentLine = new DocumentLine(line.productId(), line.productCode(), line.description(),
                line.requestedQuantity(), line.billedQuantity(), line.displayUnitPrice(), line.discountPercentage(),
                line.taxPercentage(), line.tariffId(), line.tariffCode(), line.pricingResolvedAmount(),
                line.pricingTraceJson(), line.taxCodeId(), line.taxCode(), line.taxCountryCode(), line.taxName(),
                line.taxExempt());
        documentLine.applyFiscalQualification(line.taxQualification(), line.taxExemptionCause(),
                line.taxRegimeKey());
        return documentLine;
    }
}
