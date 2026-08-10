package com.peraerp.sales.masterdata;

import java.util.UUID;

public record CustomerSnapshot(UUID id, String code, String legalName, boolean active) {
}
