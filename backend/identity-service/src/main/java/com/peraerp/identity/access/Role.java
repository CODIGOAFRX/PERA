package com.peraerp.identity.access;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "uk_role_company_code", columnNames = {"company_id", "code"}))
public class Role extends CompanyScopedEntity {

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    protected Role() {
    }

    public Role(UUID companyId, String code, String name) {
        super(companyId);
        this.code = code;
        this.name = name;
    }

    public void grant(Permission permission) {
        permissions.add(permission);
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Set<Permission> getPermissions() { return Set.copyOf(permissions); }
}
