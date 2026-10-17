package com.peraerp.identity.auth;

import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.Company;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.config.DemoProperties;
import com.peraerp.identity.config.JwtProperties;
import com.peraerp.identity.user.AppUser;
import com.peraerp.identity.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccessServiceTest {
    @Mock AppUserRepository users;
    @Mock UserCompanyRepository memberships;
    @Mock CompanyRepository companies;
    @Mock JwtService jwtService;

    @Test
    void withoutDemoTheEndpointDoesNotExist() {
        DemoAccessService service = service(false);

        assertThatThrownBy(service::enter)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(users, memberships, companies, jwtService);
    }

    @Test
    void visitorGetsAMarkedTokenForTheDemoCompany() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(companyId);
        when(company.getCode()).thenReturn("VISITA");
        when(company.getName()).thenReturn("Distribuciones Nevada (demo)");
        when(company.isActive()).thenReturn(true);
        AppUser visitor = mock(AppUser.class);
        when(visitor.getId()).thenReturn(userId);
        when(visitor.isActive()).thenReturn(true);
        UserCompany membership = mock(UserCompany.class);
        when(membership.isActive()).thenReturn(true);
        when(companies.findByCodeIgnoreCase("VISITA")).thenReturn(Optional.of(company));
        when(users.findByUsernameIgnoreCase("visitante")).thenReturn(Optional.of(visitor));
        when(memberships.findByUserIdAndCompanyId(userId, companyId)).thenReturn(Optional.of(membership));
        when(jwtService.issue(visitor, membership, true)).thenReturn("demo-token");

        LoginResponse response = service(true).enter();

        assertThat(response.accessToken()).isEqualTo("demo-token");
        assertThat(response.companies()).singleElement().extracting(CompanyOption::code).isEqualTo("VISITA");
    }

    private DemoAccessService service(boolean enabled) {
        return new DemoAccessService(new DemoProperties(enabled, "VISITA", "Distribuciones Nevada (demo)",
                "B18745638", "visitante", "Visitante de la demo"), users, memberships, companies, jwtService,
                new JwtProperties("secret", "pera-test", Duration.ofHours(2)));
    }
}
