package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.packaging.ProductPackagingRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final CurrentCompanyProvider companyProvider;
    private final ProductTypeRepository typeRepository;
    private final ProductGroupRepository groupRepository;
    private final TaxCodeService taxCodeService;
    private final ProductPackagingRepository productPackagingRepository;

    @Autowired
    public ProductService(ProductRepository repository, CurrentCompanyProvider companyProvider,
                          ProductTypeRepository typeRepository, ProductGroupRepository groupRepository,
                          TaxCodeService taxCodeService, ProductPackagingRepository productPackagingRepository) {
        this.repository = repository;
        this.companyProvider = companyProvider;
        this.typeRepository = typeRepository;
        this.groupRepository = groupRepository;
        this.taxCodeService = taxCodeService;
        this.productPackagingRepository = productPackagingRepository;
    }

    public ProductService(ProductRepository repository, CurrentCompanyProvider companyProvider,
                          ProductTypeRepository typeRepository, ProductGroupRepository groupRepository,
                          TaxCodeService taxCodeService) {
        this(repository, companyProvider, typeRepository, groupRepository, taxCodeService, null);
    }

    public ProductService(ProductRepository repository, CurrentCompanyProvider companyProvider,
                          ProductTypeRepository typeRepository, ProductGroupRepository groupRepository) {
        this(repository, companyProvider, typeRepository, groupRepository, null);
    }

    public ProductService(ProductRepository repository, CurrentCompanyProvider companyProvider) {
        this(repository, companyProvider, null, null);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedCode = request.code().trim().toUpperCase();
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, normalizedCode)) {
            throw new BusinessRuleException("Ya existe un artículo con el código " + normalizedCode);
        }
        UUID productTypeId = resolveProductType(companyId, request.productTypeId(), request.productGroupId());
        BigDecimal effectiveTaxRate = resolveTaxRate(companyId, request.taxCodeId(), request.taxRate());
        Product product = new Product(companyId, normalizedCode, request.name().trim(),
                request.description(), productTypeId, request.productGroupId(), request.taxCodeId(),
                request.familyId(), request.categoryId(), request.unitOfMeasure(), request.basePrice(),
                effectiveTaxRate);
        return ProductResponse.from(repository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        return ProductResponse.from(repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Artículo", id)));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String query, Pageable pageable) {
        String normalized = query == null || query.isBlank() ? "" : query.trim();
        return repository.search(companyProvider.requireCompanyId(), normalized, pageable).map(ProductResponse::from);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Artículo", id));
        if (!product.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código del artículo no se puede modificar.");
        }
        UUID productTypeId = resolveProductType(product.getCompanyId(), request.productTypeId(),
                request.productGroupId());
        BigDecimal effectiveTaxRate = resolveTaxRate(product.getCompanyId(), request.taxCodeId(), request.taxRate());
        if (product.isActive() && !request.active() && productPackagingRepository != null
                && productPackagingRepository.existsByCompanyIdAndProductIdAndActiveTrue(
                product.getCompanyId(), product.getId())) {
            throw new BusinessRuleException("No se puede desactivar un artículo con embalajes activos.");
        }
        product.update(request.name().trim(), request.description(), productTypeId, request.productGroupId(),
                request.taxCodeId(), request.familyId(), request.categoryId(), request.unitOfMeasure(),
                request.basePrice(), effectiveTaxRate, request.active());
        return ProductResponse.from(product);
    }

    private UUID resolveProductType(UUID companyId, UUID requestedTypeId, UUID productGroupId) {
        if (productGroupId != null) {
            requireClassificationRepositories();
            ProductGroup group = groupRepository.findByIdAndCompanyId(productGroupId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Grupo de producto", productGroupId));
            UUID groupTypeId = group.getProductTypeId();
            typeRepository.findByIdAndCompanyId(groupTypeId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de producto", groupTypeId));
            if (requestedTypeId != null && !requestedTypeId.equals(groupTypeId)) {
                throw new BusinessRuleException("El grupo seleccionado no pertenece al tipo de producto indicado.");
            }
            return groupTypeId;
        }
        if (requestedTypeId != null) {
            requireClassificationRepositories();
            typeRepository.findByIdAndCompanyId(requestedTypeId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tipo de producto", requestedTypeId));
        }
        return requestedTypeId;
    }

    private void requireClassificationRepositories() {
        if (typeRepository == null || groupRepository == null) {
            throw new IllegalStateException("Los repositorios de clasificación no están configurados.");
        }
    }

    private BigDecimal resolveTaxRate(UUID companyId, UUID taxCodeId, BigDecimal requestedTaxRate) {
        if (taxCodeId != null) {
            if (taxCodeService == null) {
                throw new IllegalStateException("El catálogo fiscal no está configurado.");
            }
            return taxCodeService.requireApplicable(taxCodeId, companyId, LocalDate.now()).getPercentage();
        }
        if (requestedTaxRate == null || requestedTaxRate.signum() < 0
                || requestedTaxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessRuleException("El porcentaje fiscal del artículo debe estar entre 0 y 100.");
        }
        return requestedTaxRate;
    }
}
