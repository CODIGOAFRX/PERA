package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_specific_prices", uniqueConstraints = @UniqueConstraint(name = "uk_customer_product_price", columnNames = {"company_id", "customer_id", "product_id", "valid_from"}))
public class CustomerSpecificPrice extends CompanyScopedEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    @Column(name = "discount_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal discountPercentage = BigDecimal.ZERO;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(nullable = false)
    private boolean active = true;

    protected CustomerSpecificPrice() {}
}
