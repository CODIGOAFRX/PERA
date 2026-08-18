package com.peraerp.finance.currency;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrencyServiceTest {

    private CurrencyRepository repository;
    private CurrencyService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        repository = mock(CurrencyRepository.class);
        CurrentCompanyProvider companyProvider = mock(CurrentCompanyProvider.class);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        service = new CurrencyService(repository, companyProvider);
    }

    @Test
    void createsNormalizedBaseCurrencyAndClearsPreviousBase() {
        when(repository.save(any(CurrencyDefinition.class))).thenAnswer(invocation -> {
            CurrencyDefinition currency = invocation.getArgument(0);
            ReflectionTestUtils.setField(currency, "id", UUID.randomUUID());
            return currency;
        });

        CurrencyResponse response = service.create(new CurrencyRequest(" eur ", "Euro", "€", 2, true, true));

        assertThat(response.code()).isEqualTo("EUR");
        assertThat(response.baseCurrency()).isTrue();
        verify(repository).clearBaseCurrency(any(UUID.class), any(UUID.class));
    }

    @Test
    void rejectsDuplicateAndInactiveBaseCurrency() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "EUR")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CurrencyRequest("EUR", "Euro", "€", 2, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe");

        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "EUR")).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CurrencyRequest("EUR", "Euro", "€", 2, true, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("debe estar activa");
    }

    @Test
    void findByIdCannotCrossTenantBoundary() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsChangingIsoCode() {
        UUID id = UUID.randomUUID();
        CurrencyDefinition currency = new CurrencyDefinition(companyId, "EUR", "Euro", "€", 2, true, true);
        ReflectionTestUtils.setField(currency, "id", id);
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(currency));

        assertThatThrownBy(() -> service.update(id,
                new CurrencyRequest("USD", "Dólar", "$", 2, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");
    }
}
