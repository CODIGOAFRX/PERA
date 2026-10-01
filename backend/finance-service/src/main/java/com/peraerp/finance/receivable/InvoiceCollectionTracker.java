package com.peraerp.finance.receivable;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Mantiene coherentes el vencimiento y la factura con lo que le ocurre a sus recibos. Lo usan el
 * cobro directo y la liquidación de remesas, para que ambos caminos hagan exactamente lo mismo.
 */
@Component
public class InvoiceCollectionTracker {

    private final DocumentDueDateRepository dueDates;
    private final ReceiptRepository receipts;
    private final InvoicePaymentNotifier notifier;

    public InvoiceCollectionTracker(DocumentDueDateRepository dueDates, ReceiptRepository receipts,
                                    InvoicePaymentNotifier notifier) {
        this.dueDates = dueDates;
        this.receipts = receipts;
        this.notifier = notifier;
    }

    /** Pone el vencimiento del recibo como pagado o pendiente según el estado actual del recibo. */
    public void refreshDueDate(Receipt receipt) {
        if (receipt.getDueDateId() == null) {
            return;
        }
        dueDates.findByIdAndCompanyId(receipt.getDueDateId(), receipt.getCompanyId()).ifPresent(dueDate -> {
            if (receipt.getStatus() == ReceiptStatus.COLLECTED) {
                dueDate.settle();
            } else {
                dueDate.reopen();
            }
        });
    }

    /**
     * Recalcula el estado de cobro de las facturas afectadas y lo traslada a Ventas.
     *
     * @return falso si alguna factura no se pudo actualizar en Ventas.
     */
    public boolean refreshInvoices(UUID companyId, Collection<Receipt> changed) {
        Set<UUID> documentIds = new LinkedHashSet<>();
        changed.forEach(receipt -> documentIds.add(receipt.getDocumentId()));
        boolean allUpdated = true;
        for (UUID documentId : documentIds) {
            InvoicePaymentStatus status = InvoicePaymentStatus.of(
                    receipts.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumberAsc(companyId, documentId));
            allUpdated &= notifier.notify(documentId, status);
        }
        return allUpdated;
    }
}
