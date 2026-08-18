package com.peraerp.activity.alert;

import com.peraerp.activity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AlertRuleService {
    private final AlertRuleRepository repository;
    private final CurrentCompanyProvider companyProvider;

    public AlertRuleService(AlertRuleRepository repository, CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> findAll() {
        return repository.findAllByCompanyIdOrderByName(companyProvider.requireCompanyId()).stream()
                .map(AlertRuleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertRuleResponse findById(UUID id) {
        return AlertRuleResponse.from(requireRule(id, companyProvider.requireCompanyId()));
    }

    @Transactional
    public AlertRuleResponse create(AlertRuleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe una regla de alerta con ese código.");
        }
        ValidatedRule values = validate(request);
        AlertRule rule = new AlertRule(companyId, code, values.name(), values.eventType(), values.action(),
                values.resourceType(), values.conditionField(), values.conditionOperator(), values.conditionValue(),
                request.severity(), values.titleTemplate(), values.messageTemplate(), request.cooldownMinutes(),
                channel(request), request.active());
        return AlertRuleResponse.from(repository.save(rule));
    }

    @Transactional
    public AlertRuleResponse update(UUID id, AlertRuleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        AlertRule rule = requireRule(id, companyId);
        if (!rule.getCode().equals(normalizeCode(request.code()))) {
            throw new BusinessRuleException("El código de una regla de alerta no se puede modificar.");
        }
        ValidatedRule values = validate(request);
        rule.update(values.name(), values.eventType(), values.action(), values.resourceType(),
                values.conditionField(), values.conditionOperator(), values.conditionValue(), request.severity(),
                values.titleTemplate(), values.messageTemplate(), request.cooldownMinutes(), channel(request),
                request.active());
        return AlertRuleResponse.from(rule);
    }

    @Transactional
    public void deactivate(UUID id) {
        requireRule(id, companyProvider.requireCompanyId()).deactivate();
    }

    private AlertRule requireRule(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla de alerta", id));
    }

    private ValidatedRule validate(AlertRuleRequest request) {
        String field = nullableTrim(request.conditionField());
        AlertConditionOperator operator = request.conditionOperator();
        String value = nullableTrim(request.conditionValue());
        if (field == null && (operator != null || value != null)) {
            throw new BusinessRuleException("Una condición con operador o valor necesita un campo.");
        }
        if (field != null && operator == null) {
            throw new BusinessRuleException("La condición necesita un operador.");
        }
        if (field != null && !field.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")) {
            throw new BusinessRuleException("El campo de condición no tiene un formato válido.");
        }
        if (operator != null && operator != AlertConditionOperator.EXISTS
                && operator != AlertConditionOperator.NOT_EXISTS && value == null) {
            throw new BusinessRuleException("El operador seleccionado necesita un valor de comparación.");
        }
        if (operator == AlertConditionOperator.EXISTS || operator == AlertConditionOperator.NOT_EXISTS) {
            value = null;
        }
        return new ValidatedRule(request.name().trim(), request.eventType().trim(), nullableTrim(request.action()),
                nullableTrim(request.resourceType()), field, operator, value, request.titleTemplate().trim(),
                request.messageTemplate().trim());
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private AlertDeliveryChannel channel(AlertRuleRequest request) {
        return request.deliveryChannel() == null ? AlertDeliveryChannel.IN_APP : request.deliveryChannel();
    }

    private String nullableTrim(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private record ValidatedRule(String name, String eventType, String action, String resourceType,
                                 String conditionField, AlertConditionOperator conditionOperator,
                                 String conditionValue, String titleTemplate, String messageTemplate) {
    }
}
