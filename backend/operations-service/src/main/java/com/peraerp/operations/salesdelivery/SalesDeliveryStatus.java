package com.peraerp.operations.salesdelivery;

public enum SalesDeliveryStatus {
    /** Aún no se ha podido dar la salida: falta almacén o existencias. */
    PENDING,
    /** Salida anotada en el diario de almacén. */
    POSTED,
    /** Ninguno de sus productos se controla en almacén. */
    NOT_APPLICABLE,
    /** La venta se anuló y la mercancía volvió al almacén. */
    REVERSED,
    /** Descartada a mano o por anulación de la venta antes de salir. */
    DISMISSED
}
