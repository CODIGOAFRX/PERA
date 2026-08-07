package com.peraerp.finance.receivable;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.payment.PaymentMethod;
import com.peraerp.finance.payment.PaymentMethodRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DueDateServiceTest {
    @Mock DocumentDueDateRepository dueDates;
    @Mock PaymentMethodRepository paymentMethods;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private DueDateService service;

    @BeforeEach
    void setUp() {
        service = new DueDateService(dueDates, paymentMethods, new PaymentScheduleCalculator(), companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void generatesAndPersistsDeterministicDueDates() {
        UUID documentId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        PaymentMethod method = new PaymentMethod(companyId, "50-50", "Dos plazos");
        method.addRule(1, 0, new BigDecimal("50"));
        method.addRule(2, 30, new BigDecimal("50"));
        when(dueDates.existsByCompanyIdAndDocumentId(companyId, documentId)).thenReturn(false);
        when(paymentMethods.findByIdAndCompanyId(methodId, companyId)).thenReturn(Optional.of(method));
        when(dueDates.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<DueDateResponse> result = service.generate(new GenerateDueDatesRequest(documentId, methodId,
                LocalDate.of(2026, 8, 7), new BigDecimal("101")));

        assertThat(result).extracting(DueDateResponse::amount)
                .containsExactly(new BigDecimal("50.5000"), new BigDecimal("50.5000"));
        assertThat(result).extracting(DueDateResponse::dueDate)
                .containsExactly(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 9, 6));
    }

    @Test
    void refusesToGenerateTheSameDocumentTwice() {
        UUID documentId = UUID.randomUUID();
        when(dueDates.existsByCompanyIdAndDocumentId(companyId, documentId)).thenReturn(true);

        assertThatThrownBy(() -> service.generate(new GenerateDueDatesRequest(documentId, UUID.randomUUID(),
                LocalDate.now(), BigDecimal.TEN))).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(paymentMethods);
    }

    @Test
    void reportsUnknownPaymentMethod() {
        UUID documentId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        when(dueDates.existsByCompanyIdAndDocumentId(companyId, documentId)).thenReturn(false);
        when(paymentMethods.findByIdAndCompanyId(methodId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(new GenerateDueDatesRequest(documentId, methodId,
                LocalDate.now(), BigDecimal.TEN))).isInstanceOf(ResourceNotFoundException.class);
    }
}
