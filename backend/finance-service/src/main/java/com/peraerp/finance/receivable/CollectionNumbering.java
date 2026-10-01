package com.peraerp.finance.receivable;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Numeración correlativa por empresa y año: REC-2026-000001, REM-2026-0001. */
@Component
public class CollectionNumbering {

    private final CollectionSequenceRepository sequences;

    public CollectionNumbering(CollectionSequenceRepository sequences) {
        this.sequences = sequences;
    }

    public String nextReceiptNumber(UUID companyId, int year) {
        return "REC-%d-%06d".formatted(year, next(companyId, CollectionSequence.RECEIPT, year));
    }

    public String nextRemittanceNumber(UUID companyId, int year) {
        return "REM-%d-%04d".formatted(year, next(companyId, CollectionSequence.REMITTANCE, year));
    }

    private long next(UUID companyId, String name, int year) {
        return sequences.findForUpdate(companyId, name, year)
                .orElseGet(() -> sequences.save(new CollectionSequence(companyId, name, year)))
                .next();
    }
}
