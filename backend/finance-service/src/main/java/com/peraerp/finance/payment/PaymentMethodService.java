package com.peraerp.finance.payment;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service
public class PaymentMethodService {
    private final PaymentMethodRepository repository; private final CurrentCompanyProvider companyProvider;
    public PaymentMethodService(PaymentMethodRepository repository,CurrentCompanyProvider companyProvider){this.repository=repository;this.companyProvider=companyProvider;}
    @Transactional public PaymentMethodResponse create(PaymentMethodRequest request){
        UUID companyId=companyProvider.requireCompanyId();
        if(repository.existsByCompanyIdAndCodeIgnoreCase(companyId,request.code())) throw new BusinessRuleException("Ya existe una forma de pago con ese código.");
        BigDecimal total=request.rules().stream().map(PaymentRuleRequest::percentage).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo(new BigDecimal("100"))!=0) throw new BusinessRuleException("Los porcentajes de vencimiento deben sumar 100.");
        PaymentMethod method=new PaymentMethod(companyId,request.code().trim().toUpperCase(),request.name().trim());
        for(int i=0;i<request.rules().size();i++){PaymentRuleRequest rule=request.rules().get(i);method.addRule(i+1,rule.dueDays(),rule.percentage());}
        return PaymentMethodResponse.from(repository.save(method));
    }
    @Transactional(readOnly=true) public List<PaymentMethodResponse> findAll(){return repository.findAllByCompanyIdOrderByName(companyProvider.requireCompanyId()).stream().map(PaymentMethodResponse::from).toList();}
}
