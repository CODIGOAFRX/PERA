package com.peraerp.finance.pos;

public enum PosTicketStatus {
    COMPLETED,
    /** Anulado en la misma sesión en que se cobró: su efectivo salió de la caja y su mercancía vuelve al almacén. */
    VOIDED
}
