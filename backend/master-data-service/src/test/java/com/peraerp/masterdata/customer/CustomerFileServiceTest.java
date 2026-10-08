package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.customer.CustomerFileDtos.AddressRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.ContactRequest;
import com.peraerp.masterdata.party.PartyAddress;
import com.peraerp.masterdata.party.PartyAddressRepository;
import com.peraerp.masterdata.party.PartyContact;
import com.peraerp.masterdata.party.PartyContactRepository;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerFileServiceTest {
    @Mock CustomerProfileRepository customers;
    @Mock PartyContactRepository contacts;
    @Mock PartyAddressRepository addresses;
    @Mock CustomerNoteRepository notes;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID partyId = UUID.randomUUID();
    private CustomerFileService service;

    @BeforeEach
    void setUp() {
        service = new CustomerFileService(customers, contacts, addresses, notes, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.of(
                new CustomerProfile(companyId, partyId, null, null, null, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, RiskPolicy.WARN)));
    }

    @Test
    void newPrimaryContactTakesTheMarkFromThePreviousOneBeforeSaving() {
        PartyContact previous = new PartyContact(companyId, partyId);
        previous.update("Ana", null, null, null, null, null, true);
        when(contacts.findAllByCompanyIdAndPartyId(companyId, partyId)).thenReturn(List.of(previous));
        when(contacts.save(any(PartyContact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.createContact(customerId,
                new ContactRequest(" Luis ", "Compras", null, "600", " ", null, true));

        assertThat(previous.isPrimaryContact()).isFalse();
        assertThat(created.primaryContact()).isTrue();
        assertThat(created.name()).isEqualTo("Luis");
        assertThat(created.email()).isNull();
        // El índice único obliga a escribir la baja de la marca antes de insertar el nuevo principal.
        InOrder order = inOrder(contacts);
        order.verify(contacts).flush();
        order.verify(contacts).save(any(PartyContact.class));
    }

    @Test
    void dischargedAddressCannotBeTheUsualDeliveryAddress() {
        when(addresses.save(any(PartyAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var address = service.createAddress(customerId, new AddressRequest("Obra norte", "Calle Mayor 1", null,
                "28001", "Madrid", "Madrid", "España", null, true, false));

        assertThat(address.type()).isEqualTo(PartyAddress.DELIVERY);
        assertThat(address.primaryAddress()).isFalse();
        assertThat(address.active()).isFalse();
    }

    @Test
    void contactOfAnotherCustomerIsNotFound() {
        UUID foreignContact = UUID.randomUUID();
        when(contacts.findByIdAndCompanyIdAndPartyId(foreignContact, companyId, partyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteContact(customerId, foreignContact))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
