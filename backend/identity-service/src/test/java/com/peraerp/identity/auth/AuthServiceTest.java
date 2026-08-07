package com.peraerp.identity.auth;

import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.Company;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.config.JwtProperties;
import com.peraerp.identity.user.AppUser;
import com.peraerp.identity.user.AppUserRepository;
import com.peraerp.platform.domain.AuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock AppUserRepository users;
    @Mock UserCompanyRepository memberships;
    @Mock CompanyRepository companies;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, memberships, companies, passwordEncoder, jwtService,
                new JwtProperties("secret", "pera-test", Duration.ofHours(2)));
    }

    @Test
    void issuesTokenForTheOnlyActiveCompany() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        AppUser user = user(userId);
        UserCompany membership = membership(companyId);
        Company company = company(companyId, "DEMO", "PERA Demo");
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(memberships.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(membership));
        when(companies.findById(companyId)).thenReturn(Optional.of(company));
        when(jwtService.issue(user, membership)).thenReturn("signed-token");

        LoginResponse response = service.login(new LoginRequest("admin", "secret", null));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.expiresInSeconds()).isEqualTo(7200);
        assertThat(response.companySelectionRequired()).isFalse();
        assertThat(response.companies()).singleElement().extracting(CompanyOption::code).isEqualTo("DEMO");
    }

    @Test
    void requestsCompanySelectionWhenSeveralMembershipsExist() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        UUID firstCompanyId = UUID.randomUUID();
        UUID secondCompanyId = UUID.randomUUID();
        UserCompany first = membership(firstCompanyId);
        UserCompany second = membership(secondCompanyId);
        Company firstCompany = company(firstCompanyId, "A", "Empresa A");
        Company secondCompany = company(secondCompanyId, "B", "Empresa B");
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(memberships.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(first, second));
        when(companies.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(firstCompany, secondCompany));

        LoginResponse response = service.login(new LoginRequest("admin", "secret", null));

        assertThat(response.companySelectionRequired()).isTrue();
        assertThat(response.accessToken()).isNull();
        assertThat(response.companies()).extracting(CompanyOption::code).containsExactly("A", "B");
        verifyNoInteractions(jwtService);
    }

    @Test
    void rejectsInvalidPasswordWithoutLeakingTheUserState() {
        AppUser user = user(UUID.randomUUID());
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "wrong", null)))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Usuario o contraseña incorrectos.");
        verifyNoInteractions(memberships, jwtService);
    }

    private AppUser user(UUID id) {
        AppUser user = mock(AppUser.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(id);
        when(user.isActive()).thenReturn(true);
        when(user.getPasswordHash()).thenReturn("hash");
        return user;
    }

    private UserCompany membership(UUID companyId) {
        UserCompany membership = mock(UserCompany.class);
        when(membership.getCompanyId()).thenReturn(companyId);
        return membership;
    }

    private Company company(UUID id, String code, String name) {
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(id);
        when(company.getCode()).thenReturn(code);
        when(company.getName()).thenReturn(name);
        when(company.isActive()).thenReturn(true);
        return company;
    }
}
