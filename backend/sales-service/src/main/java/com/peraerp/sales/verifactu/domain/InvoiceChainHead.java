package com.peraerp.sales.verifactu.domain;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Puntero de la cadena de registros de facturación de una empresa.
 *
 * <p>Una fila por empresa. Es la fila que se bloquea al encadenar: la huella de cada registro
 * incorpora la del anterior, así que dos facturas simultáneas de la misma empresa no pueden leer
 * el mismo valor. El bloqueo serializa las emisiones de <em>una</em> empresa, no las de todas.</p>
 */
@Entity
@Table(name = "verifactu_chain_head",
        uniqueConstraints = @UniqueConstraint(name = "uk_verifactu_chain_company", columnNames = "company_id"))
public class InvoiceChainHead extends CompanyScopedEntity {

    @Column(name = "last_record_id")
    private UUID lastRecordId;
    @Column(name = "last_fingerprint", length = 64)
    private String lastFingerprint;
    @Column(name = "last_generated_at")
    private java.time.Instant lastGeneratedAt;
    @Column(name = "next_sequence", nullable = false)
    private long nextSequence = 1L;

    protected InvoiceChainHead() {}

    public InvoiceChainHead(UUID companyId) {
        super(companyId);
    }

    /** {@code true} mientras la empresa no haya generado ningún registro. */
    public boolean isEmpty() {
        return lastRecordId == null;
    }

    /**
     * Avanza la cadena tras encadenar un registro.
     *
     * <p>Rechaza un registro con fecha de generación anterior a la del último encadenado. La AEAT
     * espera que {@code FechaHoraHusoGenRegistro} no decrezca a lo largo de la cadena, y una
     * cadena con el tiempo hacia atrás es un síntoma casi seguro de reloj mal configurado o de
     * fechas construidas a mano.</p>
     */
    public void advance(UUID recordId, String fingerprint, ZonedDateTime generatedAt) {
        if (recordId == null || fingerprint == null || generatedAt == null) {
            throw new IllegalArgumentException("Un avance de cadena exige registro, huella y fecha de generación.");
        }
        java.time.Instant instant = generatedAt.toInstant();
        if (lastGeneratedAt != null && instant.isBefore(lastGeneratedAt)) {
            throw new BusinessRuleException(
                    "La fecha de generación del registro es anterior a la del registro previo de la cadena. "
                            + "Revisa la hora del sistema antes de continuar.");
        }
        this.lastRecordId = recordId;
        this.lastFingerprint = fingerprint;
        this.lastGeneratedAt = instant;
        this.nextSequence = this.nextSequence + 1;
    }

    public UUID getLastRecordId() { return lastRecordId; }
    public String getLastFingerprint() { return lastFingerprint; }
    public java.time.Instant getLastGeneratedAt() { return lastGeneratedAt; }
    public long getNextSequence() { return nextSequence; }
}
