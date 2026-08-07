package com.peraerp.identity.company;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {
    @Mock CompanyRepository repository;

    @Test
    void normalizesCodeAndNameWhenCreatingCompany() {
        when(repository.existsByCodeIgnoreCase(" demo ")).thenReturn(false);
        when(repository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyService service = new CompanyService(repository);

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

        assertThatThrownBy(() -> new CompanyService(repository)
                .create(new CompanyRequest("DEMO", "Demo", null, true)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reportsMissingCompanyOnUpdate() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CompanyService(repository)
                .update(id, new CompanyRequest("DEMO", "Demo", null, true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
