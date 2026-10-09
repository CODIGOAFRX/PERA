package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.customer.CustomerFileDtos.CatalogItemRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.CatalogItemResponse;
import com.peraerp.masterdata.customer.CustomerFileDtos.SalespersonRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.SalespersonResponse;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tablas auxiliares de clientes: grupos, tipos, formas de entrega, motivos de baja y comerciales.
 * No se borran; se desactivan para que los clientes que ya las usan sigan mostrándolas.
 */
@Service
public class CustomerSetupService {
    private final CustomerCatalogItemRepository catalogRepository;
    private final SalespersonRepository salespersonRepository;
    private final CurrentCompanyProvider companyProvider;

    public CustomerSetupService(CustomerCatalogItemRepository catalogRepository,
                                SalespersonRepository salespersonRepository,
                                CurrentCompanyProvider companyProvider) {
        this.catalogRepository = catalogRepository;
        this.salespersonRepository = salespersonRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> catalog(CustomerCatalogKind kind, Boolean active) {
        return catalogRepository.search(companyProvider.requireCompanyId(), kind, active).stream()
                .map(CatalogItemResponse::from).toList();
    }

    @Transactional
    public CatalogItemResponse createCatalogItem(CatalogItemRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String name = request.name().trim();
        if (catalogRepository.existsByCompanyIdAndKindAndNameIgnoreCase(companyId, request.kind(), name)) {
            throw new BusinessRuleException("Ya existe «" + name + "» en esta tabla.");
        }
        return CatalogItemResponse.from(catalogRepository.save(new CustomerCatalogItem(companyId, request.kind(), name,
                request.active() == null || request.active())));
    }

    @Transactional
    public CatalogItemResponse updateCatalogItem(UUID id, CatalogItemRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        CustomerCatalogItem item = catalogRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clasificación de cliente", id));
        if (item.getKind() != request.kind()) {
            throw new BusinessRuleException("Un elemento no se puede pasar a otra tabla.");
        }
        String name = request.name().trim();
        if (catalogRepository.existsByCompanyIdAndKindAndNameIgnoreCaseAndIdNot(companyId, item.getKind(), name, id)) {
            throw new BusinessRuleException("Ya existe «" + name + "» en esta tabla.");
        }
        item.update(name, request.active() == null || request.active());
        return CatalogItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<SalespersonResponse> salespeople(Boolean active) {
        return salespersonRepository.search(companyProvider.requireCompanyId(), active).stream()
                .map(SalespersonResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SalespersonResponse findSalesperson(UUID id) {
        return SalespersonResponse.from(salespersonRepository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Comercial", id)));
    }

    @Transactional
    public SalespersonResponse createSalesperson(SalespersonRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = request.code().trim().toUpperCase();
        if (salespersonRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe un comercial con el código " + code);
        }
        return SalespersonResponse.from(salespersonRepository.save(new Salesperson(companyId, code,
                request.name().trim(), blankToNull(request.email()), blankToNull(request.phone()),
                request.commissionPercentage(), request.active() == null || request.active())));
    }

    @Transactional
    public SalespersonResponse updateSalesperson(UUID id, SalespersonRequest request) {
        Salesperson salesperson = salespersonRepository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Comercial", id));
        if (!salesperson.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código del comercial no se puede modificar.");
        }
        salesperson.update(request.name().trim(), blankToNull(request.email()), blankToNull(request.phone()),
                request.commissionPercentage(), request.active() == null || request.active());
        return SalespersonResponse.from(salesperson);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
