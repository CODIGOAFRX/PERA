package com.peraerp.sales.document;

import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.masterdata.CustomerSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditRiskServiceTest {
    private final UUID company = UUID.randomUUID();
    private final CommercialDocumentRepository documents = mock(CommercialDocumentRepository.class);
    private final CurrentCompanyProvider users = mock(CurrentCompanyProvider.class);
    private final CreditRiskService service = new CreditRiskService(documents, users);

    @BeforeEach
    void pendingInvoices() {
        when(documents.sumOutstandingInvoices(any(), any())).thenReturn(new BigDecimal("800.00"));
    }

    private CustomerSnapshot customer(String limit, String threshold, String policy) {
        return new CustomerSnapshot(UUID.randomUUID(), "C1", "Cliente", true, null, null, null, null, null,
                limit == null ? null : new BigDecimal(limit), threshold == null ? null : new BigDecimal(threshold), policy);
    }

    private CreditRiskService.Assessment assess(CustomerSnapshot customer, String amount) {
        return service.assess(company, customer, new BigDecimal(amount));
    }

    @Test
    void addsPendingInvoicesToTheNewDocument() {
        var risk = assess(customer("1000", "900", "WARN"), "150.00");
        assertThat(risk.outstanding()).isEqualByComparingTo("800.00");
        assertThat(risk.projected()).isEqualByComparingTo("950.00");
        assertThat(risk.level()).isEqualTo(CreditRiskService.Level.WARNING);
    }

    @Test
    void warnPolicyNeverStopsTheSaleAndZeroLimitMeansNoControl() {
        var over = assess(customer("1000", "0", "WARN"), "500.00");
        assertThat(over.level()).isEqualTo(CreditRiskService.Level.OVER_LIMIT);
        assertThatCode(() -> service.enforce(over, false)).doesNotThrowAnyException();
        assertThat(assess(customer("0", "0", "WARN"), "5000.00").level()).isEqualTo(CreditRiskService.Level.OK);
        assertThat(assess(customer(null, null, null), "5000.00").level()).isEqualTo(CreditRiskService.Level.OK);
    }

    @Test
    void confirmationPolicyNeedsAnExplicitAcknowledgement() {
        var risk = assess(customer("1000", "0", "REQUIRE_CONFIRMATION"), "500.00");
        assertThatThrownBy(() -> service.enforce(risk, false)).isInstanceOf(CreditRiskException.class);
        assertThatCode(() -> service.enforce(risk, true)).doesNotThrowAnyException();
    }

    @Test
    void blockStopsRegularUsersEvenWhenTheyAcknowledge() {
        var risk = assess(customer("0", "0", "BLOCK"), "10.00");
        assertThat(risk.blocked()).isTrue();
        assertThatThrownBy(() -> service.enforce(risk, true)).isInstanceOf(CreditRiskException.class)
                .hasMessageContaining("bloqueada");
    }

    @Test
    void ownersAndAdministratorsMayContinuePastABlockAfterConfirming() {
        when(users.hasAnyRole("OWNER", "ADMIN")).thenReturn(true);
        var risk = assess(customer("0", "0", "BLOCK"), "10.00");
        assertThat(risk.blocked()).isFalse();
        assertThatThrownBy(() -> service.enforce(risk, false)).isInstanceOf(CreditRiskException.class);
        assertThatCode(() -> service.enforce(risk, true)).doesNotThrowAnyException();
    }

    @Test
    void withinTheLimitNothingIsAsked() {
        var risk = assess(customer("5000", "4000", "BLOCK"), "100.00");
        assertThat(risk.level()).isEqualTo(CreditRiskService.Level.OK);
        assertThatCode(() -> service.enforce(risk, false)).doesNotThrowAnyException();
    }
}
