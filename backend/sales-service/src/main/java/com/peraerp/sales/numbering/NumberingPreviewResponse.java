package com.peraerp.sales.numbering;

import java.time.LocalDate;
import java.util.UUID;

public record NumberingPreviewResponse(UUID schemeId, LocalDate date, long sequence, String value) {
}
