package com.peraerp.operations.logistics.storage;

public class ShipmentDocumentStorageException extends RuntimeException {

    public ShipmentDocumentStorageException(String message) {
        super(message);
    }

    public ShipmentDocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
