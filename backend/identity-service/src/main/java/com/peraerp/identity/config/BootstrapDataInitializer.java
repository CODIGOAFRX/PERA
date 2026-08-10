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

import java.util.List;

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
        Company bootstrapCompany = company;
        companySettingsRepository.findByCompanyId(company.getId())
                .orElseGet(() -> companySettingsRepository.save(
                        CompanySettings.defaults(bootstrapCompany.getId(), bootstrapCompany.getName())));

        List<Permission> permissions = TENANT_PERMISSIONS.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseGet(() -> permissionRepository.save(new Permission(code, code))))
                .toList();
        PLATFORM_PERMISSIONS.forEach(code -> permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code))));

        Role admin = roleRepository.findByCompanyIdAndCodeIgnoreCase(company.getId(), "ADMIN")
                .orElseGet(() -> new Role(company.getId(), "ADMIN", "Administrador"));
        permissions.forEach(admin::grant);
        admin = roleRepository.save(admin);

        if (userRepository.findByUsernameIgnoreCase("admin").isEmpty()) {
            AppUser user = userRepository.save(new AppUser("admin", passwordEncoder.encode(adminPassword),
                    "Administrador", "admin@pera-erp.local"));
            UserCompany membership = new UserCompany(user, company.getId());
            membership.assignRole(admin);
            membershipRepository.save(membership);
        }
    }
}
