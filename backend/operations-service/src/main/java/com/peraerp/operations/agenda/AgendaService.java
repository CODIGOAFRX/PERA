package com.peraerp.operations.agenda;

import com.peraerp.operations.agenda.AgendaDtos.CancelEntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.CompleteEntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.ContactRequest;
import com.peraerp.operations.agenda.AgendaDtos.ContactResponse;
import com.peraerp.operations.agenda.AgendaDtos.EntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.EntryResponse;
import com.peraerp.operations.agenda.AgendaDtos.EntryTypeRequest;
import com.peraerp.operations.agenda.AgendaDtos.EntryTypeResponse;
import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.config.CurrentUserProvider;
import com.peraerp.operations.config.CurrentUserProvider.CurrentUser;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AgendaService {

    /** Un periodo de consulta más largo no cabe en una pantalla y solo carga la base de datos. */
    static final int MAX_PERIOD_DAYS = 62;

    private final AgendaEntryRepository entries;
    private final AgendaEntryTypeRepository types;
    private final ContactRepository contacts;
    private final CurrentCompanyProvider companyProvider;
    private final CurrentUserProvider userProvider;

    public AgendaService(AgendaEntryRepository entries, AgendaEntryTypeRepository types, ContactRepository contacts,
                         CurrentCompanyProvider companyProvider, CurrentUserProvider userProvider) {
        this.entries = entries;
        this.types = types;
        this.contacts = contacts;
        this.companyProvider = companyProvider;
        this.userProvider = userProvider;
    }

    // --- Tipos de cita

    @Transactional(readOnly = true)
    public List<EntryTypeResponse> types() {
        return types.findAllByCompanyIdOrderByNameAsc(companyProvider.requireCompanyId()).stream()
                .map(EntryTypeResponse::from).toList();
    }

    @Transactional
    public EntryTypeResponse createType(EntryTypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String name = request.name().trim();
        if (types.existsByCompanyIdAndNameIgnoreCase(companyId, name)) {
            throw new BusinessRuleException("Ya existe el tipo de cita «" + name + "».");
        }
        AgendaEntryType type = new AgendaEntryType(companyId);
        type.update(name, normalizeColor(request.color()), request.active() == null || request.active());
        return EntryTypeResponse.from(types.save(type));
    }

    @Transactional
    public EntryTypeResponse updateType(UUID id, EntryTypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        AgendaEntryType type = types.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita", id));
        String name = request.name().trim();
        if (types.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, name, id)) {
            throw new BusinessRuleException("Ya existe el tipo de cita «" + name + "».");
        }
        type.update(name, normalizeColor(request.color()), request.active() == null || request.active());
        return EntryTypeResponse.from(type);
    }

    // --- Listín

    @Transactional(readOnly = true)
    public Page<ContactResponse> searchContacts(Boolean active, UUID customerId, String query, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalize(query);
        return contacts.search(companyId, active != null, Boolean.TRUE.equals(active), customerId != null, customerId,
                normalizedQuery != null, normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(ContactResponse::from);
    }

    @Transactional
    public ContactResponse createContact(ContactRequest request) {
        Contact contact = new Contact(companyProvider.requireCompanyId());
        applyContact(contact, request);
        return ContactResponse.from(contacts.save(contact));
    }

    @Transactional
    public ContactResponse updateContact(UUID id, ContactRequest request) {
        Contact contact = contacts.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Contacto", id));
        applyContact(contact, request);
        return ContactResponse.from(contact);
    }

    private void applyContact(Contact contact, ContactRequest request) {
        if (request.customerId() != null && normalize(request.customerName()) == null) {
            throw new BusinessRuleException("Indica el nombre del cliente vinculado.");
        }
        if (normalize(request.phone()) == null && normalize(request.mobile()) == null
                && normalize(request.email()) == null) {
            throw new BusinessRuleException("Indica al menos un teléfono, un móvil o un correo.");
        }
        contact.update(request.name().trim(), normalize(request.organization()), normalize(request.phone()),
                normalize(request.mobile()), normalizeEmail(request.email()), normalize(request.address()),
                normalize(request.postalCode()), normalize(request.city()), normalize(request.region()),
                request.customerId(), normalize(request.customerName()), normalize(request.notes()),
                request.active() == null || request.active());
    }

    // --- Citas

    @Transactional(readOnly = true)
    public List<EntryResponse> period(LocalDate fromDate, LocalDate toDate, AgendaEntryStatus status, UUID typeId,
                                      String assignee, String query) {
        if (toDate.isBefore(fromDate)) {
            throw new BusinessRuleException("El final del periodo no puede ser anterior al inicio.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_PERIOD_DAYS) {
            throw new BusinessRuleException("Consulta como mucho " + MAX_PERIOD_DAYS + " días de agenda a la vez.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedAssignee = normalize(assignee);
        String normalizedQuery = normalize(query);
        List<AgendaEntry> found = entries.findPeriod(companyId, fromDate, toDate, status != null, status,
                typeId != null, typeId, normalizedAssignee != null, normalizedAssignee == null ? "" : normalizedAssignee,
                normalizedQuery != null, normalizedQuery == null ? "" : normalizedQuery);
        Map<UUID, Contact> contactById = new HashMap<>();
        found.stream().map(AgendaEntry::getContactId).filter(java.util.Objects::nonNull).distinct()
                .forEach(id -> contacts.findByIdAndCompanyId(id, companyId).ifPresent(c -> contactById.put(id, c)));
        return found.stream().map(entry -> response(entry, contactById.get(entry.getContactId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<String> assignees() {
        return entries.findAssignees(companyProvider.requireCompanyId());
    }

    @Transactional
    public EntryResponse createEntry(EntryRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        CurrentUser user = userProvider.requireUser();
        AgendaEntry entry = new AgendaEntry(companyId, user.id(), user.name());
        Contact contact = applyEntry(entry, request);
        return response(entries.save(entry), contact);
    }

    @Transactional
    public EntryResponse updateEntry(UUID id, EntryRequest request) {
        AgendaEntry entry = requireEntry(id);
        Contact contact = applyEntry(entry, request);
        return response(entry, contact);
    }

    @Transactional
    public EntryResponse complete(UUID id, CompleteEntryRequest request) {
        if (request.completedOn().isAfter(LocalDate.now().plusDays(1))) {
            throw new BusinessRuleException("No se puede dar por hecha una cita en una fecha futura.");
        }
        return change(id, entry -> entry.complete(request.completedOn(), normalize(request.outcome())));
    }

    @Transactional
    public EntryResponse cancel(UUID id, CancelEntryRequest request) {
        return change(id, entry -> entry.cancel(request == null ? null : normalize(request.reason())));
    }

    @Transactional
    public EntryResponse reopen(UUID id) {
        return change(id, AgendaEntry::reopen);
    }

    @Transactional
    public void deleteEntry(UUID id) {
        entries.delete(requireEntry(id));
    }

    private EntryResponse change(UUID id, Consumer<AgendaEntry> operation) {
        AgendaEntry entry = requireEntry(id);
        try {
            operation.accept(entry);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return response(entry, contactOf(entry));
    }

    private Contact applyEntry(AgendaEntry entry, EntryRequest request) {
        UUID companyId = entry.getCompanyId();
        if (request.typeId() != null) {
            AgendaEntryType type = types.findByIdAndCompanyId(request.typeId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de cita", request.typeId()));
            if (!type.isActive() && !request.typeId().equals(entry.getTypeId())) {
                throw new BusinessRuleException("El tipo de cita «" + type.getName() + "» está desactivado.");
            }
        }
        Contact contact = null;
        if (request.contactId() != null) {
            contact = contacts.findByIdAndCompanyId(request.contactId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contacto", request.contactId()));
        }
        if (request.customerId() != null && normalize(request.customerName()) == null) {
            throw new BusinessRuleException("Indica el nombre del cliente de la cita.");
        }
        try {
            entry.update(request.date(), request.startTime(), request.endTime(), request.title().trim(),
                    normalize(request.details()), request.typeId(), normalize(request.assigneeName()),
                    request.customerId(), normalize(request.customerName()), request.contactId(),
                    normalize(request.location()), normalize(request.documentReference()));
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return contact;
    }

    private AgendaEntry requireEntry(UUID id) {
        return entries.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita", id));
    }

    private Contact contactOf(AgendaEntry entry) {
        return entry.getContactId() == null ? null
                : contacts.findByIdAndCompanyId(entry.getContactId(), entry.getCompanyId()).orElse(null);
    }

    private EntryResponse response(AgendaEntry entry, Contact contact) {
        String phone = contact == null ? null : contact.getMobile() != null ? contact.getMobile() : contact.getPhone();
        return new EntryResponse(entry.getId(), entry.getDate(), entry.getStartTime(), entry.getEndTime(),
                entry.getTitle(), entry.getDetails(), entry.getTypeId(), entry.getAssigneeName(), entry.getCustomerId(),
                entry.getCustomerNameSnapshot(), entry.getContactId(), contact == null ? null : contact.getName(),
                phone, entry.getLocation(), entry.getDocumentReference(), entry.getStatus(), entry.getCompletedOn(),
                entry.getOutcome(), entry.getCreatedByName());
    }

    private String normalizeColor(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
