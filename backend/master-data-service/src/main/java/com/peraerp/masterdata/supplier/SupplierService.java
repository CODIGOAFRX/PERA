package com.peraerp.masterdata.supplier;

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
public class SupplierService {
    private final SupplierProfileRepository supplierRepository;
    private final PartyRepository partyRepository;
    private final CurrentCompanyProvider companyProvider;

    public SupplierService(SupplierProfileRepository supplierRepository, PartyRepository partyRepository,
                           CurrentCompanyProvider companyProvider) {
        this.supplierRepository = supplierRepository;
        this.partyRepository = partyRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (partyRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code())) {
            throw new BusinessRuleException("Ya existe un tercero con el código " + request.code());
        }
        Party party = partyRepository.save(new Party(companyId, request.code().trim().toUpperCase(),
                request.legalName().trim(), request.tradeName(), request.taxId(), request.phone(), request.email(),
                request.observations()));
        SupplierProfile profile = supplierRepository.save(new SupplierProfile(companyId, party.getId(),
                request.carrier(), request.route(), request.defaultPaymentMethodId()));
        return SupplierResponse.from(profile, party);
    }

    @Transactional(readOnly = true)
    public SupplierResponse findById(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        SupplierProfile profile = supplierRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
        Party party = partyRepository.findByIdAndCompanyId(profile.getPartyId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tercero", profile.getPartyId()));
        return SupplierResponse.from(profile, party);
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> search(String query, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalized = query == null || query.isBlank() ? "" : query.trim();
        return supplierRepository.search(companyId, normalized, pageable)
                .map(profile -> SupplierResponse.from(profile,
                        partyRepository.findByIdAndCompanyId(profile.getPartyId(), companyId)
                                .orElseThrow(() -> new ResourceNotFoundException("Tercero", profile.getPartyId()))));
    }
}
