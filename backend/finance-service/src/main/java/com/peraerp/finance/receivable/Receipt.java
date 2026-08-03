package com.peraerp.finance.receivable;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="receipts",uniqueConstraints=@UniqueConstraint(name="uk_receipt_number",columnNames={"company_id","receipt_number"}))
public class Receipt extends CompanyScopedEntity{
    @Column(name="receipt_number",nullable=false,length=50) private String receiptNumber;
    @Column(name="customer_id",nullable=false) private UUID customerId;
    @Column(name="document_id",nullable=false) private UUID documentId;
    @Column(name="due_date_id") private UUID dueDateId;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="due_date",nullable=false) private LocalDate dueDate;
    @Column(name="collection_date") private LocalDate collectionDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ReceiptStatus status=ReceiptStatus.PENDING;
    @Column(name="bank_account",length=80) private String bankAccount;
    @Column(columnDefinition="text") private String notes;
    protected Receipt(){}
}
