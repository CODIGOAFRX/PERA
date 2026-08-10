package com.peraerp.sales.numbering;

import com.peraerp.sales.document.DocumentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NumberingSchemeRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 160) String name,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String series,
        @NotBlank @Size(max = 120) String pattern,
        @NotNull NumberingResetPeriod resetPeriod,
        @Min(1) long initialValue,
        boolean active,
        boolean defaultScheme
) {
}
