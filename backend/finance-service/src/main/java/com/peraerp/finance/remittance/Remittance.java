package com.peraerp.finance.remittance;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Conjunto de recibos que se presentan juntos al banco para su cobro. */
@Entity
@Table(name = "remittances", uniqueConstraints = @UniqueConstraint(
        name = "uk_remittance_number", columnNames = {"company_id", "remittance_number"}))
public class Remittance extends CompanyScopedEntity {

    @Column(name = "remittance_number", nullable = false, length = 50, updatable = false)
    private String remittanceNumber;
    @Column(name = "bank_account", nullable = false, length = 80)
    private String bankAccount;
    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;
    @Column(name = "creation_date", nullable = false, updatable = false)
    private LocalDate creationDate;
    @Column(name = "sent_date")
    private LocalDate sentDate;
    @Column(name = "settlement_date")
    private LocalDate settlementDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RemittanceStatus status = RemittanceStatus.DRAFT;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(length = 500)
    private String notes;

    protected Remittance() {
    }

    public Remittance(UUID companyId, String remittanceNumber, String bankAccount, String currencyCode,
                      LocalDate creationDate, String notes) {
        super(companyId);
        this.remittanceNumber = remittanceNumber;
        this.bankAccount = bankAccount;
        this.currencyCode = currencyCode;
        this.creationDate = creationDate;
        this.notes = notes;
    }

    public void updateDraft(String bankAccount, String notes, BigDecimal totalAmount) {
        requireStatus(RemittanceStatus.DRAFT, "Solo se puede modificar una remesa en borrador.");
        this.bankAccount = bankAccount;
        this.notes = notes;
        this.totalAmount = totalAmount;
    }

    public void send(LocalDate sentDate) {
        requireStatus(RemittanceStatus.DRAFT, "Solo se puede enviar al banco una remesa en borrador.");
        if (totalAmount.signum() <= 0) {
            throw new IllegalStateException("La remesa no tiene recibos.");
        }
        if (sentDate.isBefore(creationDate)) {
            throw new IllegalStateException("La fecha de envío no puede ser anterior a la de creación.");
        }
        this.status = RemittanceStatus.SENT;
        this.sentDate = sentDate;
    }

    /** El banco abona la remesa. {@code hasReturns} indica que algún recibo vino devuelto antes del abono. */
    public void settle(LocalDate settlementDate, boolean hasReturns) {
        requireStatus(RemittanceStatus.SENT, "Solo se puede liquidar una remesa enviada al banco.");
        if (settlementDate.isBefore(sentDate)) {
            throw new IllegalStateException("La fecha de abono no puede ser anterior a la de envío.");
        }
        this.status = hasReturns ? RemittanceStatus.PARTIALLY_RETURNED : RemittanceStatus.SETTLED;
        this.settlementDate = settlementDate;
    }

    /** El banco devuelve uno de sus recibos después del abono. Antes del abono la remesa sigue enviada. */
    public void registerReturn() {
        if (status == RemittanceStatus.SETTLED) {
            this.status = RemittanceStatus.PARTIALLY_RETURNED;
        }
    }

    public void cancel() {
        if (status != RemittanceStatus.DRAFT && status != RemittanceStatus.SENT) {
            throw new IllegalStateException("Solo se puede anular una remesa en borrador o enviada sin liquidar.");
        }
        this.status = RemittanceStatus.CANCELLED;
        this.totalAmount = BigDecimal.ZERO;
    }

    private void requireStatus(RemittanceStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    public String getRemittanceNumber() { return remittanceNumber; }
    public String getBankAccount() { return bankAccount; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getCreationDate() { return creationDate; }
    public LocalDate getSentDate() { return sentDate; }
    public LocalDate getSettlementDate() { return settlementDate; }
    public RemittanceStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getNotes() { return notes; }
}
