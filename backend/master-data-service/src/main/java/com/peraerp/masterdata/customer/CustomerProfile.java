package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "customer_profiles", uniqueConstraints = @UniqueConstraint(name = "uk_customer_party", columnNames = "party_id"))
public class CustomerProfile extends CompanyScopedEntity {

    @Column(name = "party_id", nullable = false)
    private UUID partyId;
    @Column(name = "price_list_id")
    private UUID priceListId;
    @Column(name = "default_payment_method_id")
    private UUID defaultPaymentMethodId;
    @Column(name = "supplier_code", length = 60)
    private String supplierCode;
    /**
     * @deprecated Parámetro de cálculo heredado sin semántica validada. No forma parte del motor horizontal de
     * precios; se conserva únicamente para compatibilidad con datos y clientes de API existentes.
     */
    @Deprecated(since = "0.2", forRemoval = false)
    @Column(name = "calculation_multiplier", nullable = false, precision = 15, scale = 6)
    private BigDecimal calculationMultiplier = BigDecimal.ONE;
    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit = BigDecimal.ZERO;
    @Column(name = "risk_warning_threshold", nullable = false, precision = 19, scale = 4)
    private BigDecimal riskWarningThreshold = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_policy", nullable = false, length = 30)
    private RiskPolicy riskPolicy = RiskPolicy.WARN;

    protected CustomerProfile() {}

    public CustomerProfile(UUID companyId, UUID partyId, UUID priceListId, UUID defaultPaymentMethodId,
                           String supplierCode, BigDecimal calculationMultiplier, BigDecimal creditLimit,
                           BigDecimal riskWarningThreshold, RiskPolicy riskPolicy) {
        super(companyId);
        this.partyId = partyId;
        this.priceListId = priceListId;
        this.defaultPaymentMethodId = defaultPaymentMethodId;
        this.supplierCode = supplierCode;
        this.calculationMultiplier = calculationMultiplier == null ? BigDecimal.ONE : calculationMultiplier;
        this.creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
        this.riskWarningThreshold = riskWarningThreshold == null ? BigDecimal.ZERO : riskWarningThreshold;
        this.riskPolicy = riskPolicy == null ? RiskPolicy.WARN : riskPolicy;
    }

    public UUID getPartyId() { return partyId; }
    public UUID getPriceListId() { return priceListId; }
    public UUID getDefaultPaymentMethodId() { return defaultPaymentMethodId; }
    public String getSupplierCode() { return supplierCode; }
    /**
     * @deprecated Use listas de precios, precios específicos por cliente y reglas de ajuste con semántica explícita.
     */
    @Deprecated(since = "0.2", forRemoval = false)
    public BigDecimal getCalculationMultiplier() { return calculationMultiplier; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public BigDecimal getRiskWarningThreshold() { return riskWarningThreshold; }
    public RiskPolicy getRiskPolicy() { return riskPolicy; }
}
