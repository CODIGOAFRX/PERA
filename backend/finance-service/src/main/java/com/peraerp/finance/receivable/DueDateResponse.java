package com.peraerp.finance.receivable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
public record DueDateResponse(UUID id,UUID documentId,int installment,LocalDate dueDate,BigDecimal amount,BigDecimal paidAmount,DueDateStatus status){
    static DueDateResponse from(DocumentDueDate due){return new DueDateResponse(due.getId(),due.getDocumentId(),due.getInstallmentNumber(),due.getDueDate(),due.getAmount(),due.getPaidAmount(),due.getStatus());}
}
