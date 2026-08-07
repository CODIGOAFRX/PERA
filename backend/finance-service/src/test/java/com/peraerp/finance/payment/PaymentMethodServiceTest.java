package com.peraerp.finance.payment;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {
    @Mock PaymentMethodRepository methods;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private PaymentMethodService service;

    @BeforeEach
    void setUp() {
        service = new PaymentMethodService(methods, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsOrderedScheduleWhenPercentagesSumExactlyOneHundred() {
        when(methods.existsByCompanyIdAndCodeIgnoreCase(companyId, "30-60")).thenReturn(false);
        when(methods.save(any(PaymentMethod.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentMethodRequest request = new PaymentMethodRequest("30-60", "Dos plazos", List.of(
                new PaymentRuleRequest(30, new BigDecimal("60")),
                new PaymentRuleRequest(60, new BigDecimal("40"))));

        PaymentMethodResponse response = service.create(request);

        assertThat(response.code()).isEqualTo("30-60");
        assertThat(response.rules()).extracting(PaymentRuleResponse::installment).containsExactly(1, 2);
        assertThat(response.rules()).extracting(PaymentRuleResponse::percentage)
                .containsExactly(new BigDecimal("60"), new BigDecimal("40"));
    }

    @Test
    void rejectsDuplicateCodesAndInvalidPercentageTotals() {
        when(methods.existsByCompanyIdAndCodeIgnoreCase(companyId, "DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new PaymentMethodRequest("DUP", "Duplicada",
                List.of(new PaymentRuleRequest(0, new BigDecimal("100"))))))
                .isInstanceOf(BusinessRuleException.class);

        when(methods.existsByCompanyIdAndCodeIgnoreCase(companyId, "BAD")).thenReturn(false);
        assertThatThrownBy(() -> service.create(new PaymentMethodRequest("BAD", "Incorrecta",
                List.of(new PaymentRuleRequest(0, new BigDecimal("90"))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sumar 100");
    }
}
