package com.peraerp.finance.receivable;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="document_due_dates",uniqueConstraints=@UniqueConstraint(name="uk_document_installment",columnNames={"company_id","document_id","installment_number"}))
public class DocumentDueDate extends CompanyScopedEntity{
    @Column(name="document_id",nullable=false) private UUID documentId;
    @Column(name="installment_number",nullable=false) private int installmentNumber;
    @Column(name="due_date",nullable=false) private LocalDate dueDate;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="paid_amount",nullable=false,precision=19,scale=4) private BigDecimal paidAmount=BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private DueDateStatus status=DueDateStatus.PENDING;
    protected DocumentDueDate(){}
    public DocumentDueDate(UUID companyId,UUID documentId,int installmentNumber,LocalDate dueDate,BigDecimal amount){super(companyId);this.documentId=documentId;this.installmentNumber=installmentNumber;this.dueDate=dueDate;this.amount=amount;}
    public UUID getDocumentId(){return documentId;} public int getInstallmentNumber(){return installmentNumber;} public LocalDate getDueDate(){return dueDate;}
    public BigDecimal getAmount(){return amount;} public BigDecimal getPaidAmount(){return paidAmount;} public DueDateStatus getStatus(){return status;}
}
