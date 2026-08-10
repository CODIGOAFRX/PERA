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
public class ProductClassificationService {
    private final ProductNatureRepository natureRepository;
    private final ProductSupertypeRepository supertypeRepository;
    private final ProductTypeRepository typeRepository;
    private final ProductGroupRepository groupRepository;
    private final ProductRepository productRepository;
    private final CurrentCompanyProvider companyProvider;

    public ProductClassificationService(ProductNatureRepository natureRepository,
                                        ProductSupertypeRepository supertypeRepository,
                                        ProductTypeRepository typeRepository,
                                        ProductGroupRepository groupRepository,
                                        ProductRepository productRepository,
                                        CurrentCompanyProvider companyProvider) {
        this.natureRepository = natureRepository;
        this.supertypeRepository = supertypeRepository;
        this.typeRepository = typeRepository;
        this.groupRepository = groupRepository;
        this.productRepository = productRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public ProductNatureResponse createNature(ProductNatureRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        rejectDuplicate(natureRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code), "naturaleza", code);
        return ProductNatureResponse.from(natureRepository.save(
                new ProductNature(companyId, code, normalizeName(request.name()), request.active())));
    }

    @Transactional(readOnly = true)
    public ProductNatureResponse findNature(UUID id) {
        return ProductNatureResponse.from(requireNature(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<ProductNatureResponse> searchNatures(String query, Boolean active, Pageable pageable) {
        return natureRepository.search(companyProvider.requireCompanyId(), normalizeQuery(query), active, pageable)
                .map(ProductNatureResponse::from);
    }

    @Transactional
    public ProductNatureResponse updateNature(UUID id, ProductNatureRequest request) {
        ProductNature nature = requireNature(id, companyProvider.requireCompanyId());
        requireImmutableCode(nature.getCode(), request.code(), "naturaleza");
        nature.update(normalizeName(request.name()), request.active());
        return ProductNatureResponse.from(nature);
    }

    @Transactional
    public ProductSupertypeResponse createSupertype(ProductSupertypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        requireNature(request.natureId(), companyId);
        String code = normalizeCode(request.code());
        rejectDuplicate(supertypeRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code), "supertipo", code);
        return ProductSupertypeResponse.from(supertypeRepository.save(new ProductSupertype(companyId,
                request.natureId(), code, normalizeName(request.name()), request.active())));
    }

    @Transactional(readOnly = true)
    public ProductSupertypeResponse findSupertype(UUID id) {
        return ProductSupertypeResponse.from(requireSupertype(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<ProductSupertypeResponse> searchSupertypes(String query, UUID natureId, Boolean active,
                                                           Pageable pageable) {
        return supertypeRepository.search(companyProvider.requireCompanyId(), normalizeQuery(query), natureId,
                active, pageable).map(ProductSupertypeResponse::from);
    }

    @Transactional
    public ProductSupertypeResponse updateSupertype(UUID id, ProductSupertypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        ProductSupertype supertype = requireSupertype(id, companyId);
        requireImmutableCode(supertype.getCode(), request.code(), "supertipo");
        requireNature(request.natureId(), companyId);
        supertype.update(request.natureId(), normalizeName(request.name()), request.active());
        return ProductSupertypeResponse.from(supertype);
    }

    @Transactional
    public ProductTypeResponse createType(ProductTypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        requireSupertype(request.supertypeId(), companyId);
        String code = normalizeCode(request.code());
        rejectDuplicate(typeRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code), "tipo", code);
        return ProductTypeResponse.from(typeRepository.save(new ProductType(companyId, request.supertypeId(),
                code, normalizeName(request.name()), request.active())));
    }

    @Transactional(readOnly = true)
    public ProductTypeResponse findType(UUID id) {
        return ProductTypeResponse.from(requireType(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<ProductTypeResponse> searchTypes(String query, UUID supertypeId, Boolean active, Pageable pageable) {
        return typeRepository.search(companyProvider.requireCompanyId(), normalizeQuery(query), supertypeId,
                active, pageable).map(ProductTypeResponse::from);
    }

    @Transactional
    public ProductTypeResponse updateType(UUID id, ProductTypeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        ProductType type = requireType(id, companyId);
        requireImmutableCode(type.getCode(), request.code(), "tipo");
        requireSupertype(request.supertypeId(), companyId);
        type.update(request.supertypeId(), normalizeName(request.name()), request.active());
        return ProductTypeResponse.from(type);
    }

    @Transactional
    public ProductGroupResponse createGroup(ProductGroupRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        requireType(request.productTypeId(), companyId);
        String code = normalizeCode(request.code());
        rejectDuplicate(groupRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code), "grupo", code);
        return ProductGroupResponse.from(groupRepository.save(new ProductGroup(companyId, request.productTypeId(),
                code, normalizeName(request.name()), request.active())));
    }

    @Transactional(readOnly = true)
    public ProductGroupResponse findGroup(UUID id) {
        return ProductGroupResponse.from(requireGroup(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<ProductGroupResponse> searchGroups(String query, UUID productTypeId, Boolean active,
                                                   Pageable pageable) {
        return groupRepository.search(companyProvider.requireCompanyId(), normalizeQuery(query), productTypeId,
                active, pageable).map(ProductGroupResponse::from);
    }

    @Transactional
    public ProductGroupResponse updateGroup(UUID id, ProductGroupRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        ProductGroup group = requireGroup(id, companyId);
        requireImmutableCode(group.getCode(), request.code(), "grupo");
        requireType(request.productTypeId(), companyId);
        if (!group.getProductTypeId().equals(request.productTypeId())
                && productRepository.existsByCompanyIdAndProductGroupId(companyId, id)) {
            throw new BusinessRuleException(
                    "No se puede cambiar el tipo de un grupo que ya está asignado a artículos.");
        }
        group.update(request.productTypeId(), normalizeName(request.name()), request.active());
        return ProductGroupResponse.from(group);
    }

    private ProductNature requireNature(UUID id, UUID companyId) {
        return natureRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Naturaleza de producto", id));
    }

    private ProductSupertype requireSupertype(UUID id, UUID companyId) {
        return supertypeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Supertipo de producto", id));
    }

    private ProductType requireType(UUID id, UUID companyId) {
        return typeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de producto", id));
    }

    private ProductGroup requireGroup(UUID id, UUID companyId) {
        return groupRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de producto", id));
    }

    private String normalizeCode(String code) { return code.trim().toUpperCase(); }
    private String normalizeName(String name) { return name.trim(); }
    private String normalizeQuery(String query) { return query == null || query.isBlank() ? "" : query.trim(); }

    private void rejectDuplicate(boolean duplicate, String resource, String code) {
        if (duplicate) throw new BusinessRuleException("Ya existe " + article(resource) + resource +
                " de producto con el código " + code + ".");
    }

    private String article(String resource) { return "naturaleza".equals(resource) ? "una " : "un "; }

    private void requireImmutableCode(String currentCode, String requestedCode, String resource) {
        if (!currentCode.equalsIgnoreCase(requestedCode.trim())) {
            throw new BusinessRuleException("El código de " + resource + " de producto no se puede modificar.");
        }
    }
}
