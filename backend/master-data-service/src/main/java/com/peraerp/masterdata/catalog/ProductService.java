package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final CurrentCompanyProvider companyProvider;
    public ProductService(ProductRepository repository, CurrentCompanyProvider companyProvider) {
        this.repository = repository; this.companyProvider = companyProvider;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code())) {
            throw new BusinessRuleException("Ya existe un artículo con el código " + request.code());
        }
        Product product = new Product(companyId, request.code().trim().toUpperCase(), request.name().trim(),
                request.description(), request.productTypeId(), request.familyId(), request.categoryId(),
                request.unitOfMeasure(), request.basePrice(), request.taxRate());
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
        if (!product.getCode().equalsIgnoreCase(request.code())) {
            throw new BusinessRuleException("El código del artículo no se puede modificar.");
        }
        product.update(request.name().trim(), request.description(), request.productTypeId(), request.familyId(),
                request.categoryId(), request.unitOfMeasure(), request.basePrice(), request.taxRate(), request.active());
        return ProductResponse.from(product);
    }
}
