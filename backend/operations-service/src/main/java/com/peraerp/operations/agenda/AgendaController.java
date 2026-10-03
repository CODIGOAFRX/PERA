package com.peraerp.operations.agenda;

import com.peraerp.operations.agenda.AgendaDtos.CancelEntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.CompleteEntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.ContactRequest;
import com.peraerp.operations.agenda.AgendaDtos.ContactResponse;
import com.peraerp.operations.agenda.AgendaDtos.EntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.EntryResponse;
import com.peraerp.operations.agenda.AgendaDtos.EntryTypeRequest;
import com.peraerp.operations.agenda.AgendaDtos.EntryTypeResponse;
import com.peraerp.operations.config.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class AgendaController {

    private final AgendaService service;

    public AgendaController(AgendaService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/agenda-entry-types")
    List<EntryTypeResponse> types() {
        return service.types();
    }

    @PostMapping("/api/v1/agenda-entry-types")
    @ResponseStatus(HttpStatus.CREATED)
    EntryTypeResponse createType(@Valid @RequestBody EntryTypeRequest request) {
        return service.createType(request);
    }

    @PutMapping("/api/v1/agenda-entry-types/{id}")
    EntryTypeResponse updateType(@PathVariable UUID id, @Valid @RequestBody EntryTypeRequest request) {
        return service.updateType(id, request);
    }

    @GetMapping("/api/v1/contacts")
    PageResponse<ContactResponse> contacts(@RequestParam(required = false) Boolean active,
                                           @RequestParam(required = false) UUID customerId,
                                           @RequestParam(required = false) String query, Pageable pageable) {
        return PageResponse.from(service.searchContacts(active, customerId, query, pageable));
    }

    @PostMapping("/api/v1/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    ContactResponse createContact(@Valid @RequestBody ContactRequest request) {
        return service.createContact(request);
    }

    @PutMapping("/api/v1/contacts/{id}")
    ContactResponse updateContact(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        return service.updateContact(id, request);
    }

    @GetMapping("/api/v1/agenda-entries")
    List<EntryResponse> period(@RequestParam LocalDate fromDate, @RequestParam LocalDate toDate,
                               @RequestParam(required = false) AgendaEntryStatus status,
                               @RequestParam(required = false) UUID typeId,
                               @RequestParam(required = false) String assignee,
                               @RequestParam(required = false) String query) {
        return service.period(fromDate, toDate, status, typeId, assignee, query);
    }

    @GetMapping("/api/v1/agenda-entries/assignees")
    List<String> assignees() {
        return service.assignees();
    }

    @PostMapping("/api/v1/agenda-entries")
    @ResponseStatus(HttpStatus.CREATED)
    EntryResponse create(@Valid @RequestBody EntryRequest request) {
        return service.createEntry(request);
    }

    @PutMapping("/api/v1/agenda-entries/{id}")
    EntryResponse update(@PathVariable UUID id, @Valid @RequestBody EntryRequest request) {
        return service.updateEntry(id, request);
    }

    @DeleteMapping("/api/v1/agenda-entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.deleteEntry(id);
    }

    @PostMapping("/api/v1/agenda-entries/{id}/complete")
    EntryResponse complete(@PathVariable UUID id, @Valid @RequestBody CompleteEntryRequest request) {
        return service.complete(id, request);
    }

    @PostMapping("/api/v1/agenda-entries/{id}/cancel")
    EntryResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelEntryRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/api/v1/agenda-entries/{id}/reopen")
    EntryResponse reopen(@PathVariable UUID id) {
        return service.reopen(id);
    }
}
