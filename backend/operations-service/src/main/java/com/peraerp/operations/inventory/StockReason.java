package com.peraerp.operations.inventory;

/** Motivo de una entrada o salida registrada desde el almacén, para poder analizar mermas. */
public enum StockReason {
    RECEIPT(true, false),
    CUSTOMER_RETURN(true, false),
    COUNT(true, true),
    DAMAGED(false, true),
    EXPIRED(false, true),
    SPOILED(false, true),
    LOST(false, true),
    INTERNAL_USE(false, true),
    OTHER(true, true);

    private final boolean allowedIn;
    private final boolean allowedOut;

    StockReason(boolean allowedIn, boolean allowedOut) {
        this.allowedIn = allowedIn;
        this.allowedOut = allowedOut;
    }

    public boolean allows(StockMovementType type) {
        return type.isInbound() ? allowedIn : allowedOut;
    }
}
