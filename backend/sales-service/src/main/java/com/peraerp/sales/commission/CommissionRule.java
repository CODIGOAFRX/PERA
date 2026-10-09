package com.peraerp.sales.commission;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Regla de comisión de un comercial, como las de «Comisiones vendedores» del programa anterior: un
 * porcentaje para las líneas de un artículo, de un grupo de artículos o de cualquiera, opcionalmente
 * limitado a un tramo de importe de la línea.
 */
@Entity
@Table(name = "commission_rules")
public class CommissionRule extends CompanyScopedEntity {
    @Column(name = "salesperson_id", nullable = false)
    private UUID salespersonId;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "product_label", length = 220)
    private String productLabel;
    @Column(name = "product_group_id")
    private UUID productGroupId;
    @Column(name = "product_group_label", length = 180)
    private String productGroupLabel;
    @Column(name = "amount_from", precision = 19, scale = 4)
    private BigDecimal amountFrom;
    @Column(name = "amount_to", precision = 19, scale = 4)
    private BigDecimal amountTo;
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;
    @Column(nullable = false)
    private boolean active = true;

    protected CommissionRule() {}

    public CommissionRule(UUID companyId, UUID salespersonId) {
        super(companyId);
        this.salespersonId = salespersonId;
    }

    public void update(UUID productId, String productLabel, UUID productGroupId, String productGroupLabel,
                       BigDecimal amountFrom, BigDecimal amountTo, BigDecimal percentage, boolean active) {
        this.productId = productId;
        this.productLabel = productLabel;
        this.productGroupId = productGroupId;
        this.productGroupLabel = productGroupLabel;
        this.amountFrom = amountFrom;
        this.amountTo = amountTo;
        this.percentage = percentage;
        this.active = active;
    }

    public UUID getSalespersonId() { return salespersonId; }
    public UUID getProductId() { return productId; }
    public String getProductLabel() { return productLabel; }
    public UUID getProductGroupId() { return productGroupId; }
    public String getProductGroupLabel() { return productGroupLabel; }
    public BigDecimal getAmountFrom() { return amountFrom; }
    public BigDecimal getAmountTo() { return amountTo; }
    public BigDecimal getPercentage() { return percentage; }
    public boolean isActive() { return active; }
}
