package com.peraerp.identity.config;

import com.peraerp.identity.access.Permission;
import com.peraerp.identity.access.PermissionRepository;
import com.peraerp.identity.access.Role;
import com.peraerp.identity.access.RoleRepository;
import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.Company;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.company.CompanySettings;
import com.peraerp.identity.company.CompanySettingsRepository;
import com.peraerp.identity.user.AppUser;
import com.peraerp.identity.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BootstrapDataInitializer implements ApplicationRunner {

    private static final List<String> TENANT_PERMISSIONS = List.of(
            "companies:manage", "users:manage", "customers:read", "customers:write",
            "suppliers:read", "suppliers:write", "products:read", "products:write",
            "documents:read", "documents:write", "quotes:read", "quotes:write",
            "finance:read", "finance:write",
            "company-settings:read", "company-settings:write", "numbering:read", "numbering:write",
            "currencies:read", "currencies:write", "taxes:read", "taxes:write",
            "pricing:read", "pricing:write", "packaging:read", "packaging:write",
            "workflows:read", "workflows:manage", "workflows:execute",
            "logistics:read", "logistics:write", "logistics:manage", "logistics:dispatch",
            "freight:read", "freight:write",
            "history:read", "history:export", "alerts:read", "alerts:manage", "alerts:acknowledge",
            "license:read", "license:manage"
    );
    private static final List<String> PLATFORM_PERMISSIONS = List.of("platform:companies:manage");
    private static final List<RoleDefinition> ROLE_DEFINITIONS = List.of(
            new RoleDefinition("OWNER", "Propietario", TENANT_PERMISSIONS),
            new RoleDefinition("ADMIN", "Administrador", TENANT_PERMISSIONS),
            new RoleDefinition("ECONOMY", "Economía", List.of(
                    "customers:read", "products:read", "documents:read", "documents:write",
                    "quotes:read", "quotes:write", "finance:read", "finance:write",
                    "company-settings:read", "numbering:read", "currencies:read", "pricing:read", "taxes:read")),
            new RoleDefinition("LOGISTICS", "Logística y procesos", List.of(
                    "suppliers:read", "suppliers:write", "products:read",
                    "company-settings:read", "currencies:read", "workflows:read", "workflows:manage",
                    "workflows:execute", "logistics:read", "logistics:write", "logistics:manage",
                    "logistics:dispatch", "freight:read", "freight:write")),
            new RoleDefinition("CATALOG", "Catálogo y maestros", List.of(
                    "customers:read", "products:read", "products:write", "company-settings:read",
                    "currencies:read", "taxes:read", "taxes:write", "pricing:read", "pricing:write",
                    "packaging:read", "packaging:write"))
    );

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final AppUserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserCompanyRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${pera.bootstrap.admin-password}")
    private String adminPassword;

    public BootstrapDataInitializer(CompanyRepository companyRepository,
                                    CompanySettingsRepository companySettingsRepository,
                                    AppUserRepository userRepository,
                                    PermissionRepository permissionRepository, RoleRepository roleRepository,
                                    UserCompanyRepository membershipRepository, PasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Company company = companyRepository.findByCodeIgnoreCase("DEMO")
                .orElseGet(() -> companyRepository.save(new Company("DEMO", "PERA ERP Demo", "B00000000")));
        companySettingsRepository.findByCompanyId(company.getId())
                .orElseGet(() -> companySettingsRepository.save(CompanySettings.defaults(company.getId(), company.getName())));

        Map<String, Permission> permissions = new LinkedHashMap<>();
        TENANT_PERMISSIONS.forEach(code -> permissions.put(code, permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code)))));
        PLATFORM_PERMISSIONS.forEach(code -> permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code))));

        Map<String, Role> roles = new LinkedHashMap<>();
        for (RoleDefinition definition : ROLE_DEFINITIONS) {
            Role role = roleRepository.findByCompanyIdAndCodeIgnoreCase(company.getId(), definition.code())
                    .orElseGet(() -> new Role(company.getId(), definition.code(), definition.name()));
            role.replacePermissions(definition.permissions().stream().map(permissions::get).toList());
            roles.put(definition.code(), roleRepository.save(role));
        }

        ensureDemoUser(company, roles.get("OWNER"), "admin", "Propietario", "admin@pera-erp.local", true);
        ensureDemoUser(company, roles.get("ADMIN"), "administracion", "Administración", "administracion@pera-erp.local", false);
        ensureDemoUser(company, roles.get("ECONOMY"), "economia", "Equipo de economía", "economia@pera-erp.local", false);
        ensureDemoUser(company, roles.get("LOGISTICS"), "logistica", "Equipo de logística", "logistica@pera-erp.local", false);
        ensureDemoUser(company, roles.get("CATALOG"), "catalogo", "Equipo de catálogo", "catalogo@pera-erp.local", false);
    }

    private void ensureDemoUser(Company company, Role role, String username, String displayName, String email,
                                boolean synchronizeRole) {
        AppUser user = userRepository.findByUsernameIgnoreCase(username)
                .orElseGet(() -> userRepository.save(new AppUser(username, passwordEncoder.encode(adminPassword),
                        displayName, email)));
        var existing = membershipRepository.findByUserIdAndCompanyId(user.getId(), company.getId());
        UserCompany membership = existing.orElseGet(() -> new UserCompany(user, company.getId()));
        if (existing.isEmpty() || synchronizeRole) {
            membership.replaceRoles(Set.of(role));
            membership.setActive(true);
            membershipRepository.save(membership);
        }
    }

    private record RoleDefinition(String code, String name, List<String> permissions) {
    }
}
