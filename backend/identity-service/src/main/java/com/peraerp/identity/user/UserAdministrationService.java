package com.peraerp.identity.user;

import com.peraerp.identity.access.Role;
import com.peraerp.identity.access.RoleRepository;
import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAdministrationService {

    private final AppUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final UserCompanyRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentCompanyProvider companyProvider;

    public UserAdministrationService(AppUserRepository userRepository, CompanyRepository companyRepository,
                                     RoleRepository roleRepository, UserCompanyRepository membershipRepository,
                                     PasswordEncoder passwordEncoder, CurrentCompanyProvider companyProvider) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return membershipRepository.findAllByCompanyIdOrderByCreatedAtAsc(companyProvider.requireCompanyId())
                .stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (request.companyId() != null && !companyId.equals(request.companyId())) {
            throw new ResourceNotFoundException("Empresa", request.companyId());
        }
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new BusinessRuleException("El nombre de usuario ya existe.");
        }
        companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", companyId));

        Set<Role> roles = resolveRoles(companyId, request.roleCodes());
        AppUser user = userRepository.save(new AppUser(request.username().trim(),
                passwordEncoder.encode(request.password()), request.displayName().trim(), nullable(request.email())));
        UserCompany membership = new UserCompany(user, companyId);
        membership.replaceRoles(roles);
        membershipRepository.save(membership);
        return UserResponse.from(membership);
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (companyProvider.requireUserId().equals(userId)) {
            throw new BusinessRuleException("No puedes modificar tu propia cuenta desde esta sesión.");
        }
        UserCompany membership = membershipRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        if (hasRole(membership, "OWNER") && !companyProvider.hasRole("OWNER")) {
            throw new BusinessRuleException("Solo el propietario puede modificar otra cuenta propietaria.");
        }

        Set<Role> roles = resolveRoles(companyId, request.roleCodes());
        AppUser user = membership.getUser();
        user.updateProfile(request.displayName().trim(), nullable(request.email()));
        if (request.password() != null && !request.password().isBlank()) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        membership.replaceRoles(roles);
        membership.setActive(request.active());
        return UserResponse.from(membership);
    }

    private Set<Role> resolveRoles(UUID companyId, Set<String> roleCodes) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            Role role = roleRepository.findByCompanyIdAndCodeIgnoreCase(companyId, roleCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Rol", roleCode));
            if (role.getCode().equalsIgnoreCase("OWNER") && !companyProvider.hasRole("OWNER")) {
                throw new BusinessRuleException("Solo el propietario puede asignar el perfil propietario.");
            }
            roles.add(role);
        }
        return roles;
    }

    private boolean hasRole(UserCompany membership, String roleCode) {
        return membership.getRoles().stream().anyMatch(role -> role.getCode().equalsIgnoreCase(roleCode));
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
