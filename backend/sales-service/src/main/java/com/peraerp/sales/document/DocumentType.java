package com.peraerp.sales.document;

/**
 * Tipos documentales del ciclo comercial.
 *
 * <p>{@link #RECTIFYING_INVOICE} existe como tipo propio, y no como una variante de
 * {@link #INVOICE}, porque la numeración de PERA es por (empresa, tipo) y la normativa exige que
 * las facturas rectificativas lleven una serie distinta de las ordinarias. La clasificación fiscal
 * fina (R1..R5) vive en {@code CommercialDocument.invoiceKind}.</p>
 */
public enum DocumentType {
    QUOTE, SALES_ORDER, DELIVERY_NOTE, INVOICE, RECTIFYING_INVOICE, WORK_ORDER;

    /** {@code true} para los tipos que expiden factura y, por tanto, generan registro de facturación. */
    public boolean isInvoice() {
        return this == INVOICE || this == RECTIFYING_INVOICE;
    }
}
