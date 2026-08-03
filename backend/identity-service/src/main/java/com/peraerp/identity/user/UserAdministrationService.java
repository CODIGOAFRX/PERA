package com.peraerp.identity.user;

import com.peraerp.identity.access.Role;
import com.peraerp.identity.access.RoleRepository;
import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserAdministrationService {

    private final AppUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final UserCompanyRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdministrationService(AppUserRepository userRepository, CompanyRepository companyRepository,
                                     RoleRepository roleRepository, UserCompanyRepository membershipRepository,
                                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new BusinessRuleException("El nombre de usuario ya existe.");
        }
        companyRepository.findById(request.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", request.companyId()));

        Set<Role> roles = new LinkedHashSet<>();
        for (String roleCode : request.roleCodes()) {
            roles.add(roleRepository.findByCompanyIdAndCodeIgnoreCase(request.companyId(), roleCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Rol", roleCode)));
        }

        AppUser user = userRepository.save(new AppUser(request.username().trim(),
                passwordEncoder.encode(request.password()), request.displayName().trim(), request.email()));
        UserCompany membership = new UserCompany(user, request.companyId());
        roles.forEach(membership::assignRole);
        membershipRepository.save(membership);

        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                membership.getCompanyId(), roles.stream().map(Role::getCode).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                user.isActive());
    }
}
