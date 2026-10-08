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
    @Mock CustomerCatalogItemRepository catalog;
    @Mock SalespersonRepository salespeople;

    private final UUID companyId = UUID.randomUUID();
    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customers, parties, catalog, salespeople, companyProvider);
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
        when(customers.search(any(UUID.class), any(String.class), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.search(" cliente ", PageRequest.of(2, 12, Sort.by("legalName")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(customers).search(eq(companyId), eq("cliente"), eq(null), eq(null), eq(null), eq(null),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void keepsStoredClassificationWhenAnOldClientDoesNotSendIt() {
        UUID customerId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Party party = new Party(companyId, "C001", "Cliente", null, null, null, null, null);
        CustomerProfile profile = new CustomerProfile(companyId, partyId, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, RiskPolicy.WARN);
        profile.classify(new CustomerClassification(groupId, null, null, null, null, "611", "430000001"), true);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.of(profile));
        when(parties.findByIdAndCompanyId(partyId, companyId)).thenReturn(Optional.of(party));

        CustomerResponse response = service.update(customerId, request("C001", "Cliente", true));

        assertThat(response.classification().groupId()).isEqualTo(groupId);
        assertThat(response.classification().accountingAccount()).isEqualTo("430000001");
    }

    @Test
    void rejectsGroupFromAnotherTableOrAlreadyDischarged() {
        UUID customerId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        Party party = new Party(companyId, "C001", "Cliente", null, null, null, null, null);
        CustomerProfile profile = new CustomerProfile(companyId, partyId, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, RiskPolicy.WARN);
        CustomerCatalogItem type = new CustomerCatalogItem(companyId, CustomerCatalogKind.TYPE, "Particular", true);
        CustomerCatalogItem oldGroup = new CustomerCatalogItem(companyId, CustomerCatalogKind.GROUP, "Antiguo", false);
        UUID typeId = UUID.randomUUID();
        UUID oldGroupId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.of(profile));
        when(parties.findByIdAndCompanyId(partyId, companyId)).thenReturn(Optional.of(party));
        when(catalog.findByIdAndCompanyId(typeId, companyId)).thenReturn(Optional.of(type));
        when(catalog.findByIdAndCompanyId(oldGroupId, companyId)).thenReturn(Optional.of(oldGroup));

        assertThatThrownBy(() -> service.update(customerId, classified("C001", true,
                new CustomerClassification(typeId, null, null, null, null, null, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no existe");
        assertThatThrownBy(() -> service.update(customerId, classified("C001", true,
                new CustomerClassification(oldGroupId, null, null, null, null, null, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("dado de baja");
    }

    @Test
    void keepsInactiveReasonOnlyWhileTheCustomerIsInactive() {
        UUID customerId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        UUID reasonId = UUID.randomUUID();
        Party party = new Party(companyId, "C001", "Cliente", null, null, null, null, null);
        CustomerProfile profile = new CustomerProfile(companyId, partyId, null, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, RiskPolicy.WARN);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.of(profile));
        when(parties.findByIdAndCompanyId(partyId, companyId)).thenReturn(Optional.of(party));
        when(catalog.findByIdAndCompanyId(reasonId, companyId)).thenReturn(Optional.of(
                new CustomerCatalogItem(companyId, CustomerCatalogKind.INACTIVE_REASON, "Cierre", true)));
        CustomerClassification withReason = new CustomerClassification(null, null, null, null, reasonId, null, null);

        assertThat(service.update(customerId, classified("C001", false, withReason))
                .classification().inactiveReasonId()).isEqualTo(reasonId);
        assertThat(service.update(customerId, classified("C001", true, withReason))
                .classification().inactiveReasonId()).isNull();
    }

    private CustomerRequest classified(String code, Boolean active, CustomerClassification classification) {
        return new CustomerRequest(code, "Cliente", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, active, null, classification);
    }

    private CustomerRequest request(String code, String name, Boolean active) {
        return new CustomerRequest(code, name, "Comercial", "B75777847", TaxIdentificationType.NIF, "ES",
                "600000000", "cliente@demo.es", "Observaciones", null, null, "SUP-01", null,
                new BigDecimal("1000"), new BigDecimal("800"), RiskPolicy.WARN, active);
    }
}
