package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.party.Party;
import com.peraerp.masterdata.party.TaxIdentificationType;
import com.peraerp.masterdata.party.PartyRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class CustomerServiceTest {
    @Mock CustomerProfileRepository customers;
    @Mock PartyRepository parties;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customers, parties, companyProvider);
    }

    @Test
    void createsNormalizedCustomerWithSafeDefaults() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(parties.existsByCompanyIdAndCodeIgnoreCase(companyId, " c001 ")).thenReturn(false);
        when(parties.save(any(Party.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customers.save(any(CustomerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerResponse response = service.create(request(" c001 ", " Cliente Uno ", true));

        assertThat(response.code()).isEqualTo("C001");
        assertThat(response.legalName()).isEqualTo("Cliente Uno");
        assertThat(response.creditLimit()).isEqualByComparingTo("1000");
        assertThat(response.riskPolicy()).isEqualTo(RiskPolicy.WARN);
        ArgumentCaptor<CustomerProfile> captor = ArgumentCaptor.forClass(CustomerProfile.class);
        verify(customers).save(captor.capture());
        assertThat(captor.getValue().getCalculationMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void updatesCommercialDataAndActiveStateWithoutChangingCode() {
        UUID customerId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        Party party = new Party(companyId, "C001", "Anterior", null, null, null, null, null);
        CustomerProfile profile = new CustomerProfile(companyId, partyId, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, RiskPolicy.WARN);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.of(profile));
        when(parties.findByIdAndCompanyId(partyId, companyId)).thenReturn(Optional.of(party));

        CustomerResponse response = service.update(customerId, request("C001", "Nombre actualizado", false));

        assertThat(response.legalName()).isEqualTo("Nombre actualizado");
        assertThat(response.active()).isFalse();
        assertThat(response.creditLimit()).isEqualByComparingTo("1000");
    }

    @Test
    void rejectsCodeChangesOnUpdate() {
        UUID customerId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        CustomerProfile profile = new CustomerProfile(companyId, partyId, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, RiskPolicy.WARN);
        Party party = new Party(companyId, "C001", "Cliente", null, null, null, null, null);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.of(profile));
        when(parties.findByIdAndCompanyId(partyId, companyId)).thenReturn(Optional.of(party));

        assertThatThrownBy(() -> service.update(customerId, request("OTHER", "Cliente", true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");
    }

    @Test
    void isolatesMissingCustomersByCompany() {
        UUID customerId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void usesRepositoryAlphabeticalOrderInsteadOfEntitySortFields() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.search(any(UUID.class), any(String.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search(" cliente ", PageRequest.of(2, 12, Sort.by("legalName")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customers).search(eq(companyId), eq("cliente"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
    }

    private CustomerRequest request(String code, String name, Boolean active) {
        return new CustomerRequest(code, name, "Comercial", "B123", TaxIdentificationType.NIF, "ES",
                "600000000", "cliente@demo.es", "Observaciones", null, null, "SUP-01", null,
                new BigDecimal("1000"), new BigDecimal("800"), RiskPolicy.WARN, active);
    }
}
