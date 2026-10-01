package com.peraerp.operations.inventory;

public enum StockMovementType {
    PURCHASE_RECEIPT(true),
    PURCHASE_REVERSAL(false),
    ADJUSTMENT_IN(true),
    ADJUSTMENT_OUT(false),
    TRANSFER_IN(true),
    TRANSFER_OUT(false);

    private final boolean inbound;

    StockMovementType(boolean inbound) {
        this.inbound = inbound;
    }

    public boolean isInbound() {
        return inbound;
    }

    /** Los ajustes son los únicos movimientos que un usuario registra a mano. */
    public boolean isManualAdjustment() {
        return this == ADJUSTMENT_IN || this == ADJUSTMENT_OUT;
    }
}
