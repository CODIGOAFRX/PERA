package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.outbox.DomainEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {
    @Mock CommercialDocumentRepository repository;
    @Mock DocumentService documentService;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock DomainEventRecorder events;

    private final UUID companyId = UUID.randomUUID();
    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(repository, documentService, companyProvider, events);
        lenient().when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsDedicatedQuoteAndCanSendItImmediately() {
        CommercialDocument quote = quote();
        when(documentService.create(any())).thenReturn(DocumentResponse.from(quote));
        when(repository.findByIdAndCompanyIdAndType(quote.getId(), companyId, DocumentType.QUOTE))
                .thenReturn(Optional.of(quote));
        CreateQuoteRequest request = request(true, LocalDate.of(2026, 9, 15));

        DocumentResponse response = service.create(request);

        assertThat(response.quoteStatus()).isEqualTo(QuoteStatus.SENT);
        assertThat(response.quoteValidUntil()).isEqualTo(LocalDate.of(2026, 9, 15));
        verify(events).record(eq("CommercialDocument"), eq(quote.getId()), eq("QuoteCreated"), any());
    }

    @Test
    void dedicatedConversionRequiresPriorAcceptance() {
        CommercialDocument quote = quote();
        when(repository.findByIdAndCompanyIdAndType(quote.getId(), companyId, DocumentType.QUOTE))
                .thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.convertAccepted(quote.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aceptado");

        quote.confirm();
        quote.acceptQuote(java.time.Instant.now(), LocalDate.of(2026, 8, 11));
        when(documentService.convert(quote.getId())).thenReturn(DocumentResponse.from(quote));
        service.convertAccepted(quote.getId());
        verify(documentService).convert(quote.getId());
    }

    @Test
    void rejectsInvalidValidityBeforeCreatingDocument() {
        CreateQuoteRequest invalid = request(false, LocalDate.of(2026, 8, 9));
        assertThatThrownBy(() -> service.create(invalid)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deletesDraftQuote() {
        CommercialDocument quote = quote();
        when(repository.findByIdAndCompanyIdAndType(quote.getId(), companyId, DocumentType.QUOTE))
                .thenReturn(Optional.of(quote));

        service.delete(quote.getId());

        verify(repository).delete(quote);
        verify(events).record(eq("CommercialDocument"), eq(quote.getId()), eq("QuoteDeleted"), any());
    }

    @Test
    void refusesToDeleteQuoteThatIsNoLongerDraft() {
        CommercialDocument quote = quote();
        quote.confirm();
        when(repository.findByIdAndCompanyIdAndType(quote.getId(), companyId, DocumentType.QUOTE))
                .thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.delete(quote.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("borrador");
        verify(repository, never()).delete(quote);
    }

    private CreateQuoteRequest request(boolean send, LocalDate validUntil) {
        return new CreateQuoteRequest(UUID.randomUUID(), "C-1", "Cliente", LocalDate.of(2026, 8, 10),
                validUntil, "EUR", null, null, send,
                List.of(new DocumentLineRequest(null, null, "Servicio", java.math.BigDecimal.ONE,
                        java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, new java.math.BigDecimal("21"))), null);
    }

    private CommercialDocument quote() {
        CommercialDocument quote = new CommercialDocument(companyId, "PRE-1", DocumentType.QUOTE, UUID.randomUUID(),
                "C-1", "Cliente", LocalDate.of(2026, 8, 10), null, "EUR", null, null, null);
        ReflectionTestUtils.setField(quote, "id", UUID.randomUUID());
        return quote;
    }
}
