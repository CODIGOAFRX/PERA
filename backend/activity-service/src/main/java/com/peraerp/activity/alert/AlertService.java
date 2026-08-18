package com.peraerp.activity.alert;

import com.peraerp.activity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AlertService {
    private final AlertInstanceRepository repository;
    private final CurrentCompanyProvider companyProvider;

    public AlertService(AlertInstanceRepository repository, CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> findAll(AlertStatus status, int page, int size) {
        UUID companyId = companyProvider.requireCompanyId();
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AlertInstance> alerts = status == null
                ? repository.findAllByCompanyId(companyId, pageable)
                : repository.findAllByCompanyIdAndStatus(companyId, status, pageable);
        return alerts.map(AlertResponse::from);
    }

    @Transactional(readOnly = true)
    public AlertResponse findById(UUID id) {
        return AlertResponse.from(requireAlert(id, companyProvider.requireCompanyId()));
    }

    @Transactional
    public AlertResponse acknowledge(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        AlertInstance alert = requireAlert(id, companyId);
        alert.acknowledge(companyProvider.requireUserId(), Instant.now());
        return AlertResponse.from(alert);
    }

    @Transactional
    public AlertResponse resolve(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        AlertInstance alert = requireAlert(id, companyId);
        alert.resolve(companyProvider.requireUserId(), Instant.now());
        return AlertResponse.from(alert);
    }

    private AlertInstance requireAlert(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta", id));
    }
}
