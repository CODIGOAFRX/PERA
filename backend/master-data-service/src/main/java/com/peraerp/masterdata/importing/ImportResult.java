package com.peraerp.masterdata.importing;

import java.util.List;

public record ImportResult(int imported, int blankRows, int failed, List<ImportRowError> errors) {
    public record ImportRowError(int row, String message) {}
}
