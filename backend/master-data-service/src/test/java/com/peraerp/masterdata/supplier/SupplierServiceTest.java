package com.peraerp.masterdata.supplier;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.party.Party;
import com.peraerp.masterdata.party.PartyRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {
    @Mock SupplierProfileRepository suppliers;
    @Mock PartyRepository parties;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(suppliers, parties, companyProvider);
    }

    @Test
    void createsSupplierWithOptionalLogistics() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(parties.existsByCompanyIdAndCodeIgnoreCase(companyId, " p001 ")).thenReturn(false);
        when(parties.save(any(Party.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(suppliers.save(any(SupplierProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = service.create(request(" p001 ", " Proveedor Uno ", true));

        assertThat(response.code()).isEqualTo("P001");
        assertThat(response.legalName()).isEqualTo("Proveedor Uno");
        assertThat(response.carrier()).isEqualTo("Transporte Norte");
        assertThat(response.route()).isEqualTo("Ruta A");
    }

    @Test
    void updatesSupplierAndCanDeactivateIt() {
        UUID supplierId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        Party party = new Party(companyId, "P001", "Anterior", null, null, null, null, null);
        SupplierProfile profile = new SupplierProfile(companyId, partyId, null, null, null);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(suppliers.findByIdAndCompanyId(supplierId, companyId)).thenReturn(Optional.of(profile));
        when(parties.findByIdAndCompanyId(partyId, companyId)).thenReturn(Optional.of(party));

        SupplierResponse response = service.update(supplierId, request("P001", "Proveedor actualizado", false));

        assertThat(response.legalName()).isEqualTo("Proveedor actualizado");
        assertThat(response.active()).isFalse();
        assertThat(response.route()).isEqualTo("Ruta A");
    }

    @Test
    void rejectsSupplierCodeChanges() {
        UUID supplierId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        Party party = new Party(companyId, "P001", "Proveedor", null, null, null, null, null);
        SupplierProfile profile = new SupplierProfile(companyId, partyId, null, null, null);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(suppliers.findByIdAndCompanyId(supplierId, companyId)).thenReturn(Optional.of(profile));
        when(parties.findByIdAndCompanyId(partyId, companyId)).thenReturn(Optional.of(party));

        assertThatThrownBy(() -> service.update(supplierId, request("NEW", "Proveedor", true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");
    }

    @Test
    void usesRepositoryAlphabeticalOrderInsteadOfEntitySortFields() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(suppliers.search(any(UUID.class), any(String.class), any(Pageable.class))).thenReturn(Page.empty());

        service.search(" proveedor ", PageRequest.of(1, 20, Sort.by("legalName")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(suppliers).search(eq(companyId), eq("proveedor"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
    }

    private SupplierRequest request(String code, String name, Boolean active) {
        return new SupplierRequest(code, name, "Comercial", "A58818501", "611000000", "proveedor@demo.es",
                "Observaciones", "Transporte Norte", "Ruta A", null, active);
    }
}
