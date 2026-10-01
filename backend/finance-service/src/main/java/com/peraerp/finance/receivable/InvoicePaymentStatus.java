package com.peraerp.finance.receivable;

import java.util.List;

/** Estado de cobro de una factura, con los mismos valores que usa Ventas. */
public enum InvoicePaymentStatus {
    PENDING, PARTIALLY_PAID, PAID;

    /** Se deduce de los recibos en vigor de la factura. */
    public static InvoicePaymentStatus of(List<Receipt> receipts) {
        List<Receipt> live = receipts.stream().filter(receipt -> receipt.getStatus() != ReceiptStatus.CANCELLED).toList();
        long collected = live.stream().filter(receipt -> receipt.getStatus() == ReceiptStatus.COLLECTED).count();
        if (collected == 0) {
            return PENDING;
        }
        return collected == live.size() ? PAID : PARTIALLY_PAID;
    }
}
