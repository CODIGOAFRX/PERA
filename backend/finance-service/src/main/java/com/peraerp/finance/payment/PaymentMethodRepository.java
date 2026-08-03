package com.peraerp.finance.payment;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod,UUID>{
    Optional<PaymentMethod> findByIdAndCompanyId(UUID id,UUID companyId);
    List<PaymentMethod> findAllByCompanyIdOrderByName(UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId,String code);
}
