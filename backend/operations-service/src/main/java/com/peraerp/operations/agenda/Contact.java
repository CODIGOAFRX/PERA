package com.peraerp.operations.agenda;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Entrada del listín: una persona o empresa con la que se habla, sea o no cliente. */
@Entity
@Table(name = "contacts", uniqueConstraints = @UniqueConstraint(
        name = "uk_contact_company_id", columnNames = {"company_id", "id"}))
public class Contact extends CompanyScopedEntity {

    @Column(nullable = false, length = 180)
    private String name;
    @Column(length = 180)
    private String organization;
    @Column(length = 40)
    private String phone;
    @Column(length = 40)
    private String mobile;
    @Column(length = 180)
    private String email;
    @Column(length = 300)
    private String address;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(length = 120)
    private String city;
    @Column(length = 120)
    private String region;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "customer_name_snapshot", length = 180)
    private String customerNameSnapshot;
    @Column(length = 1000)
    private String notes;
    @Column(nullable = false)
    private boolean active = true;

    protected Contact() {
    }

    public Contact(UUID companyId) {
        super(companyId);
    }

    public void update(String name, String organization, String phone, String mobile, String email, String address,
                       String postalCode, String city, String region, UUID customerId, String customerName,
                       String notes, boolean active) {
        this.name = name;
        this.organization = organization;
        this.phone = phone;
        this.mobile = mobile;
        this.email = email;
        this.address = address;
        this.postalCode = postalCode;
        this.city = city;
        this.region = region;
        this.customerId = customerId;
        this.customerNameSnapshot = customerId == null ? null : customerName;
        this.notes = notes;
        this.active = active;
    }

    public String getName() { return name; }
    public String getOrganization() { return organization; }
    public String getPhone() { return phone; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getPostalCode() { return postalCode; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot; }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
}
