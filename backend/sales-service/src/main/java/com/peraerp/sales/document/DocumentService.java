package com.peraerp.sales.document;

import com.peraerp.sales.numbering.NumberingScheme;
import com.peraerp.sales.numbering.NumberingSchemeRepository;
import com.peraerp.sales.numbering.SeriesOptions;

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
    private final NumberingSchemeRepository numberingSchemes;

    public DocumentService(CommercialDocumentRepository repository, DocumentNumberGenerator numberGenerator,
                           DocumentAmountsCalculator calculator, CurrentCompanyProvider companyProvider,
                           DomainEventRecorder events, DocumentCurrencyService currencyService,
                           SalesMasterDataService masterDataService, VerifactuIssuanceService verifactuIssuance,
                           CreditRiskService creditRisk, NumberingSchemeRepository numberingSchemes) {
        this.repository=repository; this.numberGenerator=numberGenerator; this.calculator=calculator;
        this.companyProvider=companyProvider; this.events=events; this.currencyService=currencyService;
        this.masterDataService=masterDataService; this.verifactuIssuance=verifactuIssuance; this.creditRisk=creditRisk;
        this.numberingSchemes=numberingSchemes;
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
        NumberingScheme series = numberGenerator.resolveScheme(companyId, request.type(), request.numberingSchemeId());
        String number = numberFor(companyId, series, request);
        CommercialDocument document = new CommercialDocument(companyId, number, request.type(), customer.id(),
                customer.code(), customer.legalName(), request.issueDate(), request.dueDate(), currency,
                null, request.paymentMethodId(), request.notes());
        var salesperson = masterDataService.resolveSalesperson(request.salespersonId(), customer);
        if (salesperson != null) {
            document.assignSalesperson(salesperson.id(), salesperson.name());
        }
        for (ResolvedDocumentLine line : resolvedLines) {
            document.addLine(toLine(line));
        }
        document.recalculate(calculator);
        document.assignSeries(series.getId());
        if (series.options().zeroAmounts()) document.zeroAmounts(calculator);
        document.applyCustomerContactSnapshot(customer.email(), customer.details() == null ? null : customer.details().postalAddress());
        document.applyCustomerTaxSnapshot(customer.taxId(), customer.taxIdentificationType(),
                customer.taxCountryCode());
        DocumentCurrencySnapshot currencySnapshot = currencyService.resolve(document.getCurrency(), request.issueDate());
        document.applyCurrencySnapshot(currencySnapshot.baseCurrency(), currencySnapshot.exchangeRate(),
                currencySnapshot.rateDate(), currencySnapshot.source());
        // A draft commits nothing yet: its risk is checked when it is confirmed.
        if (request.confirm() && CreditRiskService.CHECKED_TYPES.contains(request.type()) && series.options().countsForRisk()) {
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
        if (CreditRiskService.CHECKED_TYPES.contains(document.getType()) && seriesOf(document).options().countsForRisk()) {
            CustomerSnapshot customer = masterDataService.requireActiveCustomer(document.getCustomerId());
            creditRisk.enforce(creditRisk.assess(companyId, customer, document.getBaseTotalAmount()), riskAcknowledged);
        }
        refreshMissingCustomerTax(document);
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

    /**
     * Una factura que se expide desde un borrador o un albarán lleva los datos fiscales del cliente de cuando
     * se creó. Si entonces no tenía NIF y se ha completado después en su ficha, se toma el de ahora: si no, la
     * factura no se podría expedir aunque el cliente ya esté bien.
     */
    private void refreshMissingCustomerTax(CommercialDocument document) {
        if (!document.getType().isInvoice() || (document.getCustomerTaxIdSnapshot() != null && !document.getCustomerTaxIdSnapshot().isBlank())) {
            return;
        }
        CustomerSnapshot customer = masterDataService.requireActiveCustomer(document.getCustomerId());
        if (customer.taxId() != null && !customer.taxId().isBlank()) {
            document.applyCustomerTaxSnapshot(customer.taxId(), customer.taxIdentificationType(), customer.taxCountryCode());
        }
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
        SeriesOptions sourceSeries = seriesOf(source).options();
        NumberingScheme targetSeries = targetSeries(companyId, sourceSeries, targetType);
        SeriesOptions targetOptions = targetSeries.options();
        LocalDate issueDate = targetOptions.sameDateAsSource() ? source.getIssueDate() : LocalDate.now();
        String number = targetOptions.sameNumberAsSource()
                ? requireFreeNumber(companyId, targetType, source.getDocumentNumber())
                : numberGenerator.next(companyId, targetSeries, issueDate);
        CommercialDocument target = new CommercialDocument(companyId,
                number, targetType, source.getCustomerId(),
                source.getCustomerCodeSnapshot(), source.getCustomerNameSnapshot(), issueDate, source.getDueDate(),
                source.getCurrency(), source.getId(), source.getPaymentMethodId(),
                sourceSeries.carriesNotes() ? source.getNotes() : null);
        target.assignSeries(targetSeries.getId());
        // La venta sigue siendo del mismo comercial al pasar de presupuesto a albarán y de albarán a factura.
        target.assignSalesperson(source.getSalespersonId(), source.getSalespersonName());
        for (DocumentLine line : source.getLines()) {
            target.addLine(line.copySnapshot());
        }
        target.recalculate(calculator);
        if (targetOptions.zeroAmounts()) target.zeroAmounts(calculator);
        target.applyCustomerContactSnapshot(source.getCustomerEmailSnapshot(), source.getCustomerAddressSnapshot());
        target.applyCustomerTaxSnapshot(source.getCustomerTaxIdSnapshot(),
                source.getCustomerTaxIdentificationTypeSnapshot(), source.getCustomerTaxCountrySnapshot());
        target.applyCurrencySnapshot(source.getBaseCurrency(), source.getExchangeRate(), source.getExchangeRateDate(),
                source.getExchangeRateSource());
        // A quote becomes a new commitment; a delivery note was already checked when it was created.
        if (source.getType() == DocumentType.QUOTE && targetOptions.countsForRisk()) {
            CustomerSnapshot customer = masterDataService.requireActiveCustomer(source.getCustomerId());
            creditRisk.enforce(creditRisk.assess(companyId, customer, target.getBaseTotalAmount()), riskAcknowledged);
        }
        refreshMissingCustomerTax(target);
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

    /**
     * Cambia el comercial de un documento, como el «cambio de vendedores» del programa anterior. No es dato
     * fiscal, así que se admite también en facturas expedidas. Sin comercial, el documento queda sin él.
     */
    @Transactional
    public DocumentResponse changeSalesperson(UUID id, UUID salespersonId) {
        CommercialDocument document = requireDocument(id);
        if (salespersonId == null) {
            document.assignSalesperson(null, null);
        } else {
            var salesperson = masterDataService.findSalesperson(salespersonId);
            if (!salesperson.active() && !salespersonId.equals(document.getSalespersonId())) {
                throw new BusinessRuleException("El comercial " + salesperson.name() + " está dado de baja.");
            }
            document.assignSalesperson(salesperson.id(), salesperson.name());
        }
        return DocumentResponse.from(repository.save(document));
    }

    private CommercialDocument requireDocument(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Documento", id));
    }
    /**
     * Número del documento nuevo. Una serie que pide número (pide_numdocu de Dimpro) lo toma de la petición sin
     * tocar el contador; las demás, del contador. Pedir un número en una serie que no lo pide es un error.
     */
    private String numberFor(UUID companyId, NumberingScheme series, CreateDocumentRequest request) {
        String manual = request.documentNumber() == null ? "" : request.documentNumber().trim();
        if (series.options().asksNumber()) {
            if (manual.isEmpty()) throw new BusinessRuleException("La serie " + series.getName() + " pide el número del documento.");
            return requireFreeNumber(companyId, request.type(), manual);
        }
        if (!manual.isEmpty()) throw new BusinessRuleException("La serie " + series.getName() + " no admite número a mano.");
        return numberGenerator.next(companyId, series, request.issueDate());
    }

    private String requireFreeNumber(UUID companyId, DocumentType type, String number) {
        if (repository.existsByCompanyIdAndTypeAndDocumentNumber(companyId, type, number)) {
            throw new BusinessRuleException("Ya hay un documento de este tipo con el número " + number + ".");
        }
        return number;
    }

    /**
     * La serie del documento, aunque se haya desactivado después: sigue mandando en sus documentos. Los antiguos,
     * sin serie, se comportan como la predeterminada de su tipo.
     */
    private NumberingScheme seriesOf(CommercialDocument document) {
        if (document.getNumberingSchemeId() != null) {
            var own = numberingSchemes.findByIdAndCompanyId(document.getNumberingSchemeId(), document.getCompanyId());
            if (own.isPresent()) return own.get();
        }
        return numberGenerator.resolveScheme(document.getCompanyId(), document.getType(), null);
    }

    /** «Pasará al documento…» de la serie de origen, si es del tipo al que se convierte y está activa. */
    private NumberingScheme targetSeries(UUID companyId, SeriesOptions sourceSeries, DocumentType targetType) {
        if (sourceSeries.nextSchemeId() != null) {
            var next = numberingSchemes.findByIdAndCompanyId(sourceSeries.nextSchemeId(), companyId)
                    .filter(scheme -> scheme.isActive() && scheme.getDocumentType() == targetType);
            if (next.isPresent()) return next.get();
        }
        return numberGenerator.resolveScheme(companyId, targetType, null);
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
