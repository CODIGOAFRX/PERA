package com.peraerp.finance.pos;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ticket del TPV: una venta o la devolución de parte de una venta anterior.
 *
 * <p>Los importes se guardan siempre en positivo; {@link PosTicketKind} dice si suman o restan.
 * Un ticket no se edita: se corrige anulándolo (mientras su sesión sigue abierta) o con una
 * devolución.</p>
 */
@Entity
@Table(name = "pos_tickets")
public class PosTicket extends CompanyScopedEntity {

    @Column(name = "cash_register_id", nullable = false, updatable = false)
    private UUID cashRegisterId;
    @Column(name = "cash_session_id", nullable = false, updatable = false)
    private UUID cashSessionId;
    @Column(name = "ticket_number", nullable = false, length = 60, updatable = false)
    private String ticketNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PosTicketKind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosTicketStatus status = PosTicketStatus.COMPLETED;
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;
    @Column(name = "cashier_id", nullable = false, updatable = false)
    private UUID cashierId;
    @Column(name = "cashier_name", nullable = false, length = 160, updatable = false)
    private String cashierName;
    @Column(name = "customer_name", length = 180, updatable = false)
    private String customerName;
    @Column(name = "customer_tax_id", length = 30, updatable = false)
    private String customerTaxId;
    @Column(name = "original_ticket_id", updatable = false)
    private UUID originalTicketId;
    @Column(name = "original_ticket_number", length = 60, updatable = false)
    private String originalTicketNumber;
    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode = "EUR";
    @Column(name = "discount_percent", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal discountPercent;
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal grossAmount;
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal discountAmount;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal totalAmount;
    @Column(name = "tax_base_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal taxBaseAmount;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal taxAmount;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PosPaymentMethod paymentMethod;
    @Column(name = "cash_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal cashAmount;
    @Column(name = "card_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal cardAmount;
    @Column(name = "cash_tendered", precision = 19, scale = 4, updatable = false)
    private BigDecimal cashTendered;
    @Column(name = "change_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal changeAmount;
    @Column(length = 300, updatable = false)
    private String note;
    @Column(name = "client_reference", updatable = false)
    private UUID clientReference;
    @Column(name = "voided_at")
    private Instant voidedAt;
    @Column(name = "voided_by")
    private UUID voidedBy;
    @Column(name = "void_reason", length = 300)
    private String voidReason;

    protected PosTicket() {
    }

    public PosTicket(UUID companyId, UUID cashRegisterId, UUID cashSessionId, String ticketNumber, PosTicketKind kind,
                     Instant issuedAt, UUID cashierId, String cashierName, String customerName, String customerTaxId,
                     PosTicket original, BigDecimal discountPercent, PosCalculator.Totals totals,
                     PosCalculator.Payment payment, String note, UUID clientReference) {
        super(companyId);
        this.cashRegisterId = cashRegisterId;
        this.cashSessionId = cashSessionId;
        this.ticketNumber = ticketNumber;
        this.kind = kind;
        this.issuedAt = issuedAt;
        this.cashierId = cashierId;
        this.cashierName = cashierName;
        this.customerName = customerName;
        this.customerTaxId = customerTaxId;
        if (original != null) {
            this.originalTicketId = original.getId();
            this.originalTicketNumber = original.getTicketNumber();
        }
        this.discountPercent = discountPercent;
        this.grossAmount = totals.grossAmount();
        this.discountAmount = totals.discountAmount();
        this.totalAmount = totals.totalAmount();
        this.taxBaseAmount = totals.taxBaseAmount();
        this.taxAmount = totals.taxAmount();
        this.paymentMethod = payment.method();
        this.cashAmount = payment.cashAmount();
        this.cardAmount = payment.cardAmount();
        this.cashTendered = payment.cashTendered();
        this.changeAmount = payment.changeAmount();
        this.note = note;
        this.clientReference = clientReference;
    }

    public void voidTicket(UUID userId, Instant at, String reason) {
        if (status != PosTicketStatus.COMPLETED) {
            throw new IllegalStateException("El ticket " + ticketNumber + " ya está anulado.");
        }
        this.status = PosTicketStatus.VOIDED;
        this.voidedBy = userId;
        this.voidedAt = at;
        this.voidReason = reason;
    }

    public boolean isSale() {
        return kind == PosTicketKind.SALE;
    }

    public UUID getCashRegisterId() { return cashRegisterId; }
    public UUID getCashSessionId() { return cashSessionId; }
    public String getTicketNumber() { return ticketNumber; }
    public PosTicketKind getKind() { return kind; }
    public PosTicketStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public UUID getCashierId() { return cashierId; }
    public String getCashierName() { return cashierName; }
    public String getCustomerName() { return customerName; }
    public String getCustomerTaxId() { return customerTaxId; }
    public UUID getOriginalTicketId() { return originalTicketId; }
    public String getOriginalTicketNumber() { return originalTicketNumber; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getTaxBaseAmount() { return taxBaseAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public PosPaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getCashAmount() { return cashAmount; }
    public BigDecimal getCardAmount() { return cardAmount; }
    public BigDecimal getCashTendered() { return cashTendered; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public String getNote() { return note; }
    public UUID getClientReference() { return clientReference; }
    public Instant getVoidedAt() { return voidedAt; }
    public UUID getVoidedBy() { return voidedBy; }
    public String getVoidReason() { return voidReason; }
}
