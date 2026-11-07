package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.currency.DocumentCurrencyService;
import com.peraerp.sales.currency.DocumentCurrencySnapshot;
import com.peraerp.sales.outbox.DomainEventRecorder;
import com.peraerp.sales.masterdata.CustomerSnapshot;
import com.peraerp.sales.masterdata.ResolvedDocumentLine;
import com.peraerp.sales.masterdata.SalesMasterDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock CommercialDocumentRepository documents;
    @Mock DocumentNumberGenerator numberGenerator;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock DomainEventRecorder events;
    @Mock DocumentCurrencyService currencyService;
    @Mock SalesMasterDataService masterDataService;
    @Mock com.peraerp.sales.verifactu.VerifactuIssuanceService verifactuIssuance;
    @Mock com.peraerp.sales.numbering.NumberingSchemeRepository numberingSchemes;

    private final UUID companyId = UUID.randomUUID();
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documents, numberGenerator, new DocumentAmountsCalculator(), companyProvider,
                events, currencyService, masterDataService, verifactuIssuance, new CreditRiskService(documents, companyProvider), numberingSchemes);
        // La serie predeterminada de cada tipo, con el comportamiento de siempre; su número sale del contador del tipo.
        lenient().when(numberGenerator.resolveScheme(any(), any(), any())).thenAnswer(invocation ->
                new com.peraerp.sales.numbering.NumberingScheme(companyId, "AUTO", "Automática", invocation.getArgument(1),
                        "X", "{seq}", com.peraerp.sales.numbering.NumberingResetPeriod.YEARLY, 1, true, true));
        lenient().when(numberGenerator.next(any(), any(com.peraerp.sales.numbering.NumberingScheme.class), any())).thenAnswer(invocation ->
                numberGenerator.next(companyId, ((com.peraerp.sales.numbering.NumberingScheme) invocation.getArgument(1)).getDocumentType(),
                        invocation.getArgument(2), null));
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        lenient().when(masterDataService.requireActiveCustomer(any())).thenAnswer(invocation ->
                new CustomerSnapshot(invocation.getArgument(0), "C001", "Cliente Demo", true));
        lenient().when(masterDataService.resolveLine(any(), any(), any(), any())).thenAnswer(invocation -> {
            DocumentLineRequest line = invocation.getArgument(1);
            return new ResolvedDocumentLine(line.productId(), line.productCode(), line.description(), line.quantity(),
                    line.quantity(), line.unitPrice(), line.discountPercentage(), line.taxPercentage(),
                    null, null, null, null);
        });
    }

    @Test
    void createsConfirmedDocumentWithReproducibleTotalsAndOutboxEvent() {
        when(numberGenerator.next(companyId, DocumentType.QUOTE, LocalDate.of(2026, 8, 7), null))
                .thenReturn("PRE-2026-000001");
        when(currencyService.resolve("EUR", LocalDate.of(2026, 8, 7)))
                .thenReturn(new DocumentCurrencySnapshot("EUR", BigDecimal.ONE,
                        LocalDate.of(2026, 8, 7), "IDENTITY"));
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        DocumentResponse response = service.create(request(DocumentType.QUOTE, true));

        assertThat(response.number()).isEqualTo("PRE-2026-000001");
        assertThat(response.status()).isEqualTo(DocumentStatus.CONFIRMED);
        assertThat(response.netAmount()).isEqualByComparingTo("90.0000");
        assertThat(response.taxAmount()).isEqualByComparingTo("18.9000");
        assertThat(response.totalAmount()).isEqualByComparingTo("108.9000");
        verify(events).record(eq("CommercialDocument"), eq(response.id()), eq("DocumentCreated"), any());
    }

    @Test
    void convertsConfirmedQuoteToDeliveryNoteAndKeepsTraceability() {
        UUID sourceId = UUID.randomUUID();
        CommercialDocument source = document(sourceId, DocumentType.QUOTE);
        source.addLine(new DocumentLine(UUID.randomUUID(), "A001", "Servicio", BigDecimal.ONE,
                new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("21")));
        source.configureQuoteValidity(LocalDate.now().plusDays(30));
        source.applyCustomerContactSnapshot("client@example.test", "Calle Mayor 1, Madrid");
        source.recalculate(new DocumentAmountsCalculator());
        source.confirm();
        source.acceptQuote(java.time.Instant.now(), LocalDate.now());
        when(documents.findByIdAndCompanyId(sourceId, companyId)).thenReturn(Optional.of(source));
        when(numberGenerator.next(eq(companyId), eq(DocumentType.DELIVERY_NOTE), any(LocalDate.class), eq(null)))
                .thenReturn("ALB-2026-000001");
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        DocumentResponse response = service.convert(sourceId);

        assertThat(response.type()).isEqualTo(DocumentType.DELIVERY_NOTE);
        assertThat(response.sourceDocumentId()).isEqualTo(sourceId);
        assertThat(response.status()).isEqualTo(DocumentStatus.CONFIRMED);
        assertThat(source.getStatus()).isEqualTo(DocumentStatus.CONVERTED);
        verify(events).record(eq("CommercialDocument"), eq(response.id()), eq("DocumentConverted"), any());
    }

    @Test
    void rejectsConversionOfDraftDocuments() {
        UUID sourceId = UUID.randomUUID();
        when(documents.findByIdAndCompanyId(sourceId, companyId))
                .thenReturn(Optional.of(document(sourceId, DocumentType.QUOTE)));

        assertThatThrownBy(() -> service.convert(sourceId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("confirmados");
    }

    @Test
    void rejectsPaymentStatusForNonInvoicesAndNotApplicableInvoices() {
        UUID quoteId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(documents.findByIdAndCompanyId(quoteId, companyId))
                .thenReturn(Optional.of(document(quoteId, DocumentType.QUOTE)));
        when(documents.findByIdAndCompanyId(invoiceId, companyId))
                .thenReturn(Optional.of(document(invoiceId, DocumentType.INVOICE)));

        assertThatThrownBy(() -> service.updatePaymentStatus(quoteId, PaymentStatus.PAID))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.updatePaymentStatus(invoiceId, PaymentStatus.NOT_APPLICABLE))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void convertsTheReportedDeliveryNoteWithRoundedInvoiceTotals() {
        UUID sourceId = UUID.randomUUID();
        CommercialDocument source = document(sourceId, DocumentType.DELIVERY_NOTE);
        source.addLine(new DocumentLine(null, "P1", "Creatina", new BigDecimal("2"),
                new BigDecimal("27.95"), BigDecimal.ZERO, new BigDecimal("21")));
        source.recalculate(new DocumentAmountsCalculator());
        // Existing delivery notes retain the original four-decimal amounts in storage.
        ReflectionTestUtils.setField(source, "taxAmount", new BigDecimal("11.7390"));
        ReflectionTestUtils.setField(source, "totalAmount", new BigDecimal("67.6390"));
        source.confirm();
        when(documents.findByIdAndCompanyId(sourceId, companyId)).thenReturn(Optional.of(source));
        when(numberGenerator.next(eq(companyId), eq(DocumentType.INVOICE), any(), eq(null))).thenReturn("FAC-TEST");
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        DocumentResponse result = service.convert(sourceId);

        assertThat(result.taxAmount()).isEqualByComparingTo("11.74");
        assertThat(result.totalAmount()).isEqualByComparingTo("67.64");
        assertThat(source.getTaxAmount()).isEqualByComparingTo("11.7390");
        verify(verifactuIssuance).recordIssuance(any(CommercialDocument.class));
    }

    @Test
    void genericConversionCannotBypassQuoteAcceptance() {
        UUID sourceId = UUID.randomUUID();
        CommercialDocument source = document(sourceId, DocumentType.QUOTE);
        source.configureQuoteValidity(LocalDate.now().plusDays(30));
        source.confirm();
        when(documents.findByIdAndCompanyId(sourceId, companyId)).thenReturn(Optional.of(source));
        assertThatThrownBy(() -> service.convert(sourceId)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aceptado");
        org.mockito.Mockito.verifyNoInteractions(numberGenerator, events, verifactuIssuance);
    }

    @Test
    void draftInvoicesCannotBeRectified() {
        UUID originalId = UUID.randomUUID();
        CommercialDocument original = document(originalId, DocumentType.INVOICE);
        when(documents.findByIdAndCompanyId(originalId, companyId)).thenReturn(Optional.of(original));
        var basic = request(DocumentType.RECTIFYING_INVOICE, true);
        when(currencyService.resolve("EUR", basic.issueDate())).thenReturn(
                new DocumentCurrencySnapshot("EUR", BigDecimal.ONE, basic.issueDate(), "IDENTITY"));
        var request = new CreateDocumentRequest(basic.type(), basic.customerId(), basic.customerCode(),
                basic.customerName(), basic.issueDate(), basic.dueDate(), basic.currency(), basic.paymentMethodId(),
                basic.notes(), true, basic.lines(), null,
                com.peraerp.sales.verifactu.domain.InvoiceKind.R1,
                com.peraerp.sales.verifactu.domain.RectificationType.DIFFERENCES, originalId);
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("borradores");
        org.mockito.Mockito.verify(documents, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void issuesADraftInvoiceAndRegistersItInVerifactu() {
        UUID id = UUID.randomUUID();
        CommercialDocument draft = document(id, DocumentType.INVOICE);
        when(documents.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(draft));
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponse response = service.confirmDraft(id, false);

        assertThat(response.status()).isEqualTo(DocumentStatus.CONFIRMED);
        assertThat(draft.isIssued()).isTrue();
        verify(verifactuIssuance).recordIssuance(draft);
        verify(events).record(eq("CommercialDocument"), eq(id), eq("DocumentConfirmed"), any());
    }

    @Test
    void onlyDraftsOtherThanQuotesCanBeConfirmedHere() {
        UUID invoiceId = UUID.randomUUID();
        CommercialDocument issued = document(invoiceId, DocumentType.INVOICE);
        issued.confirm();
        when(documents.findByIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.of(issued));
        assertThatThrownBy(() -> service.confirmDraft(invoiceId, false)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("borrador");

        UUID quoteId = UUID.randomUUID();
        when(documents.findByIdAndCompanyId(quoteId, companyId)).thenReturn(Optional.of(document(quoteId, DocumentType.QUOTE)));
        assertThatThrownBy(() -> service.confirmDraft(quoteId, false)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Presupuestos");
        org.mockito.Mockito.verifyNoInteractions(verifactuIssuance);
    }

    @Test
    void takesTheSalespersonOfTheCustomerAndLetsItBeChanged() {
        UUID salespersonId = UUID.randomUUID();
        when(masterDataService.resolveSalesperson(any(), any())).thenReturn(
                new com.peraerp.sales.masterdata.SalespersonSnapshot(salespersonId, "V01", "Marta Ruiz", null, true));
        when(numberGenerator.next(any(), any(), any(), any())).thenReturn("ALB-2026-000001");
        when(currencyService.resolve(any(), any())).thenReturn(new DocumentCurrencySnapshot("EUR", BigDecimal.ONE,
                LocalDate.of(2026, 8, 7), "IDENTITY"));
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        DocumentResponse created = service.create(request(DocumentType.DELIVERY_NOTE, true));

        assertThat(created.salespersonId()).isEqualTo(salespersonId);
        assertThat(created.salespersonName()).isEqualTo("Marta Ruiz");

        UUID id = UUID.randomUUID();
        CommercialDocument stored = document(id, DocumentType.DELIVERY_NOTE);
        when(documents.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(stored));
        UUID retired = UUID.randomUUID();
        when(masterDataService.findSalesperson(retired)).thenReturn(
                new com.peraerp.sales.masterdata.SalespersonSnapshot(retired, "V02", "Retirado", null, false));
        assertThatThrownBy(() -> service.changeSalesperson(id, retired)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("baja");
        assertThat(service.changeSalesperson(id, null).salespersonId()).isNull();
    }

    // --- series de documentos (fseries de DimproCristalWin) ---

    private com.peraerp.sales.numbering.NumberingScheme series(DocumentType type, String code,
                                                               com.peraerp.sales.numbering.SeriesOptions options) {
        var scheme = new com.peraerp.sales.numbering.NumberingScheme(companyId, code, code, type, code, "{seq}",
                com.peraerp.sales.numbering.NumberingResetPeriod.YEARLY, 1, true, false);
        ReflectionTestUtils.setField(scheme, "id", UUID.randomUUID());
        scheme.configure(options);
        return scheme;
    }

    private static com.peraerp.sales.numbering.SeriesOptions options(java.util.function.UnaryOperator<java.util.Map<String, Object>> change) {
        var d = com.peraerp.sales.numbering.SeriesOptions.defaults();
        var m = new java.util.HashMap<String, Object>();
        m.put("next", d.nextSchemeId()); m.put("sameNumber", false); m.put("sameDate", false); m.put("asks", false);
        m.put("notes", true); m.put("priced", true); m.put("internal", false); m.put("zero", false); m.put("risk", true);
        change.apply(m);
        return new com.peraerp.sales.numbering.SeriesOptions(0, null, null, (UUID) m.get("next"), (boolean) m.get("sameNumber"),
                (boolean) m.get("sameDate"), (boolean) m.get("asks"), (boolean) m.get("notes"), null, 0, (boolean) m.get("priced"),
                (boolean) m.get("internal"), (boolean) m.get("zero"), (boolean) m.get("risk"), 0);
    }

    @Test
    void convertingGoesToTheNextSeriesOfTheSourceWithItsNumberAndDateAndWithoutNotesWhenTheSeriesSaysSo() {
        var invoiceSeries = series(DocumentType.INVOICE, "FC", options(m -> { m.put("sameNumber", true); m.put("sameDate", true); return m; }));
        var deliverySeries = series(DocumentType.DELIVERY_NOTE, "AT", options(m -> { m.put("next", invoiceSeries.getId()); m.put("notes", false); return m; }));
        UUID sourceId = UUID.randomUUID();
        CommercialDocument source = document(sourceId, DocumentType.DELIVERY_NOTE);
        ReflectionTestUtils.setField(source, "notes", "Entregar por la mañana");
        source.addLine(new DocumentLine(UUID.randomUUID(), "A001", "Servicio", BigDecimal.ONE, new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("21")));
        source.assignSeries(deliverySeries.getId());
        source.applyCustomerTaxSnapshot("B12345674", null, "ES");
        source.recalculate(new DocumentAmountsCalculator());
        source.confirm();
        when(documents.findByIdAndCompanyId(sourceId, companyId)).thenReturn(Optional.of(source));
        when(numberingSchemes.findByIdAndCompanyId(deliverySeries.getId(), companyId)).thenReturn(Optional.of(deliverySeries));
        when(numberingSchemes.findByIdAndCompanyId(invoiceSeries.getId(), companyId)).thenReturn(Optional.of(invoiceSeries));
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        DocumentResponse response = service.convert(sourceId);

        assertThat(response.numberingSchemeId()).isEqualTo(invoiceSeries.getId());
        assertThat(response.number()).isEqualTo("DOC-1");
        assertThat(response.issueDate()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(response.notes()).isNull();
    }

    @Test
    void aSeriesThatAsksForTheNumberTakesItFromTheRequestAndNeverFromTheCounter() {
        var manual = series(DocumentType.QUOTE, "M", options(m -> { m.put("asks", true); return m; }));
        when(numberGenerator.resolveScheme(companyId, DocumentType.QUOTE, manual.getId())).thenReturn(manual);
        when(currencyService.resolve(any(), any())).thenReturn(new DocumentCurrencySnapshot("EUR", BigDecimal.ONE, LocalDate.of(2026, 8, 7), "IDENTITY"));
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        var base = request(DocumentType.QUOTE, true);
        var withNumber = new CreateDocumentRequest(base.type(), base.customerId(), base.customerCode(), base.customerName(),
                base.issueDate(), null, "EUR", null, null, true, base.lines(), manual.getId(), null, null, null, null, "229258");

        assertThat(service.create(withNumber).number()).isEqualTo("229258");
        verify(numberGenerator, org.mockito.Mockito.never()).next(any(), any(com.peraerp.sales.numbering.NumberingScheme.class), any());

        var withoutNumber = new CreateDocumentRequest(base.type(), base.customerId(), base.customerCode(), base.customerName(),
                base.issueDate(), null, "EUR", null, null, true, base.lines(), manual.getId(), null, null, null, null, null);
        assertThatThrownBy(() -> service.create(withoutNumber)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("pide el número");
    }

    @Test
    void aReplacementSeriesPutsEveryAmountToZero() {
        var replacement = series(DocumentType.DELIVERY_NOTE, "R", options(m -> { m.put("zero", true); return m; }));
        when(numberGenerator.resolveScheme(companyId, DocumentType.DELIVERY_NOTE, replacement.getId())).thenReturn(replacement);
        when(numberGenerator.next(companyId, DocumentType.DELIVERY_NOTE, LocalDate.of(2026, 8, 7), null)).thenReturn("REP-1");
        when(currencyService.resolve(any(), any())).thenReturn(new DocumentCurrencySnapshot("EUR", BigDecimal.ONE, LocalDate.of(2026, 8, 7), "IDENTITY"));
        when(documents.save(any(CommercialDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        var base = request(DocumentType.DELIVERY_NOTE, true);
        var request = new CreateDocumentRequest(base.type(), base.customerId(), base.customerCode(), base.customerName(),
                base.issueDate(), null, "EUR", null, null, true, base.lines(), replacement.getId(), null, null, null, null, null);

        DocumentResponse response = service.create(request);

        assertThat(response.totalAmount()).isEqualByComparingTo("0");
        assertThat(response.lines()).hasSize(1);
    }

    private CreateDocumentRequest request(DocumentType type, boolean confirm) {
        return new CreateDocumentRequest(type, UUID.randomUUID(), "C001", "Cliente Demo",
                LocalDate.of(2026, 8, 7), null, "EUR", null, "Primera operación", confirm,
                List.of(new DocumentLineRequest(UUID.randomUUID(), "A001", "Servicio", new BigDecimal("2"),
                        new BigDecimal("50"), new BigDecimal("10"), new BigDecimal("21"))));
    }

    private CommercialDocument document(UUID id, DocumentType type) {
        CommercialDocument document = new CommercialDocument(companyId, "DOC-1", type, UUID.randomUUID(),
                "C001", "Cliente Demo", LocalDate.of(2026, 8, 7), null, "EUR", null, null, null);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private CommercialDocument withId(CommercialDocument document) {
        if (document.getId() == null) {
            ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
        }
        return document;
    }
}
