package com.peraerp.sales.masterdata;

import java.math.BigDecimal;
import java.util.UUID;

/** Comercial tal como lo da maestros: ventas guarda su nombre en el documento y usa su comisión por defecto. */
public record SalespersonSnapshot(UUID id, String code, String name, BigDecimal commissionPercentage, boolean active) {
}
