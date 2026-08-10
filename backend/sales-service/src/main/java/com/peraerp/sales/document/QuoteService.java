package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.outbox.DomainEventRecorder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class QuoteService {
    private final CommercialDocumentRepository repository;
    private final DocumentService documentService;
    private final CurrentCompanyProvider companyProvider;
    private final DomainEventRecorder events;

    public QuoteService(CommercialDocumentRepository repository, DocumentService documentService,
                        CurrentCompanyProvider companyProvider, DomainEventRecorder events) {
        this.repository = repository;
        this.documentService = documentService;
        this.companyProvider = companyProvider;
        this.events = events;
    }

    @Transactional
    public DocumentResponse create(CreateQuoteRequest request) {
        if (request.validUntil().isBefore(request.issueDate())) {
            throw new BusinessRuleException("La validez del presupuesto no puede ser anterior a su fecha de emisión.");
        }
        DocumentResponse created = documentService.create(request.toDocumentRequest());
        CommercialDocument quote = requireQuote(created.id());
        quote.configureQuoteValidity(request.validUntil());
        if (request.sendOnCreate()) quote.confirm();
        events.record("CommercialDocument", quote.getId(), "QuoteCreated",
                Map.of("documentId", quote.getId(), "validUntil", quote.getQuoteValidUntil(),
                        "status", quote.getQuoteStatus(), "companyId", quote.getCompanyId()));
        return DocumentResponse.from(quote);
    }

    @Transactional
    public Page<DocumentResponse> search(QuoteStatus status, UUID customerId, LocalDate fromDate,
                                         LocalDate toDate, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        repository.expireDueQuotes(companyId, LocalDate.now());
        return repository.searchQuotes(companyId, status, customerId, fromDate, toDate, pageable)
                .map(DocumentResponse::from);
    }

    @Transactional
    public DocumentResponse findById(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        repository.expireDueQuotes(companyId, LocalDate.now());
        return DocumentResponse.from(requireQuote(id, companyId));
    }

    @Transactional
    public DocumentResponse send(UUID id) {
        CommercialDocument quote = requireQuote(id);
        quote.confirm();
        record(quote, "QuoteSent");
        return DocumentResponse.from(quote);
    }

    @Transactional
    public DocumentResponse accept(UUID id) {
        CommercialDocument quote = requireQuote(id);
        quote.acceptQuote(Instant.now(), LocalDate.now());
        record(quote, "QuoteAccepted");
        return DocumentResponse.from(quote);
    }

    @Transactional
    public DocumentResponse reject(UUID id, String reason) {
        CommercialDocument quote = requireQuote(id);
        quote.rejectQuote(reason.trim(), Instant.now(), LocalDate.now());
        record(quote, "QuoteRejected");
        return DocumentResponse.from(quote);
    }

    @Transactional
    public DocumentResponse convertAccepted(UUID id) {
        CommercialDocument quote = requireQuote(id);
        quote.expireQuoteIfDue(LocalDate.now());
        if (quote.getQuoteStatus() != QuoteStatus.ACCEPTED) {
            throw new BusinessRuleException("El presupuesto debe estar aceptado antes de convertirlo.");
        }
        return documentService.convert(id);
    }

    private CommercialDocument requireQuote(UUID id) {
        return requireQuote(id, companyProvider.requireCompanyId());
    }

    private CommercialDocument requireQuote(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyIdAndType(id, companyId, DocumentType.QUOTE)
                .orElseThrow(() -> new ResourceNotFoundException("Presupuesto", id));
    }

    private void record(CommercialDocument quote, String eventType) {
        events.record("CommercialDocument", quote.getId(), eventType,
                Map.of("documentId", quote.getId(), "status", quote.getQuoteStatus(),
                        "companyId", quote.getCompanyId()));
    }
}
