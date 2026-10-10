package com.peraerp.finance.pos;

public enum PosPaymentMethod {
    CASH, CARD,
    /** Parte con tarjeta y el resto en efectivo. */
    MIXED
}
