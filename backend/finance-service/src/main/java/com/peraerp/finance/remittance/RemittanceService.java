package com.peraerp.finance.remittance;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.receivable.CollectionNumbering;
import com.peraerp.finance.receivable.InvoiceCollectionTracker;
import com.peraerp.finance.receivable.Receipt;
import com.peraerp.finance.receivable.ReceiptDtos.ReceiptResponse;
import com.peraerp.finance.receivable.ReceiptRepository;
import com.peraerp.finance.receivable.ReceiptStatus;
import com.peraerp.finance.remittance.RemittanceDtos.RemittanceRequest;
import com.peraerp.finance.remittance.RemittanceDtos.RemittanceResponse;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Remesas de recibos. Esta versión lleva la gestión —qué recibos se presentan, cuándo se envían y
 * cuándo se abonan— pero no genera el fichero para el banco.
 */
@Service
public class RemittanceService {

    private final RemittanceRepository repository;
    private final ReceiptRepository receipts;
    private final CollectionNumbering numbering;
    private final InvoiceCollectionTracker tracker;
    private final CurrentCompanyProvider companyProvider;

    public RemittanceService(RemittanceRepository repository, ReceiptRepository receipts,
                             CollectionNumbering numbering, InvoiceCollectionTracker tracker,
                             CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.receipts = receipts;
        this.numbering = numbering;
        this.tracker = tracker;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public RemittanceResponse create(RemittanceRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        List<Receipt> selected = requireReceipts(companyId, request.receiptIds());
        LocalDate creationDate = request.creationDate() == null ? LocalDate.now() : request.creationDate();
        Remittance remittance = repository.saveAndFlush(new Remittance(companyId,
                numbering.nextRemittanceNumber(companyId, creationDate.getYear()), request.bankAccount().trim(),
                singleCurrency(selected), creationDate, normalize(request.notes())));
        assign(remittance, selected);
        remittance.updateDraft(remittance.getBankAccount(), remittance.getNotes(), total(selected));
        return response(remittance, selected, null);
    }

    /** Sustituye los recibos de una remesa en borrador por los indicados. */
    @Transactional
    public RemittanceResponse update(UUID id, RemittanceRequest request) {
        Remittance remittance = require(id);
        UUID companyId = remittance.getCompanyId();
        if (remittance.getStatus() != RemittanceStatus.DRAFT) {
            throw new BusinessRuleException("Solo se puede modificar una remesa en borrador.");
        }
        Set<UUID> wanted = new LinkedHashSet<>(request.receiptIds());
        try {
            for (Receipt current : receiptsOf(remittance)) {
                if (!wanted.remove(current.getId())) {
                    current.releaseFromRemittance();
                }
            }
            List<Receipt> added = wanted.isEmpty() ? List.of() : requireReceipts(companyId, List.copyOf(wanted));
            if (added.stream().anyMatch(receipt -> !receipt.getCurrencyCode().equals(remittance.getCurrencyCode()))) {
                throw new BusinessRuleException("Todos los recibos de una remesa deben estar en la misma moneda.");
            }
            assign(remittance, added);
            receipts.flush();
            List<Receipt> all = receiptsOf(remittance);
            remittance.updateDraft(request.bankAccount().trim(), normalize(request.notes()), total(all));
            return response(remittance, all, null);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<RemittanceResponse> search(RemittanceStatus status, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.search(companyId, status != null, status, pageable)
                .map(remittance -> response(remittance, receiptsOf(remittance), null));
    }

    @Transactional(readOnly = true)
    public RemittanceResponse findById(UUID id) {
        Remittance remittance = require(id);
        return response(remittance, receiptsOf(remittance), null);
    }

    /** Presenta la remesa al banco: sus recibos dejan de estar disponibles para cobrarse por otra vía. */
    @Transactional
    public RemittanceResponse send(UUID id, LocalDate sentDate) {
        Remittance remittance = require(id);
        List<Receipt> all = receiptsOf(remittance);
        try {
            remittance.send(sentDate);
            all.forEach(Receipt::markRemitted);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return response(remittance, all, null);
    }

    /** El banco abona la remesa: se dan por cobrados los recibos que no hayan venido devueltos. */
    @Transactional
    public RemittanceResponse settle(UUID id, LocalDate settlementDate) {
        Remittance remittance = require(id);
        List<Receipt> all = receiptsOf(remittance);
        List<Receipt> presented = all.stream().filter(receipt -> receipt.getStatus() == ReceiptStatus.REMITTED).toList();
        boolean hasReturns = all.stream().anyMatch(receipt -> receipt.getStatus() == ReceiptStatus.RETURNED);
        try {
            remittance.settle(settlementDate, hasReturns);
            for (Receipt receipt : presented) {
                receipt.settle(settlementDate);
                tracker.refreshDueDate(receipt);
            }
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        boolean invoiceUpdated = tracker.refreshInvoices(remittance.getCompanyId(), presented);
        return response(remittance, all, invoiceUpdated);
    }

    /** Anula la remesa y devuelve sus recibos a pendientes de cobro. */
    @Transactional
    public RemittanceResponse cancel(UUID id) {
        Remittance remittance = require(id);
        List<Receipt> all = receiptsOf(remittance);
        try {
            remittance.cancel();
            all.stream()
                    .filter(receipt -> receipt.getStatus() == ReceiptStatus.PENDING
                            || receipt.getStatus() == ReceiptStatus.REMITTED)
                    .forEach(Receipt::releaseFromRemittance);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return response(remittance, List.of(), null);
    }

    private void assign(Remittance remittance, List<Receipt> selected) {
        try {
            selected.forEach(receipt -> receipt.assignTo(remittance.getId()));
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
    }

    private List<Receipt> requireReceipts(UUID companyId, List<UUID> ids) {
        Set<UUID> distinct = new LinkedHashSet<>(ids);
        List<Receipt> found = receipts.findAllByCompanyIdAndIdIn(companyId, distinct);
        if (found.size() != distinct.size()) {
            throw new BusinessRuleException("Alguno de los recibos indicados no existe.");
        }
        return found;
    }

    private String singleCurrency(List<Receipt> selected) {
        Set<String> currencies = new LinkedHashSet<>();
        selected.forEach(receipt -> currencies.add(receipt.getCurrencyCode()));
        if (currencies.size() != 1) {
            throw new BusinessRuleException("Todos los recibos de una remesa deben estar en la misma moneda.");
        }
        return currencies.iterator().next();
    }

    private static BigDecimal total(List<Receipt> selected) {
        return selected.stream().map(Receipt::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Receipt> receiptsOf(Remittance remittance) {
        return receipts.findAllByCompanyIdAndRemittanceIdOrderByDueDateAscReceiptNumberAsc(
                remittance.getCompanyId(), remittance.getId());
    }

    private Remittance require(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Remesa", id));
    }

    private RemittanceResponse response(Remittance remittance, List<Receipt> remittanceReceipts,
                                        Boolean invoiceUpdated) {
        return new RemittanceResponse(remittance.getId(), remittance.getRemittanceNumber(),
                remittance.getBankAccount(), remittance.getCurrencyCode(), remittance.getCreationDate(),
                remittance.getSentDate(), remittance.getSettlementDate(), remittance.getStatus(),
                remittance.getTotalAmount(), remittance.getNotes(),
                remittanceReceipts.stream().map(ReceiptResponse::from).toList(), invoiceUpdated);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
