package com.peraerp.operations.inventory;

public enum StockSourceType {
    MANUAL, TRANSFER, PURCHASE_DOCUMENT, SALES_DOCUMENT,
    /** Ticket o devolución del TPV (finance-service). */
    POS_TICKET,
    /** Entrada o retirada registrada desde la aplicación de almacén con escáner. */
    SCAN
}
