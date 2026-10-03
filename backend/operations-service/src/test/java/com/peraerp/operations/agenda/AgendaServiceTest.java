package com.peraerp.operations.agenda;

import com.peraerp.operations.agenda.AgendaDtos.CompleteEntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.ContactRequest;
import com.peraerp.operations.agenda.AgendaDtos.ContactResponse;
import com.peraerp.operations.agenda.AgendaDtos.EntryRequest;
import com.peraerp.operations.agenda.AgendaDtos.EntryResponse;
import com.peraerp.operations.agenda.AgendaDtos.EntryTypeRequest;
import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.config.CurrentUserProvider;
import com.peraerp.operations.config.CurrentUserProvider.CurrentUser;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Mock AgendaEntryRepository entries;
    @Mock AgendaEntryTypeRepository types;
    @Mock ContactRepository contacts;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock CurrentUserProvider userProvider;

    private final Map<UUID, AgendaEntry> entryById = new HashMap<>();
    private final Map<UUID, Contact> contactById = new HashMap<>();
    private final Map<UUID, AgendaEntryType> typeById = new HashMap<>();
    private AgendaService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new AgendaService(entries, types, contacts, companyProvider, userProvider);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(userProvider.requireUser()).thenReturn(new CurrentUser(UUID.randomUUID(), "Raúl"));
        when(entries.save(any(AgendaEntry.class))).thenAnswer(invocation -> {
            AgendaEntry entry = invocation.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
            entryById.put(entry.getId(), entry);
            return entry;
        });
        when(entries.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(entryById.get(invocation.<UUID>getArgument(0))));
        when(contacts.save(any(Contact.class))).thenAnswer(invocation -> {
            Contact contact = invocation.getArgument(0);
            ReflectionTestUtils.setField(contact, "id", UUID.randomUUID());
            contactById.put(contact.getId(), contact);
            return contact;
        });
        when(contacts.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(contactById.get(invocation.<UUID>getArgument(0))));
        when(types.save(any(AgendaEntryType.class))).thenAnswer(invocation -> {
            AgendaEntryType type = invocation.getArgument(0);
            ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
            typeById.put(type.getId(), type);
            return type;
        });
        when(types.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(typeById.get(invocation.<UUID>getArgument(0))));
    }

    @Test
    void createsAnAppointmentWithTypeContactAndCreator() {
        UUID type = service.createType(new EntryTypeRequest("Medición", "#2F6B3F", null)).id();
        ContactResponse contact = service.createContact(contact("Pedro Ruiz", "600111222"));

        EntryResponse entry = service.createEntry(new EntryRequest(MONDAY, LocalTime.of(9, 30), LocalTime.of(10, 30),
                " Medir ventanas ", null, type, " Juan ", null, null, contact.id(), "Calle Mayor 3", "PRE-2026-000012"));

        assertThat(entry.title()).isEqualTo("Medir ventanas");
        assertThat(entry.assigneeName()).isEqualTo("Juan");
        assertThat(entry.status()).isEqualTo(AgendaEntryStatus.PENDING);
        assertThat(entry.contactName()).isEqualTo("Pedro Ruiz");
        assertThat(entry.contactPhone()).isEqualTo("600111222");
        assertThat(entry.createdByName()).isEqualTo("Raúl");
        assertThat(typeById.get(type).getColor()).isEqualTo("#2f6b3f");
    }

    @Test
    void validatesTimesAndReferences() {
        assertThatThrownBy(() -> service.createEntry(entry(LocalTime.of(11, 0), LocalTime.of(10, 0))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("posterior");
        assertThatThrownBy(() -> service.createEntry(entry(null, LocalTime.of(10, 0))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.createEntry(new EntryRequest(MONDAY, null, null, "Llamar", null, null, null,
                null, null, UUID.randomUUID(), null, null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.createEntry(new EntryRequest(MONDAY, null, null, "Llamar", null, null, null,
                UUID.randomUUID(), " ", null, null, null))).isInstanceOf(BusinessRuleException.class);
        verify(entries, never()).save(any());
    }

    @Test
    void anAppointmentIsCompletedWithItsOutcomeAndLockedUntilReopened() {
        EntryResponse created = service.createEntry(entry(null, null));

        assertThatThrownBy(() -> service.complete(created.id(), new CompleteEntryRequest(MONDAY.plusYears(1), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("futura");
        EntryResponse done = service.complete(created.id(),
                new CompleteEntryRequest(LocalDate.now(), " Medidas tomadas: 4 huecos "));

        assertThat(done.status()).isEqualTo(AgendaEntryStatus.DONE);
        assertThat(done.outcome()).isEqualTo("Medidas tomadas: 4 huecos");
        assertThatThrownBy(() -> service.updateEntry(created.id(), entry(null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Reábrela");
        assertThat(service.reopen(created.id()).status()).isEqualTo(AgendaEntryStatus.PENDING);
        assertThat(service.cancel(created.id(), null).status()).isEqualTo(AgendaEntryStatus.CANCELLED);
    }

    @Test
    void thePeriodIsLimitedAndPassesTheFilters() {
        assertThatThrownBy(() -> service.period(MONDAY, MONDAY.minusDays(1), null, null, null, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.period(MONDAY, MONDAY.plusDays(AgendaService.MAX_PERIOD_DAYS), null, null,
                null, null)).isInstanceOf(BusinessRuleException.class);
        when(entries.findPeriod(eq(companyId), eq(MONDAY), eq(MONDAY.plusDays(6)), anyBoolean(), any(), anyBoolean(),
                any(), anyBoolean(), any(), anyBoolean(), any())).thenReturn(List.of());

        assertThat(service.period(MONDAY, MONDAY.plusDays(6), AgendaEntryStatus.PENDING, null, " Juan ", null))
                .isEmpty();
        verify(entries).findPeriod(companyId, MONDAY, MONDAY.plusDays(6), true, AgendaEntryStatus.PENDING, false,
                null, true, "Juan", false, "");
    }

    @Test
    void aContactNeedsAWayToBeReached() {
        assertThatThrownBy(() -> service.createContact(new ContactRequest("Sin datos", null, " ", null, null, null,
                null, null, null, null, null, null, null))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("teléfono");

        ContactResponse withEmail = service.createContact(new ContactRequest("Lucía", "Obras Lucía SL", null, null,
                " Lucia@Example.com ", null, null, "Valencia", null, null, null, null, null));
        assertThat(withEmail.email()).isEqualTo("lucia@example.com");
        assertThat(withEmail.active()).isTrue();
    }

    private EntryRequest entry(LocalTime start, LocalTime end) {
        return new EntryRequest(MONDAY, start, end, "Montaje", null, null, null, null, null, null, null, null);
    }

    private ContactRequest contact(String name, String mobile) {
        return new ContactRequest(name, null, null, mobile, null, null, null, null, null, null, null, null, null);
    }
}
