package com.peraerp.finance.receivable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DocumentDueDateRepository extends JpaRepository<DocumentDueDate,UUID>{
    boolean existsByCompanyIdAndDocumentId(UUID companyId,UUID documentId);
    List<DocumentDueDate> findAllByCompanyIdAndDocumentIdOrderByInstallmentNumber(UUID companyId,UUID documentId);
}
