package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.outbox.DomainEventRecorder;
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

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    @Mock CommercialDocumentRepository documents;
    @Mock DocumentNumberGenerator numberGenerator;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock DomainEventRecorder events;

    private final UUID companyId = UUID.randomUUID();
    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documents, numberGenerator, new DocumentAmountsCalculator(), companyProvider, events);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsConfirmedDocumentWithReproducibleTotalsAndOutboxEvent() {
        when(numberGenerator.next(companyId, DocumentType.QUOTE, 2026)).thenReturn("PRE-2026-000001");
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
        source.recalculate(new DocumentAmountsCalculator());
        source.confirm();
        when(documents.findByIdAndCompanyId(sourceId, companyId)).thenReturn(Optional.of(source));
        when(numberGenerator.next(eq(companyId), eq(DocumentType.DELIVERY_NOTE), any(Integer.class)))
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
