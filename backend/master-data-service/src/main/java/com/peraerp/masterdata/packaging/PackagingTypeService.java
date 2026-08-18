package com.peraerp.masterdata.packaging;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
public class PackagingTypeService {
    private final PackagingTypeRepository repository;
    private final ProductPackagingRepository productPackagingRepository;
    private final CurrentCompanyProvider companyProvider;

    public PackagingTypeService(PackagingTypeRepository repository,
                                ProductPackagingRepository productPackagingRepository,
                                CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.productPackagingRepository = productPackagingRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public PackagingTypeResponse create(PackagingTypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        validate(request);
        String code = normalizeCode(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe un tipo de embalaje con el código " + code + ".");
        }
        PackagingType type = new PackagingType(companyId, code, request.name().trim(), trimToNull(request.description()),
                request.internalLength(), request.internalWidth(), request.internalHeight(),
                request.externalLength(), request.externalWidth(), request.externalHeight(), request.tareWeight(),
                request.maximumWeight(), request.maximumVolume(), request.returnable(), request.active());
        return PackagingTypeResponse.from(repository.save(type));
    }

    @Transactional(readOnly = true)
    public PackagingTypeResponse findById(UUID id) {
        return PackagingTypeResponse.from(require(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<PackagingTypeResponse> search(String query, Boolean returnable, Boolean active, Pageable pageable) {
        return repository.search(companyProvider.requireCompanyId(), normalizeQuery(query), returnable, active,
                pageable).map(PackagingTypeResponse::from);
    }

    @Transactional
    public PackagingTypeResponse update(UUID id, PackagingTypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        PackagingType type = require(id, companyId);
        validate(request);
        if (!type.getCode().equalsIgnoreCase(normalizeCode(request.code()))) {
            throw new BusinessRuleException("El código del tipo de embalaje no se puede modificar.");
        }
        if (type.isActive() && !request.active()
                && productPackagingRepository.existsByCompanyIdAndPackagingTypeIdAndActiveTrue(companyId, id)) {
            throw new BusinessRuleException("No se puede desactivar un tipo usado por embalajes de producto activos.");
        }
        type.update(request.name().trim(), trimToNull(request.description()), request.internalLength(),
                request.internalWidth(), request.internalHeight(), request.externalLength(), request.externalWidth(),
                request.externalHeight(), request.tareWeight(), request.maximumWeight(), request.maximumVolume(),
                request.returnable(), request.active());
        return PackagingTypeResponse.from(type);
    }

    PackagingType require(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de embalaje", id));
    }

    private void validate(PackagingTypeRequest request) {
        validateDimensions("Las dimensiones internas", request.internalLength(), request.internalWidth(),
                request.internalHeight());
        validateDimensions("Las dimensiones externas", request.externalLength(), request.externalWidth(),
                request.externalHeight());
        validatePositive(request.tareWeight(), "El peso de tara");
        validatePositive(request.maximumWeight(), "El peso máximo");
        validatePositive(request.maximumVolume(), "El volumen máximo");
        if (request.internalLength() != null && request.externalLength() != null
                && (request.externalLength().compareTo(request.internalLength()) < 0
                || request.externalWidth().compareTo(request.internalWidth()) < 0
                || request.externalHeight().compareTo(request.internalHeight()) < 0)) {
            throw new BusinessRuleException("Las dimensiones externas no pueden ser menores que las internas.");
        }
        if (request.tareWeight() != null && request.maximumWeight() != null
                && request.maximumWeight().compareTo(request.tareWeight()) < 0) {
            throw new BusinessRuleException("El peso máximo no puede ser menor que la tara.");
        }
    }

    private void validateDimensions(String label, BigDecimal length, BigDecimal width, BigDecimal height) {
        int supplied = (length == null ? 0 : 1) + (width == null ? 0 : 1) + (height == null ? 0 : 1);
        if (supplied != 0 && supplied != 3) {
            throw new BusinessRuleException(label + " deben indicar largo, ancho y alto conjuntamente.");
        }
        validatePositive(length, label);
        validatePositive(width, label);
        validatePositive(height, label);
    }

    private void validatePositive(BigDecimal value, String label) {
        if (value != null && value.signum() <= 0) {
            throw new BusinessRuleException(label + " debe ser mayor que cero.");
        }
    }

    private String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }
    private String normalizeQuery(String query) { return query == null || query.isBlank() ? "" : query.trim(); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
