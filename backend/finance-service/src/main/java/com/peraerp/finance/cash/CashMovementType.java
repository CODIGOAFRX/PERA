package com.peraerp.finance.cash;

public enum CashMovementType {
    OPENING, SALE_COLLECTION, INCOME, EXPENSE, WITHDRAWAL, CLOSING_ADJUSTMENT,
    /** Efectivo cobrado por un ticket del TPV (ya descontado el cambio devuelto). */
    POS_SALE,
    /** Efectivo devuelto por una devolución o por la anulación de un ticket del TPV. */
    POS_REFUND;

    public boolean isInflow() {
        return this == OPENING || this == SALE_COLLECTION || this == INCOME || this == POS_SALE;
    }

    /** Apuntes que un usuario registra a mano en el diario de caja. */
    public boolean isManual() {
        return this == INCOME || this == EXPENSE || this == WITHDRAWAL;
    }
}
