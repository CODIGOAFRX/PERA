package com.peraerp.identity.company;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CompanyService {

    private final CompanyRepository repository;

    public CompanyService(CompanyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> findAll() {
        return repository.findAll().stream().map(CompanyResponse::from).toList();
    }

    @Transactional
    public CompanyResponse create(CompanyRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessRuleException("Ya existe una empresa con el código " + request.code());
        }
        return CompanyResponse.from(repository.save(new Company(request.code().trim().toUpperCase(), request.name().trim(), request.taxId())));
    }

    @Transactional
    public CompanyResponse update(UUID id, CompanyRequest request) {
        Company company = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Empresa", id));
        company.update(request.name().trim(), request.taxId(), request.active());
        return CompanyResponse.from(company);
    }
}
