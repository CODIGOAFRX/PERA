package com.peraerp.masterdata.packaging;

import com.peraerp.masterdata.catalog.Product;
import com.peraerp.masterdata.catalog.ProductRepository;
import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductPackagingService {
    private final ProductPackagingRepository repository;
    private final PackagingTypeRepository packagingTypeRepository;
    private final ProductRepository productRepository;
    private final CurrentCompanyProvider companyProvider;

    public ProductPackagingService(ProductPackagingRepository repository,
                                   PackagingTypeRepository packagingTypeRepository,
                                   ProductRepository productRepository,
                                   CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.packagingTypeRepository = packagingTypeRepository;
        this.productRepository = productRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public ProductPackagingResponse create(ProductPackagingRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeOptionalCode(request.code());
        validateCodeAvailable(companyId, code);
        requireProduct(request.productId(), companyId, true);
        PackagingType type = requirePackagingType(request.packagingTypeId(), companyId, true);
        validate(request, type);
        validateDefault(companyId, request.productId(), null, request.defaultPackaging(), request.active());
        ProductPackaging packaging = new ProductPackaging(companyId, request.productId(), request.packagingTypeId(), code,
                request.unitsPerPackage(), request.levels(), request.unitsPerLevel(), request.length(),
                request.width(), request.height(), request.grossWeight(), request.defaultPackaging(), request.active());
        return ProductPackagingResponse.from(repository.save(packaging));
    }

    @Transactional(readOnly = true)
    public ProductPackagingResponse findById(UUID id) {
        return ProductPackagingResponse.from(require(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<ProductPackagingResponse> search(String query, UUID productId, UUID packagingTypeId,
                                                 Boolean defaultPackaging, Boolean active, Pageable pageable) {
        return repository.search(companyProvider.requireCompanyId(), normalizeQuery(query), productId,
                packagingTypeId, defaultPackaging, active, pageable).map(ProductPackagingResponse::from);
    }

    @Transactional
    public ProductPackagingResponse update(UUID id, ProductPackagingRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        ProductPackaging packaging = require(id, companyId);
        String code = normalizeOptionalCode(request.code());
        if (!Objects.equals(packaging.getCode(), code)) {
            throw new BusinessRuleException("El código/SKU del embalaje no se puede modificar.");
        }
        if (!packaging.getProductId().equals(request.productId())
                || !packaging.getPackagingTypeId().equals(request.packagingTypeId())) {
            throw new BusinessRuleException("El producto y el tipo de embalaje no se pueden modificar.");
        }
        requireProduct(request.productId(), companyId, request.active());
        PackagingType type = requirePackagingType(request.packagingTypeId(), companyId, request.active());
        validate(request, type);
        validateDefault(companyId, request.productId(), id, request.defaultPackaging(), request.active());
        packaging.update(request.unitsPerPackage(), request.levels(), request.unitsPerLevel(), request.length(),
                request.width(), request.height(), request.grossWeight(), request.defaultPackaging(), request.active());
        return ProductPackagingResponse.from(packaging);
    }

    private ProductPackaging require(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Embalaje de producto", id));
    }

    private Product requireProduct(UUID id, UUID companyId, boolean requireActive) {
        if (id == null) throw new BusinessRuleException("El producto es obligatorio.");
        Product product = productRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo", id));
        if (requireActive && !product.isActive()) {
            throw new BusinessRuleException("No se puede asociar un embalaje activo a un producto inactivo.");
        }
        return product;
    }

    private PackagingType requirePackagingType(UUID id, UUID companyId, boolean requireActive) {
        if (id == null) throw new BusinessRuleException("El tipo de embalaje es obligatorio.");
        PackagingType type = packagingTypeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de embalaje", id));
        if (requireActive && !type.isActive()) {
            throw new BusinessRuleException("No se puede asociar un tipo de embalaje inactivo.");
        }
        return type;
    }

    private void validate(ProductPackagingRequest request, PackagingType type) {
        if (request.unitsPerPackage() == null || request.unitsPerPackage().signum() <= 0) {
            throw new BusinessRuleException("Las unidades por paquete deben ser mayores que cero.");
        }
        if ((request.levels() == null) != (request.unitsPerLevel() == null)) {
            throw new BusinessRuleException("Los niveles y las unidades por nivel deben informarse conjuntamente.");
        }
        if (request.levels() != null) {
            if (request.levels() <= 0 || request.unitsPerLevel().signum() <= 0) {
                throw new BusinessRuleException("Los niveles y sus unidades deben ser mayores que cero.");
            }
            BigDecimal levelUnits = request.unitsPerLevel().multiply(BigDecimal.valueOf(request.levels()));
            if (levelUnits.compareTo(request.unitsPerPackage()) != 0) {
                throw new BusinessRuleException("Las unidades por paquete deben coincidir con niveles por unidades.");
            }
        }
        validateDimensions(request.length(), request.width(), request.height());
        if (request.grossWeight() != null && request.grossWeight().signum() <= 0) {
            throw new BusinessRuleException("El peso bruto debe ser mayor que cero.");
        }
        if (request.grossWeight() != null && type.getTareWeight() != null
                && request.grossWeight().compareTo(type.getTareWeight()) < 0) {
            throw new BusinessRuleException("El peso bruto no puede ser menor que la tara del embalaje.");
        }
        if (request.grossWeight() != null && type.getMaximumWeight() != null
                && request.grossWeight().compareTo(type.getMaximumWeight()) > 0) {
            throw new BusinessRuleException("El peso bruto supera la capacidad del tipo de embalaje.");
        }
    }

    private void validateDimensions(BigDecimal length, BigDecimal width, BigDecimal height) {
        int supplied = (length == null ? 0 : 1) + (width == null ? 0 : 1) + (height == null ? 0 : 1);
        if (supplied != 0 && supplied != 3) {
            throw new BusinessRuleException("Las dimensiones deben indicar largo, ancho y alto conjuntamente.");
        }
        if ((length != null && length.signum() <= 0) || (width != null && width.signum() <= 0)
                || (height != null && height.signum() <= 0)) {
            throw new BusinessRuleException("Las dimensiones deben ser mayores que cero.");
        }
    }

    private void validateDefault(UUID companyId, UUID productId, UUID packagingId,
                                 boolean defaultPackaging, boolean active) {
        if (defaultPackaging && !active) {
            throw new BusinessRuleException("Un embalaje por defecto debe estar activo.");
        }
        if (!defaultPackaging) return;
        boolean duplicate = packagingId == null
                ? repository.existsByCompanyIdAndProductIdAndDefaultPackagingTrueAndActiveTrue(companyId, productId)
                : repository.existsByCompanyIdAndProductIdAndDefaultPackagingTrueAndActiveTrueAndIdNot(
                        companyId, productId, packagingId);
        if (duplicate) {
            throw new BusinessRuleException("El producto ya tiene un embalaje activo por defecto.");
        }
    }

    private void validateCodeAvailable(UUID companyId, String code) {
        if (code != null && repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe un embalaje con el código/SKU " + code + ".");
        }
    }

    private String normalizeOptionalCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeQuery(String query) { return query == null || query.isBlank() ? "" : query.trim(); }
}
