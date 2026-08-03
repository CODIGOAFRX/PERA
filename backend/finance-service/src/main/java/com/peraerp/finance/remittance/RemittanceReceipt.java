package com.peraerp.finance.remittance;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="remittance_receipts",uniqueConstraints=@UniqueConstraint(name="uk_remittance_receipt",columnNames={"remittance_id","receipt_id"}))
public class RemittanceReceipt extends CompanyScopedEntity{
    @Column(name="remittance_id",nullable=false) private UUID remittanceId;
    @Column(name="receipt_id",nullable=false) private UUID receiptId;
    protected RemittanceReceipt(){}
}
