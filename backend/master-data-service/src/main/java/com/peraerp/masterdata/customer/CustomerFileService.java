package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.customer.CustomerFileDtos.AddressRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.AddressResponse;
import com.peraerp.masterdata.customer.CustomerFileDtos.ContactRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.ContactResponse;
import com.peraerp.masterdata.customer.CustomerFileDtos.NoteRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.NoteResponse;
import com.peraerp.masterdata.party.PartyAddress;
import com.peraerp.masterdata.party.PartyAddressRepository;
import com.peraerp.masterdata.party.PartyContact;
import com.peraerp.masterdata.party.PartyContactRepository;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.peraerp.masterdata.customer.CustomerSetupService.blankToNull;

/**
 * Contactos, direcciones de entrega y notas de un cliente. Los contactos y direcciones cuelgan del tercero
 * (así servirán también para proveedores); las notas, del perfil de cliente.
 */
@Service
public class CustomerFileService {
    private final CustomerProfileRepository customerRepository;
    private final PartyContactRepository contactRepository;
    private final PartyAddressRepository addressRepository;
    private final CustomerNoteRepository noteRepository;
    private final CurrentCompanyProvider companyProvider;

    public CustomerFileService(CustomerProfileRepository customerRepository, PartyContactRepository contactRepository,
                               PartyAddressRepository addressRepository, CustomerNoteRepository noteRepository,
                               CurrentCompanyProvider companyProvider) {
        this.customerRepository = customerRepository;
        this.contactRepository = contactRepository;
        this.addressRepository = addressRepository;
        this.noteRepository = noteRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> contacts(UUID customerId) {
        CustomerProfile customer = requireCustomer(customerId);
        return contactRepository.findAllByCompanyIdAndPartyIdOrderByPrimaryContactDescNameAsc(
                customer.getCompanyId(), customer.getPartyId()).stream().map(ContactResponse::from).toList();
    }

    @Transactional
    public ContactResponse createContact(UUID customerId, ContactRequest request) {
        CustomerProfile customer = requireCustomer(customerId);
        PartyContact contact = new PartyContact(customer.getCompanyId(), customer.getPartyId());
        return saveContact(customer, contact, request);
    }

    @Transactional
    public ContactResponse updateContact(UUID customerId, UUID contactId, ContactRequest request) {
        CustomerProfile customer = requireCustomer(customerId);
        return saveContact(customer, requireContact(customer, contactId), request);
    }

    @Transactional
    public void deleteContact(UUID customerId, UUID contactId) {
        CustomerProfile customer = requireCustomer(customerId);
        contactRepository.delete(requireContact(customer, contactId));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(UUID customerId) {
        CustomerProfile customer = requireCustomer(customerId);
        return deliveryAddresses(customer).stream().map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse createAddress(UUID customerId, AddressRequest request) {
        CustomerProfile customer = requireCustomer(customerId);
        PartyAddress address = new PartyAddress(customer.getCompanyId(), customer.getPartyId(), PartyAddress.DELIVERY);
        return saveAddress(customer, address, request);
    }

    @Transactional
    public AddressResponse updateAddress(UUID customerId, UUID addressId, AddressRequest request) {
        CustomerProfile customer = requireCustomer(customerId);
        return saveAddress(customer, requireAddress(customer, addressId), request);
    }

    @Transactional
    public void deleteAddress(UUID customerId, UUID addressId) {
        CustomerProfile customer = requireCustomer(customerId);
        addressRepository.delete(requireAddress(customer, addressId));
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> notes(UUID customerId) {
        CustomerProfile customer = requireCustomer(customerId);
        return noteRepository.findAllByCompanyIdAndCustomerIdAndActiveTrueOrderByCreatedAtDesc(
                customer.getCompanyId(), customer.getId()).stream().map(NoteResponse::from).toList();
    }

    @Transactional
    public NoteResponse createNote(UUID customerId, NoteRequest request) {
        CustomerProfile customer = requireCustomer(customerId);
        CustomerNote note = new CustomerNote(customer.getCompanyId(), customer.getId());
        note.update(request.title().trim(), request.message().trim(), Boolean.TRUE.equals(request.showOnDocuments()));
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public NoteResponse updateNote(UUID customerId, UUID noteId, NoteRequest request) {
        CustomerNote note = requireNote(requireCustomer(customerId), noteId);
        note.update(request.title().trim(), request.message().trim(), Boolean.TRUE.equals(request.showOnDocuments()));
        return NoteResponse.from(note);
    }

    @Transactional
    public void deleteNote(UUID customerId, UUID noteId) {
        requireNote(requireCustomer(customerId), noteId).deactivate();
    }

    private ContactResponse saveContact(CustomerProfile customer, PartyContact contact, ContactRequest request) {
        boolean primary = Boolean.TRUE.equals(request.primaryContact());
        if (primary) {
            // El índice único de contacto principal obliga a quitar la marca del anterior antes de guardar.
            contactRepository.findAllByCompanyIdAndPartyId(customer.getCompanyId(), customer.getPartyId()).stream()
                    .filter(other -> other.isPrimaryContact() && !other.equals(contact))
                    .forEach(PartyContact::clearPrimary);
            contactRepository.flush();
        }
        contact.update(request.name().trim(), blankToNull(request.position()), blankToNull(request.phone()),
                blankToNull(request.mobile()), blankToNull(request.email()), blankToNull(request.notes()), primary);
        return ContactResponse.from(contactRepository.save(contact));
    }

    private AddressResponse saveAddress(CustomerProfile customer, PartyAddress address, AddressRequest request) {
        boolean active = request.active() == null || request.active();
        // Una dirección dada de baja no puede ser la de entrega habitual.
        boolean primary = active && Boolean.TRUE.equals(request.primaryAddress());
        if (primary) {
            deliveryAddresses(customer).stream()
                    .filter(other -> other.isPrimaryAddress() && !other.equals(address))
                    .forEach(PartyAddress::clearPrimary);
            addressRepository.flush();
        }
        address.update(blankToNull(request.label()), request.line1().trim(), blankToNull(request.line2()),
                blankToNull(request.postalCode()), blankToNull(request.city()), blankToNull(request.province()),
                blankToNull(request.country()), blankToNull(request.contactPhone()), primary, active);
        return AddressResponse.from(addressRepository.save(address));
    }

    private List<PartyAddress> deliveryAddresses(CustomerProfile customer) {
        return addressRepository.findAllByCompanyIdAndPartyIdAndTypeOrderByPrimaryAddressDescActiveDescLabelAsc(
                customer.getCompanyId(), customer.getPartyId(), PartyAddress.DELIVERY);
    }

    private CustomerProfile requireCustomer(UUID customerId) {
        return customerRepository.findByIdAndCompanyId(customerId, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", customerId));
    }

    private PartyContact requireContact(CustomerProfile customer, UUID contactId) {
        return contactRepository.findByIdAndCompanyIdAndPartyId(contactId, customer.getCompanyId(),
                        customer.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Contacto", contactId));
    }

    private PartyAddress requireAddress(CustomerProfile customer, UUID addressId) {
        return addressRepository.findByIdAndCompanyIdAndPartyId(addressId, customer.getCompanyId(),
                        customer.getPartyId())
                .filter(address -> PartyAddress.DELIVERY.equals(address.getType()))
                .orElseThrow(() -> new ResourceNotFoundException("Dirección", addressId));
    }

    private CustomerNote requireNote(CustomerProfile customer, UUID noteId) {
        return noteRepository.findByIdAndCompanyIdAndCustomerIdAndActiveTrue(noteId, customer.getCompanyId(),
                        customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nota", noteId));
    }
}
