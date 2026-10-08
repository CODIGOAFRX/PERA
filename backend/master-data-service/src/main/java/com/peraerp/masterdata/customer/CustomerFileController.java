package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.customer.CustomerFileDtos.AddressRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.AddressResponse;
import com.peraerp.masterdata.customer.CustomerFileDtos.ContactRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.ContactResponse;
import com.peraerp.masterdata.customer.CustomerFileDtos.NoteRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.NoteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}")
public class CustomerFileController {
    private final CustomerFileService service;

    public CustomerFileController(CustomerFileService service) { this.service = service; }

    @GetMapping("/contacts")
    List<ContactResponse> contacts(@PathVariable UUID customerId) { return service.contacts(customerId); }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    ContactResponse createContact(@PathVariable UUID customerId, @Valid @RequestBody ContactRequest request) {
        return service.createContact(customerId, request);
    }

    @PutMapping("/contacts/{contactId}")
    ContactResponse updateContact(@PathVariable UUID customerId, @PathVariable UUID contactId,
                                  @Valid @RequestBody ContactRequest request) {
        return service.updateContact(customerId, contactId, request);
    }

    @DeleteMapping("/contacts/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteContact(@PathVariable UUID customerId, @PathVariable UUID contactId) {
        service.deleteContact(customerId, contactId);
    }

    @GetMapping("/addresses")
    List<AddressResponse> addresses(@PathVariable UUID customerId) { return service.addresses(customerId); }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    AddressResponse createAddress(@PathVariable UUID customerId, @Valid @RequestBody AddressRequest request) {
        return service.createAddress(customerId, request);
    }

    @PutMapping("/addresses/{addressId}")
    AddressResponse updateAddress(@PathVariable UUID customerId, @PathVariable UUID addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return service.updateAddress(customerId, addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAddress(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        service.deleteAddress(customerId, addressId);
    }

    @GetMapping("/notes")
    List<NoteResponse> notes(@PathVariable UUID customerId) { return service.notes(customerId); }

    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    NoteResponse createNote(@PathVariable UUID customerId, @Valid @RequestBody NoteRequest request) {
        return service.createNote(customerId, request);
    }

    @PutMapping("/notes/{noteId}")
    NoteResponse updateNote(@PathVariable UUID customerId, @PathVariable UUID noteId,
                            @Valid @RequestBody NoteRequest request) {
        return service.updateNote(customerId, noteId, request);
    }

    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteNote(@PathVariable UUID customerId, @PathVariable UUID noteId) {
        service.deleteNote(customerId, noteId);
    }
}
