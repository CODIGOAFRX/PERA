package com.peraerp.finance.receivable;

import java.util.UUID;

/** Traslada a Ventas el estado de cobro de una factura para que el riesgo del cliente lo refleje. */
public interface InvoicePaymentNotifier {

    /** @return falso si Ventas no pudo actualizarse; la cartera no depende de ello. */
    boolean notify(UUID documentId, InvoicePaymentStatus status);
}
