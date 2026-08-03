package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_list_items", uniqueConstraints = @UniqueConstraint(name = "uk_price_list_product_date", columnNames = {"company_id", "price_list_id", "product_id", "valid_from"}))
public class PriceListItem extends CompanyScopedEntity {
    @Column(name = "price_list_id", nullable = false)
    private UUID priceListId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    protected PriceListItem() {}
}
