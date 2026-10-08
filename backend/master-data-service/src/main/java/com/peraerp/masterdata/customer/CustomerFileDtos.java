package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.party.PartyAddress;
import com.peraerp.masterdata.party.PartyContact;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Peticiones y respuestas de la ficha de cliente: clasificación, comerciales, contactos, direcciones y notas. */
public final class CustomerFileDtos {
    private CustomerFileDtos() {}

    public record CatalogItemRequest(
            @NotNull CustomerCatalogKind kind,
            @NotBlank @Size(max = 160) String name,
            Boolean active
    ) {}

    public record CatalogItemResponse(UUID id, CustomerCatalogKind kind, String name, boolean active) {
        static CatalogItemResponse from(CustomerCatalogItem item) {
            return new CatalogItemResponse(item.getId(), item.getKind(), item.getName(), item.isActive());
        }
    }

    public record SalespersonRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 160) String name,
            @Email @Size(max = 180) String email,
            @Size(max = 40) String phone,
            @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercentage,
            Boolean active
    ) {}

    public record SalespersonResponse(UUID id, String code, String name, String email, String phone,
                                      BigDecimal commissionPercentage, boolean active) {
        static SalespersonResponse from(Salesperson salesperson) {
            return new SalespersonResponse(salesperson.getId(), salesperson.getCode(), salesperson.getName(),
                    salesperson.getEmail(), salesperson.getPhone(), salesperson.getCommissionPercentage(),
                    salesperson.isActive());
        }
    }

    public record ContactRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 120) String position,
            @Size(max = 40) String phone,
            @Size(max = 40) String mobile,
            @Email @Size(max = 180) String email,
            @Size(max = 300) String notes,
            Boolean primaryContact
    ) {}

    public record ContactResponse(UUID id, String name, String position, String phone, String mobile, String email,
                                  String notes, boolean primaryContact) {
        static ContactResponse from(PartyContact contact) {
            return new ContactResponse(contact.getId(), contact.getName(), contact.getPosition(), contact.getPhone(),
                    contact.getMobile(), contact.getEmail(), contact.getNotes(), contact.isPrimaryContact());
        }
    }

    public record AddressRequest(
            @Size(max = 120) String label,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @Size(max = 20) String postalCode,
            @Size(max = 100) String city,
            @Size(max = 100) String province,
            @Size(max = 100) String country,
            @Size(max = 40) String contactPhone,
            Boolean primaryAddress,
            Boolean active
    ) {}

    public record AddressResponse(UUID id, String type, String label, String line1, String line2, String postalCode,
                                  String city, String province, String country, String contactPhone,
                                  boolean primaryAddress, boolean active) {
        static AddressResponse from(PartyAddress address) {
            return new AddressResponse(address.getId(), address.getType(), address.getLabel(), address.getLine1(),
                    address.getLine2(), address.getPostalCode(), address.getCity(), address.getProvince(),
                    address.getCountry(), address.getContactPhone(), address.isPrimaryAddress(), address.isActive());
        }
    }

    public record NoteRequest(
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 4000) String message,
            Boolean showOnDocuments
    ) {}

    public record NoteResponse(UUID id, String title, String message, boolean showOnDocuments, Instant createdAt,
                               Instant updatedAt) {
        static NoteResponse from(CustomerNote note) {
            return new NoteResponse(note.getId(), note.getTitle(), note.getMessage(), note.isShowOnDocuments(),
                    note.getCreatedAt(), note.getUpdatedAt());
        }
    }
}
