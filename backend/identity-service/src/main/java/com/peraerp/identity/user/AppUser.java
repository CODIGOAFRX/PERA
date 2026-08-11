package com.peraerp.identity.user;

import com.peraerp.platform.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
public class AppUser extends AuditableEntity {

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(length = 180)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String displayName, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
    }

    public void updateProfile(String displayName, String email) {
        this.displayName = displayName;
        this.email = email;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
}
