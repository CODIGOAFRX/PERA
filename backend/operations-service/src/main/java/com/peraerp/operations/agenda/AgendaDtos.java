package com.peraerp.operations.agenda;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class AgendaDtos {

    private AgendaDtos() {
    }

    public record EntryTypeRequest(
            @NotBlank @Size(max = 80) String name,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
            Boolean active
    ) {
    }

    public record EntryTypeResponse(UUID id, String name, String color, boolean active) {
        static EntryTypeResponse from(AgendaEntryType type) {
            return new EntryTypeResponse(type.getId(), type.getName(), type.getColor(), type.isActive());
        }
    }

    public record ContactRequest(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 180) String organization,
            @Size(max = 40) String phone,
            @Size(max = 40) String mobile,
            @Email @Size(max = 180) String email,
            @Size(max = 300) String address,
            @Size(max = 20) String postalCode,
            @Size(max = 120) String city,
            @Size(max = 120) String region,
            UUID customerId,
            @Size(max = 180) String customerName,
            @Size(max = 1000) String notes,
            Boolean active
    ) {
    }

    public record ContactResponse(UUID id, String name, String organization, String phone, String mobile,
                                  String email, String address, String postalCode, String city, String region,
                                  UUID customerId, String customerName, String notes, boolean active) {
        static ContactResponse from(Contact contact) {
            return new ContactResponse(contact.getId(), contact.getName(), contact.getOrganization(),
                    contact.getPhone(), contact.getMobile(), contact.getEmail(), contact.getAddress(),
                    contact.getPostalCode(), contact.getCity(), contact.getRegion(), contact.getCustomerId(),
                    contact.getCustomerNameSnapshot(), contact.getNotes(), contact.isActive());
        }
    }

    public record EntryRequest(
            @NotNull LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String details,
            UUID typeId,
            @Size(max = 160) String assigneeName,
            UUID customerId,
            @Size(max = 180) String customerName,
            UUID contactId,
            @Size(max = 300) String location,
            @Size(max = 100) String documentReference
    ) {
    }

    public record CompleteEntryRequest(@NotNull LocalDate completedOn, @Size(max = 1000) String outcome) {
    }

    public record CancelEntryRequest(@Size(max = 1000) String reason) {
    }

    public record EntryResponse(UUID id, LocalDate date, LocalTime startTime, LocalTime endTime, String title,
                                String details, UUID typeId, String assigneeName, UUID customerId,
                                String customerName, UUID contactId, String contactName, String contactPhone,
                                String location, String documentReference, AgendaEntryStatus status,
                                LocalDate completedOn, String outcome, String createdByName) {
    }
}
