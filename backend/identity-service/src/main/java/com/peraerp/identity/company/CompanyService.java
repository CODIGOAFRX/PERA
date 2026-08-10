package com.peraerp.identity.company;

import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CompanyService {

    private final CompanyRepository repository;
    private final CompanySettingsRepository settingsRepository;
    private final CurrentCompanyProvider companyProvider;

    public CompanyService(CompanyRepository repository, CompanySettingsRepository settingsRepository,
                          CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.settingsRepository = settingsRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> findAll() {
        UUID companyId = companyProvider.requireCompanyId();
        Company company = repository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", companyId));
        return List.of(CompanyResponse.from(company));
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessRuleException("Ya existe una empresa con el código " + request.code());
        }
        Company company = repository.save(new Company(request.code().trim().toUpperCase(), request.name().trim(), request.taxId()));
        settingsRepository.save(CompanySettings.defaults(company.getId(), company.getName()));
        return CompanyResponse.from(company);
    }

    @Transactional
    public CompanyResponse update(UUID id, CompanyRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (!companyId.equals(id)) {
            throw new ResourceNotFoundException("Empresa", id);
        }
        Company company = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Empresa", id));
        company.update(request.name().trim(), request.taxId(), request.active());
        return CompanyResponse.from(company);
    }
}
