package com.peraerp.operations.purchasing;

/**
 * Tipos documentales del ciclo de compra: pedido a proveedor, albarán de entrada y factura recibida.
 */
public enum PurchaseDocumentType {
    PURCHASE_ORDER("PC"),
    GOODS_RECEIPT("AC"),
    SUPPLIER_INVOICE("FC");

    private final String numberPrefix;

    PurchaseDocumentType(String numberPrefix) {
        this.numberPrefix = numberPrefix;
    }

    public String numberPrefix() {
        return numberPrefix;
    }

    /** Un pedido puede recibirse o facturarse directamente; un albarán solo puede facturarse. */
    public boolean canConvertTo(PurchaseDocumentType target) {
        return switch (this) {
            case PURCHASE_ORDER -> target == GOODS_RECEIPT || target == SUPPLIER_INVOICE;
            case GOODS_RECEIPT -> target == SUPPLIER_INVOICE;
            case SUPPLIER_INVOICE -> false;
        };
    }
}
