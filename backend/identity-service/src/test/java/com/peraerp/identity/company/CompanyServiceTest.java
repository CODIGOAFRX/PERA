package com.peraerp.identity.company;

import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {
    @Mock CompanyRepository repository;
    @Mock CompanySettingsRepository settingsRepository;
    @Mock CurrentCompanyProvider companyProvider;

    private CompanyService service;

    @BeforeEach
    void setUp() {
        service = new CompanyService(repository, settingsRepository, companyProvider);
    }

    @Test
    void normalizesCodeAndNameWhenCreatingCompany() {
        when(repository.existsByCodeIgnoreCase(" demo ")).thenReturn(false);
        when(repository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyResponse response = service.create(new CompanyRequest(" demo ", " PERA Demo ", "B123", true));

        assertThat(response.code()).isEqualTo("DEMO");
        assertThat(response.name()).isEqualTo("PERA Demo");
        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void rejectsDuplicateCompanyCode() {
        when(repository.existsByCodeIgnoreCase("DEMO")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CompanyRequest("DEMO", "Demo", null, true)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reportsMissingCompanyOnUpdate() {
        UUID id = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new CompanyRequest("DEMO", "Demo", null, true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void onlyListsTheCompanyFromTheSignedTenant() {
        UUID companyId = UUID.randomUUID();
        Company company = org.mockito.Mockito.mock(Company.class);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(repository.findById(companyId)).thenReturn(Optional.of(company));

        assertThat(service.findAll()).hasSize(1);

        verify(repository).findById(companyId);
    }

    @Test
    void rejectsUpdatingACompanyOutsideTheSignedTenant() {
        UUID activeCompanyId = UUID.randomUUID();
        UUID foreignCompanyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(activeCompanyId);

        assertThatThrownBy(() -> service.update(foreignCompanyId,
                new CompanyRequest("OTHER", "Otra empresa", null, true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(foreignCompanyId.toString());

        verifyNoInteractions(repository);
    }
}
