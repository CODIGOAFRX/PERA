package com.peraerp.sales.verifactu.mapping;

import com.peraerp.sales.verifactu.domain.ExemptionCause;
import com.peraerp.sales.verifactu.domain.OperationQualification;

import java.math.BigDecimal;

/**
 * Una línea del bloque {@code Desglose} del registro de facturación.
 *
 * <p>No se corresponde con una línea de la factura sino con una combinación fiscal: todas las
 * líneas que comparten régimen, calificación, causa de exención y tipo impositivo se suman en una
 * sola. Una factura de cuarenta líneas al 21 % produce un único desglose.</p>
 *
 * @param regimeKey      ClaveRegimen
 * @param qualification  CalificacionOperacion; {@code EXEMPT} indica que la causa viaja aparte
 * @param exemptionCause OperacionExenta, solo en las exentas
 * @param taxRate        TipoImpositivo
 * @param taxableBase    BaseImponible
 * @param taxAmount      CuotaRepercutida
 */
public record TaxBreakdownEntry(
        String regimeKey,
        OperationQualification qualification,
        ExemptionCause exemptionCause,
        BigDecimal taxRate,
        BigDecimal taxableBase,
        BigDecimal taxAmount) {
}
