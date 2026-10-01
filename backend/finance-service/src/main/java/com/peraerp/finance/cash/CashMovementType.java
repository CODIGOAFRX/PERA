package com.peraerp.finance.cash;

public enum CashMovementType {
    OPENING, SALE_COLLECTION, INCOME, EXPENSE, WITHDRAWAL, CLOSING_ADJUSTMENT;

    public boolean isInflow() {
        return this == OPENING || this == SALE_COLLECTION || this == INCOME;
    }

    /** Apuntes que un usuario registra a mano en el diario de caja. */
    public boolean isManual() {
        return this == INCOME || this == EXPENSE || this == WITHDRAWAL;
    }
}
