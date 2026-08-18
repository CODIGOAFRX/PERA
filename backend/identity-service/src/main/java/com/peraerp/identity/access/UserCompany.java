package com.peraerp.identity.access;

import com.peraerp.identity.user.AppUser;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_companies", uniqueConstraints = @UniqueConstraint(name = "uk_user_company", columnNames = {"user_id", "company_id"}))
public class UserCompany extends CompanyScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_company_roles",
            joinColumns = @JoinColumn(name = "user_company_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected UserCompany() {
    }

    public UserCompany(AppUser user, UUID companyId) {
        super(companyId);
        this.user = user;
    }

    public void assignRole(Role role) {
        roles.add(role);
    }

    public void replaceRoles(Collection<Role> nextRoles) {
        roles.clear();
        roles.addAll(nextRoles);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public AppUser getUser() { return user; }
    public boolean isActive() { return active; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
}
