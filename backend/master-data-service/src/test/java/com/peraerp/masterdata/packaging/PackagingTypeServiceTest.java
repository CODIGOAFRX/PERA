package com.peraerp.masterdata.packaging;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackagingTypeServiceTest {
    @Mock PackagingTypeRepository repository;
    @Mock ProductPackagingRepository productPackagingRepository;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private PackagingTypeService service;

    @BeforeEach
    void setUp() {
        service = new PackagingTypeService(repository, productPackagingRepository, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsNormalizedPackagingType() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "BOX")).thenReturn(false);
        when(repository.save(any(PackagingType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PackagingTypeResponse response = service.create(request(" box ", true, true,
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("1"), new BigDecimal("15")));

        assertThat(response.code()).isEqualTo("BOX");
        assertThat(response.name()).isEqualTo("Caja");
        assertThat(response.description()).isEqualTo("Embalaje estándar");
        assertThat(response.returnable()).isTrue();
    }

    @Test
    void rejectsPartialDimensionTriplets() {
        PackagingTypeRequest request = new PackagingTypeRequest("BOX", "Caja", null,
                BigDecimal.ONE, null, BigDecimal.ONE, null, null, null, null, null, null, false, true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("conjuntamente");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsExternalDimensionsSmallerThanInternal() {
        PackagingTypeRequest request = new PackagingTypeRequest("BOX", "Caja", null,
                new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("6"),
                new BigDecimal("9"), new BigDecimal("9"), new BigDecimal("7"),
                null, null, null, false, true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("externas");
    }

    @Test
    void rejectsMaximumWeightBelowTare() {
        assertThatThrownBy(() -> service.create(request("BOX", false, true,
                new BigDecimal("10"), new BigDecimal("12"), new BigDecimal("5"), new BigDecimal("4"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tara");
    }

    @Test
    void keepsCodeImmutable() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(type("BOX", true)));

        assertThatThrownBy(() -> service.update(id, request("OTHER", false, true,
                null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");
    }

    @Test
    void blocksDeactivationWhileActiveProductPackagingUsesType() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(type("BOX", true)));
        when(productPackagingRepository.existsByCompanyIdAndPackagingTypeIdAndActiveTrue(companyId, id))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(id, request("BOX", false, false,
                null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("embalajes").hasMessageContaining("activos");
    }

    @Test
    void hidesTypeFromAnotherTenant() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchesWithTenantAndFilters() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.search(companyId, "caja", true, true, pageable))
                .thenReturn(new PageImpl<>(List.of(type("BOX", true)), pageable, 1));

        assertThat(service.search(" caja ", true, true, pageable).getContent()).hasSize(1);
        verify(repository).search(companyId, "caja", true, true, pageable);
    }

    private PackagingTypeRequest request(String code, boolean returnable, boolean active,
                                         BigDecimal internalLength, BigDecimal externalLength,
                                         BigDecimal tareWeight, BigDecimal maximumWeight) {
        BigDecimal internalWidth = internalLength == null ? null : new BigDecimal("8");
        BigDecimal internalHeight = internalLength == null ? null : new BigDecimal("6");
        BigDecimal externalWidth = externalLength == null ? null : new BigDecimal("10");
        BigDecimal externalHeight = externalLength == null ? null : new BigDecimal("7");
        return new PackagingTypeRequest(code, " Caja ", " Embalaje estándar ", internalLength, internalWidth,
                internalHeight, externalLength, externalWidth, externalHeight, tareWeight, maximumWeight,
                new BigDecimal("100"), returnable, active);
    }

    private PackagingType type(String code, boolean active) {
        return new PackagingType(companyId, code, "Caja", null, null, null, null, null, null, null,
                null, null, null, false, active);
    }
}
