package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.customer.CustomerProfileRepository;
import com.peraerp.masterdata.customer.CustomerProfile;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class PricingReferenceService {
    private final CustomerProfileRepository customerRepository;
    private final ProductNatureRepository natureRepository;
    private final ProductSupertypeRepository supertypeRepository;
    private final ProductTypeRepository typeRepository;
    private final ProductGroupRepository groupRepository;
    private final ProductRepository productRepository;

    public PricingReferenceService(CustomerProfileRepository customerRepository,
                                   ProductNatureRepository natureRepository,
                                   ProductSupertypeRepository supertypeRepository,
                                   ProductTypeRepository typeRepository,
                                   ProductGroupRepository groupRepository,
                                   ProductRepository productRepository) {
        this.customerRepository = customerRepository;
        this.natureRepository = natureRepository;
        this.supertypeRepository = supertypeRepository;
        this.typeRepository = typeRepository;
        this.groupRepository = groupRepository;
        this.productRepository = productRepository;
    }

    public void validateTariffTarget(UUID companyId, TariffRequest request) {
        if (request.customerId() != null) requireCustomer(request.customerId(), companyId);
        switch (request.scope()) {
            case GENERAL -> requireOnly(request, null, null, null, null, null, null);
            case CUSTOMER -> {
                if (request.customerId() == null) {
                    throw new BusinessRuleException("Una tarifa de cliente requiere customerId.");
                }
                requireOnly(request, request.customerId(), null, null, null, null, null);
            }
            case PRODUCT_NATURE -> {
                requireNature(request.productNatureId(), companyId);
                requireOnly(request, request.customerId(), request.productNatureId(), null, null, null, null);
            }
            case PRODUCT_SUPERTYPE -> {
                requireSupertype(request.productSupertypeId(), companyId);
                requireOnly(request, request.customerId(), null, request.productSupertypeId(), null, null, null);
            }
            case PRODUCT_TYPE -> {
                requireType(request.productTypeId(), companyId);
                requireOnly(request, request.customerId(), null, null, request.productTypeId(), null, null);
            }
            case PRODUCT_GROUP -> {
                requireGroup(request.productGroupId(), companyId);
                requireOnly(request, request.customerId(), null, null, null, request.productGroupId(), null);
            }
            case PRODUCT -> {
                requireProduct(request.productId(), companyId);
                requireOnly(request, request.customerId(), null, null, null, null, request.productId());
            }
        }
    }

    public void validateRuleTarget(UUID companyId, PricingRuleRequest request) {
        if (request.customerId() != null) requireCustomer(request.customerId(), companyId);
        switch (request.targetType()) {
            case PRODUCT_NATURE -> {
                requireNature(request.productNatureId(), companyId);
                requireOnly(request, request.productNatureId(), null, null, null, null);
            }
            case PRODUCT_SUPERTYPE -> {
                requireSupertype(request.productSupertypeId(), companyId);
                requireOnly(request, null, request.productSupertypeId(), null, null, null);
            }
            case PRODUCT_TYPE -> {
                requireType(request.productTypeId(), companyId);
                requireOnly(request, null, null, request.productTypeId(), null, null);
            }
            case PRODUCT_GROUP -> {
                requireGroup(request.productGroupId(), companyId);
                requireOnly(request, null, null, null, request.productGroupId(), null);
            }
            case PRODUCT -> {
                requireProduct(request.productId(), companyId);
                requireOnly(request, null, null, null, null, request.productId());
            }
        }
    }

    public PricingContext resolveContext(UUID companyId, PricingResolveRequest request) {
        CustomerProfile customer = request.customerId() == null ? null : requireCustomer(request.customerId(), companyId);

        UUID productId = request.productId();
        UUID groupId = request.productGroupId();
        UUID typeId = request.productTypeId();
        UUID supertypeId = request.productSupertypeId();
        UUID natureId = request.productNatureId();

        if (productId != null) {
            Product product = requireProduct(productId, companyId);
            requireMatching("grupo", groupId, product.getProductGroupId());
            requireMatching("tipo", typeId, product.getProductTypeId());
            groupId = product.getProductGroupId();
            typeId = product.getProductTypeId();
        }
        if (groupId != null) {
            ProductGroup group = requireGroup(groupId, companyId);
            requireMatching("tipo", typeId, group.getProductTypeId());
            typeId = group.getProductTypeId();
        }
        if (typeId != null) {
            ProductType type = requireType(typeId, companyId);
            requireMatching("supertipo", supertypeId, type.getSupertypeId());
            supertypeId = type.getSupertypeId();
        }
        if (supertypeId != null) {
            ProductSupertype supertype = requireSupertype(supertypeId, companyId);
            requireMatching("naturaleza", natureId, supertype.getNatureId());
            natureId = supertype.getNatureId();
        }
        if (natureId != null) requireNature(natureId, companyId);

        return new PricingContext(companyId, request.customerId(), customer == null ? null : customer.getPriceListId(),
                productId, natureId, supertypeId, typeId, groupId, request.quantity(), request.date(), request.basePrice(),
                request.currency().trim().toUpperCase(Locale.ROOT));
    }

    CustomerProfile requireCustomer(UUID id, UUID companyId) {
        return customerRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
    }

    ProductNature requireNature(UUID id, UUID companyId) {
        if (id == null) throw new BusinessRuleException("La naturaleza de producto es obligatoria.");
        return natureRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Naturaleza de producto", id));
    }

    ProductSupertype requireSupertype(UUID id, UUID companyId) {
        if (id == null) throw new BusinessRuleException("El supertipo de producto es obligatorio.");
        return supertypeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Supertipo de producto", id));
    }

    ProductType requireType(UUID id, UUID companyId) {
        if (id == null) throw new BusinessRuleException("El tipo de producto es obligatorio.");
        return typeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de producto", id));
    }

    ProductGroup requireGroup(UUID id, UUID companyId) {
        if (id == null) throw new BusinessRuleException("El grupo de producto es obligatorio.");
        return groupRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de producto", id));
    }

    Product requireProduct(UUID id, UUID companyId) {
        if (id == null) throw new BusinessRuleException("El artículo es obligatorio.");
        return productRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Artículo", id));
    }

    private void requireOnly(TariffRequest request, UUID customerId, UUID natureId, UUID supertypeId,
                             UUID typeId, UUID groupId, UUID productId) {
        if (!Objects.equals(request.customerId(), customerId)
                || !Objects.equals(request.productNatureId(), natureId)
                || !Objects.equals(request.productSupertypeId(), supertypeId)
                || !Objects.equals(request.productTypeId(), typeId)
                || !Objects.equals(request.productGroupId(), groupId)
                || !Objects.equals(request.productId(), productId)) {
            throw new BusinessRuleException("Las referencias indicadas no son coherentes con el ámbito de la tarifa.");
        }
    }

    private void requireOnly(PricingRuleRequest request, UUID natureId, UUID supertypeId, UUID typeId,
                             UUID groupId, UUID productId) {
        if (!Objects.equals(request.productNatureId(), natureId)
                || !Objects.equals(request.productSupertypeId(), supertypeId)
                || !Objects.equals(request.productTypeId(), typeId)
                || !Objects.equals(request.productGroupId(), groupId)
                || !Objects.equals(request.productId(), productId)) {
            throw new BusinessRuleException("Las referencias indicadas no son coherentes con el objetivo de la regla.");
        }
    }

    private void requireMatching(String label, UUID supplied, UUID derived) {
        if (supplied != null && !Objects.equals(supplied, derived)) {
            throw new BusinessRuleException("La referencia de " + label + " no coincide con la clasificación derivada.");
        }
    }
}
