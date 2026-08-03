package com.peraerp.finance.receivable;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.payment.*;
import com.peraerp.platform.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class DueDateService{
    private final DocumentDueDateRepository repository; private final PaymentMethodRepository paymentMethods;
    private final PaymentScheduleCalculator calculator; private final CurrentCompanyProvider companyProvider;
    public DueDateService(DocumentDueDateRepository repository,PaymentMethodRepository paymentMethods,PaymentScheduleCalculator calculator,CurrentCompanyProvider companyProvider){this.repository=repository;this.paymentMethods=paymentMethods;this.calculator=calculator;this.companyProvider=companyProvider;}
    @Transactional public List<DueDateResponse> generate(GenerateDueDatesRequest request){
        UUID companyId=companyProvider.requireCompanyId();
        if(repository.existsByCompanyIdAndDocumentId(companyId,request.documentId())) throw new BusinessRuleException("El documento ya tiene vencimientos generados.");
        PaymentMethod method=paymentMethods.findByIdAndCompanyId(request.paymentMethodId(),companyId).orElseThrow(()->new ResourceNotFoundException("Forma de pago",request.paymentMethodId()));
        List<DocumentDueDate> dates=calculator.calculate(request.totalAmount(),request.issueDate(),method.getRules()).stream()
                .map(item->new DocumentDueDate(companyId,request.documentId(),item.installment(),item.dueDate(),item.amount())).toList();
        return repository.saveAll(dates).stream().map(DueDateResponse::from).toList();
    }
    @Transactional(readOnly=true) public List<DueDateResponse> findByDocument(UUID documentId){return repository.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumber(companyProvider.requireCompanyId(),documentId).stream().map(DueDateResponse::from).toList();}
}
