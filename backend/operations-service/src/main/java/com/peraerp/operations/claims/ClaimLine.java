package com.peraerp.operations.claims;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

/** Producto o servicio afectado por la reclamación, con la cantidad reclamada. */
@Entity
@Table(name = "claim_lines", uniqueConstraints = @UniqueConstraint(
        name = "uk_claim_line_sequence", columnNames = {"company_id", "claim_id", "line_sequence"}))
public class ClaimLine extends CompanyScopedEntity {

    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;
    @Column(name = "line_sequence", nullable = false)
    private int lineSequence;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "product_code_snapshot", length = 100)
    private String productCodeSnapshot;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    protected ClaimLine() {
    }

    public ClaimLine(UUID companyId, UUID claimId, int lineSequence, UUID productId, String productCodeSnapshot,
                     String description, BigDecimal quantity) {
        super(companyId);
        this.claimId = claimId;
        this.lineSequence = lineSequence;
        this.productId = productId;
        this.productCodeSnapshot = productCodeSnapshot;
        this.description = description;
        this.quantity = quantity;
    }

    public UUID getClaimId() { return claimId; }
    public int getLineSequence() { return lineSequence; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
}
