package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.party.Party;
import com.peraerp.masterdata.party.PartyRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerProfileRepository customerRepository;
    private final PartyRepository partyRepository;
    private final CustomerCatalogItemRepository catalogRepository;
    private final SalespersonRepository salespersonRepository;
    private final CurrentCompanyProvider companyProvider;

    public CustomerService(CustomerProfileRepository customerRepository, PartyRepository partyRepository,
                           CustomerCatalogItemRepository catalogRepository, SalespersonRepository salespersonRepository,
                           CurrentCompanyProvider companyProvider) {
        this.customerRepository = customerRepository;
        this.partyRepository = partyRepository;
        this.catalogRepository = catalogRepository;
        this.salespersonRepository = salespersonRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    @SuppressWarnings("deprecation") // Frontera de compatibilidad: se conserva el valor heredado sin interpretarlo.
    public CustomerResponse create(CustomerRequest request) {
        return create(request, false);
    }

    @Transactional
    @SuppressWarnings("deprecation") // La importación conserva datos fiscales heredados aunque no sean válidos.
    public CustomerResponse createImported(CustomerRequest request) {
        return create(request, true);
    }

    private CustomerResponse create(CustomerRequest request, boolean imported) {
        UUID companyId = companyProvider.requireCompanyId();
        if (partyRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code())) {
            throw new BusinessRuleException("Ya existe un tercero con el código " + request.code());
        }
        Party party = imported
                ? Party.imported(companyId, request.code().trim().toUpperCase(), request.legalName().trim(),
                request.tradeName(), request.taxId(), request.taxIdentificationType(), request.taxCountryCode(),
                request.phone(), request.email(), request.observations())
                : new Party(companyId, request.code().trim().toUpperCase(), request.legalName().trim(),
                request.tradeName(), request.taxId(), request.taxIdentificationType(), request.taxCountryCode(),
                request.phone(), request.email(), request.observations());
        party.setDetails(request.details());
        party = partyRepository.save(party);
        CustomerProfile profile = new CustomerProfile(companyId, party.getId(),
                request.priceListId(), request.defaultPaymentMethodId(), request.supplierCode(),
                request.calculationMultiplier(), request.creditLimit(), request.riskWarningThreshold(), request.riskPolicy());
        if (request.classification() != null) {
            requireValidClassification(companyId, CustomerClassification.from(profile), request.classification());
            profile.classify(request.classification(), party.isActive());
        }
        profile = customerRepository.save(profile);
        return CustomerResponse.from(profile, party);
    }

    @Transactional
    @SuppressWarnings("deprecation") // Mantiene el valor heredado si un cliente antiguo todavía lo envía.
    public CustomerResponse update(UUID id, CustomerRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        CustomerProfile profile = customerRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
        Party party = partyRepository.findByIdAndCompanyId(profile.getPartyId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tercero", profile.getPartyId()));
        if (!party.getCode().equalsIgnoreCase(request.code())) {
            throw new BusinessRuleException("El código del cliente no se puede modificar.");
        }
        party.update(request.legalName().trim(), request.tradeName(), request.taxId(),
                request.taxIdentificationType(), request.taxCountryCode(), request.phone(),
                request.email(), request.observations(), request.active() == null || request.active());
        party.setDetails(request.details());
        profile.update(request.priceListId(), request.defaultPaymentMethodId(), request.supplierCode(),
                request.calculationMultiplier(), request.creditLimit(), request.riskWarningThreshold(),
                request.riskPolicy());
        CustomerClassification current = CustomerClassification.from(profile);
        // Las peticiones antiguas (importación, API) no envían clasificación: se conserva la guardada.
        CustomerClassification classification = request.classification() == null ? current : request.classification();
        requireValidClassification(companyId, current, classification);
        profile.classify(classification, party.isActive());
        return CustomerResponse.from(profile, party);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        CustomerProfile profile = customerRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
        Party party = partyRepository.findByIdAndCompanyId(profile.getPartyId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tercero", profile.getPartyId()));
        return CustomerResponse.from(profile, party);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, Pageable pageable) {
        return search(query, CustomerFilter.NONE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, CustomerFilter filter, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalized = query == null || query.isBlank() ? "" : query.trim();
        Pageable alphabeticalPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return customerRepository.search(companyId, normalized, filter.groupId(), filter.typeId(),
                        filter.salespersonId(), filter.active(), alphabeticalPage)
                .map(profile -> CustomerResponse.from(profile,
                        partyRepository.findByIdAndCompanyId(profile.getPartyId(), companyId)
                                .orElseThrow(() -> new ResourceNotFoundException("Tercero", profile.getPartyId()))));
    }

    /** Filtros de la lista de clientes; los nulos no filtran. */
    public record CustomerFilter(UUID groupId, UUID typeId, UUID salespersonId, Boolean active) {
        public static final CustomerFilter NONE = new CustomerFilter(null, null, null, null);
    }

    /**
     * Cada referencia debe ser de la empresa y de la tabla que toca. Solo se exige que esté activa cuando cambia:
     * un cliente puede seguir con un grupo que ya se dio de baja.
     */
    private void requireValidClassification(UUID companyId, CustomerClassification current,
                                            CustomerClassification requested) {
        requireCatalogItem(companyId, current.groupId(), requested.groupId(), CustomerCatalogKind.GROUP, "El grupo");
        requireCatalogItem(companyId, current.typeId(), requested.typeId(), CustomerCatalogKind.TYPE, "El tipo");
        requireCatalogItem(companyId, current.deliveryMethodId(), requested.deliveryMethodId(),
                CustomerCatalogKind.DELIVERY_METHOD, "La forma de entrega");
        requireCatalogItem(companyId, current.inactiveReasonId(), requested.inactiveReasonId(),
                CustomerCatalogKind.INACTIVE_REASON, "El motivo de baja");
        UUID salespersonId = requested.salespersonId();
        if (salespersonId != null && !salespersonId.equals(current.salespersonId())) {
            Salesperson salesperson = salespersonRepository.findByIdAndCompanyId(salespersonId, companyId)
                    .orElseThrow(() -> new BusinessRuleException("El comercial indicado no existe."));
            if (!salesperson.isActive()) {
                throw new BusinessRuleException("El comercial " + salesperson.getName() + " está dado de baja.");
            }
        }
    }

    private void requireCatalogItem(UUID companyId, UUID currentId, UUID requestedId, CustomerCatalogKind kind,
                                    String label) {
        if (requestedId == null || requestedId.equals(currentId)) {
            return;
        }
        CustomerCatalogItem item = catalogRepository.findByIdAndCompanyId(requestedId, companyId)
                .filter(found -> found.getKind() == kind)
                .orElseThrow(() -> new BusinessRuleException(label + " indicado no existe."));
        if (!item.isActive()) {
            throw new BusinessRuleException(label + " «" + item.getName() + "» está dado de baja.");
        }
    }
}
