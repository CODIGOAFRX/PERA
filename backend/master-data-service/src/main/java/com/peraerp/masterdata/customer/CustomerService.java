package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.masterdata.party.Party;
import com.peraerp.masterdata.party.PartyRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerProfileRepository customerRepository;
    private final PartyRepository partyRepository;
    private final CurrentCompanyProvider companyProvider;

    public CustomerService(CustomerProfileRepository customerRepository, PartyRepository partyRepository,
                           CurrentCompanyProvider companyProvider) {
        this.customerRepository = customerRepository;
        this.partyRepository = partyRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (partyRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code())) {
            throw new BusinessRuleException("Ya existe un tercero con el código " + request.code());
        }
        Party party = partyRepository.save(new Party(companyId, request.code().trim().toUpperCase(),
                request.legalName().trim(), request.tradeName(), request.taxId(), request.phone(), request.email(),
                request.observations()));
        CustomerProfile profile = customerRepository.save(new CustomerProfile(companyId, party.getId(),
                request.priceListId(), request.defaultPaymentMethodId(), request.supplierCode(),
                request.calculationMultiplier(), request.creditLimit(), request.riskWarningThreshold(), request.riskPolicy()));
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
        UUID companyId = companyProvider.requireCompanyId();
        String normalized = query == null || query.isBlank() ? "" : query.trim();
        return customerRepository.search(companyId, normalized, pageable)
                .map(profile -> CustomerResponse.from(profile,
                        partyRepository.findByIdAndCompanyId(profile.getPartyId(), companyId)
                                .orElseThrow(() -> new ResourceNotFoundException("Tercero", profile.getPartyId()))));
    }
}
