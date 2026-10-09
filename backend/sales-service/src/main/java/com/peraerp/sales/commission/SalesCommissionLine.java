package com.peraerp.sales.commission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Comisión de una línea de la factura y de dónde sale su porcentaje. */
@Entity
@Table(name = "sales_commission_lines")
public class SalesCommissionLine {
    /** De dónde sale el porcentaje: una regla, la comisión por defecto del comercial o ninguno. */
    public enum Origin { RULE, DEFAULT, NONE }

    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commission_id", nullable = false)
    private SalesCommission commission;
    @Column(name = "line_order", nullable = false)
    private int lineOrder;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmount;
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;
    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionAmount;
    @Column(name = "rule_id")
    private UUID ruleId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origin origin;

    protected SalesCommissionLine() {}

    public SalesCommissionLine(int lineOrder, String description, BigDecimal baseAmount, BigDecimal percentage,
                               BigDecimal commissionAmount, UUID ruleId, Origin origin) {
        this.id = UUID.randomUUID();
        this.lineOrder = lineOrder;
        this.description = description.length() > 500 ? description.substring(0, 500) : description;
        this.baseAmount = baseAmount;
        this.percentage = percentage;
        this.commissionAmount = commissionAmount;
        this.ruleId = ruleId;
        this.origin = origin;
    }

    void attachTo(SalesCommission commission) { this.commission = commission; }

    public int getLineOrder() { return lineOrder; }
    public String getDescription() { return description; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getPercentage() { return percentage; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public UUID getRuleId() { return ruleId; }
    public Origin getOrigin() { return origin; }
}
