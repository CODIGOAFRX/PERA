package com.peraerp.identity.user;

import com.peraerp.identity.access.RoleRepository;
import com.peraerp.identity.access.Role;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    @Mock AppUserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserCompanyRepository membershipRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CurrentCompanyProvider companyProvider;

    @Test
    void rejectsCreatingAUserForACompanyOutsideTheSignedTenant() {
        UUID activeCompanyId = UUID.randomUUID();
        UUID foreignCompanyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(activeCompanyId);
        UserAdministrationService service = new UserAdministrationService(userRepository, companyRepository,
                roleRepository, membershipRepository, passwordEncoder, companyProvider);

        CreateUserRequest request = new CreateUserRequest("other-admin", "StrongPassword123!", "Otro admin",
                "admin@example.com", foreignCompanyId, Set.of("ADMIN"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(foreignCompanyId.toString());

        verifyNoInteractions(userRepository, companyRepository, roleRepository, membershipRepository, passwordEncoder);
    }

    @Test
    void preventsAnAdministratorFromAssigningTheProtectedOwnerProfile() {
        UUID companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(userRepository.existsByUsernameIgnoreCase("new-owner")).thenReturn(false);
        when(companyRepository.findById(companyId)).thenReturn(java.util.Optional.of(
                new com.peraerp.identity.company.Company("DEMO", "Demo", null)));
        when(roleRepository.findByCompanyIdAndCodeIgnoreCase(companyId, "OWNER"))
                .thenReturn(java.util.Optional.of(new Role(companyId, "OWNER", "Propietario")));
        when(companyProvider.hasRole("OWNER")).thenReturn(false);
        UserAdministrationService service = new UserAdministrationService(userRepository, companyRepository,
                roleRepository, membershipRepository, passwordEncoder, companyProvider);

        CreateUserRequest request = new CreateUserRequest("new-owner", "StrongPassword123!", "Propietario",
                "owner@example.com", null, Set.of("OWNER"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("propietario");
        verifyNoInteractions(passwordEncoder, membershipRepository);
    }

    @Test
    void preventsChangingTheAccountThatOwnsTheActiveSession() {
        UUID companyId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(companyProvider.requireUserId()).thenReturn(currentUserId);
        UserAdministrationService service = new UserAdministrationService(userRepository, companyRepository,
                roleRepository, membershipRepository, passwordEncoder, companyProvider);

        UpdateUserRequest request = new UpdateUserRequest("Nuevo nombre", null, null, Set.of("ADMIN"), false);

        assertThatThrownBy(() -> service.update(currentUserId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("propia cuenta");
        verifyNoInteractions(userRepository, companyRepository, roleRepository, membershipRepository, passwordEncoder);
    }

    @Test
    void createsAUserWithTheSelectedOperationalProfile() {
        UUID companyId = UUID.randomUUID();
        Role logistics = new Role(companyId, "LOGISTICS", "Logística y procesos");
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(userRepository.existsByUsernameIgnoreCase("warehouse")).thenReturn(false);
        when(companyRepository.findById(companyId)).thenReturn(java.util.Optional.of(
                new com.peraerp.identity.company.Company("DEMO", "Demo", null)));
        when(roleRepository.findByCompanyIdAndCodeIgnoreCase(companyId, "LOGISTICS"))
                .thenReturn(java.util.Optional.of(logistics));
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("encoded-password");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.save(any(com.peraerp.identity.access.UserCompany.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserAdministrationService service = new UserAdministrationService(userRepository, companyRepository,
                roleRepository, membershipRepository, passwordEncoder, companyProvider);

        UserResponse response = service.create(new CreateUserRequest("warehouse", "StrongPassword123!",
                "Almacén", "warehouse@example.com", null, Set.of("LOGISTICS")));

        assertThat(response.username()).isEqualTo("warehouse");
        assertThat(response.roles()).containsExactly("LOGISTICS");
        assertThat(response.active()).isTrue();
        verify(membershipRepository).save(any(com.peraerp.identity.access.UserCompany.class));
    }
}
