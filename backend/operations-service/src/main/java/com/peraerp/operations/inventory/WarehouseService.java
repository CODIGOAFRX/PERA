package com.peraerp.operations.inventory;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static com.peraerp.operations.inventory.InventoryDtos.WarehouseRequest;
import static com.peraerp.operations.inventory.InventoryDtos.WarehouseResponse;

@Service
public class WarehouseService {

    private final WarehouseRepository repository;
    private final StockMovementRepository movementRepository;
    private final CurrentCompanyProvider companyProvider;

    public WarehouseService(WarehouseRepository repository, StockMovementRepository movementRepository,
                            CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.movementRepository = movementRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe un almacén con el código " + code + ".");
        }
        Warehouse warehouse = new Warehouse(companyId, code, request.name().trim());
        apply(warehouse, request);
        return WarehouseResponse.from(repository.save(warehouse));
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> search(Boolean active, String query, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return repository.search(companyId, active != null, Boolean.TRUE.equals(active),
                        normalizedQuery != null, normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(WarehouseResponse::from);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(UUID id) {
        return WarehouseResponse.from(require(id, companyProvider.requireCompanyId()));
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        Warehouse warehouse = require(id, companyId);
        if (!warehouse.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código de un almacén no se puede modificar.");
        }
        apply(warehouse, request);
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        Warehouse warehouse = require(id, companyId);
        if (movementRepository.existsByCompanyIdAndWarehouseId(companyId, id)) {
            throw new BusinessRuleException(
                    "El almacén tiene movimientos registrados. Desactívalo para conservar su diario.");
        }
        repository.delete(warehouse);
    }

    private void apply(Warehouse warehouse, WarehouseRequest request) {
        boolean active = request.active() == null || request.active();
        boolean makeDefault = Boolean.TRUE.equals(request.defaultWarehouse());
        if (makeDefault && !active) {
            throw new BusinessRuleException("El almacén predeterminado debe estar activo.");
        }
        if (makeDefault) {
            // El índice único parcial exige quitar la marca anterior antes de escribir la nueva.
            repository.findByCompanyIdAndDefaultWarehouseTrue(warehouse.getCompanyId())
                    .filter(current -> current != warehouse)
                    .ifPresent(current -> {
                        current.clearDefault();
                        repository.flush();
                    });
        }
        warehouse.update(request.name().trim(), normalize(request.location()), makeDefault, active);
    }

    private Warehouse require(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Almacén", id));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
