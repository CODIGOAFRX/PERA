package com.peraerp.identity.user;

import com.peraerp.identity.access.RoleRepository;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
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
}
