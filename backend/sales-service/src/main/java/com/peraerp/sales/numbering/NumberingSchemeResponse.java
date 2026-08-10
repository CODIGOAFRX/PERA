package com.peraerp.sales.numbering;

import com.peraerp.sales.document.DocumentType;

import java.util.UUID;

public record NumberingSchemeResponse(
        UUID id,
        String code,
        String name,
        DocumentType documentType,
        String series,
        String pattern,
        NumberingResetPeriod resetPeriod,
        long initialValue,
        boolean active,
        boolean defaultScheme
) {
    static NumberingSchemeResponse from(NumberingScheme scheme) {
        return new NumberingSchemeResponse(scheme.getId(), scheme.getCode(), scheme.getName(),
                scheme.getDocumentType(), scheme.getSeries(), scheme.getPattern(), scheme.getResetPeriod(),
                scheme.getInitialValue(), scheme.isActive(), scheme.isDefaultScheme());
    }
}
