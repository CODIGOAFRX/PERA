package com.peraerp.operations.inventory;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.config.CurrentUserProvider;
import com.peraerp.operations.inventory.InventoryDtos.ScanDirection;
import com.peraerp.operations.inventory.InventoryDtos.StockScanRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.peraerp.operations.inventory.InventoryDtos.StockAdjustmentRequest;
import static com.peraerp.operations.inventory.InventoryDtos.StockLevelResponse;
import static com.peraerp.operations.inventory.InventoryDtos.StockMovementResponse;
import static com.peraerp.operations.inventory.InventoryDtos.StockTransferRequest;

@Service
public class StockService {

    /** Datos de un apunte que otro módulo (compras, por ejemplo) pide registrar en el diario. */
    public record StockPosting(UUID warehouseId, UUID productId, String productCode, String productName,
                               String unitOfMeasure, StockMovementType type, BigDecimal quantity,
                               BigDecimal unitCost, String costCurrencyCode, StockSourceType sourceType,
                               UUID sourceId, String sourceNumber, String note, StockReason reason,
                               UUID clientReference) {
        public StockPosting(UUID warehouseId, UUID productId, String productCode, String productName,
                            String unitOfMeasure, StockMovementType type, BigDecimal quantity, BigDecimal unitCost,
                            String costCurrencyCode, StockSourceType sourceType, UUID sourceId, String sourceNumber,
                            String note) {
            this(warehouseId, productId, productCode, productName, unitOfMeasure, type, quantity, unitCost,
                    costCurrencyCode, sourceType, sourceId, sourceNumber, note, null, null);
        }
    }

    private final StockLevelRepository levelRepository;
    private final StockMovementRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final CurrentCompanyProvider companyProvider;
    private final CurrentUserProvider userProvider;

    @Autowired
    public StockService(StockLevelRepository levelRepository, StockMovementRepository movementRepository,
                        WarehouseRepository warehouseRepository, CurrentCompanyProvider companyProvider,
                        CurrentUserProvider userProvider) {
        this.levelRepository = levelRepository;
        this.movementRepository = movementRepository;
        this.warehouseRepository = warehouseRepository;
        this.companyProvider = companyProvider;
        this.userProvider = userProvider;
    }

    public StockService(StockLevelRepository levelRepository, StockMovementRepository movementRepository,
                        WarehouseRepository warehouseRepository, CurrentCompanyProvider companyProvider) {
        this(levelRepository, movementRepository, warehouseRepository, companyProvider, null);
    }

    @Transactional(readOnly = true)
    public Page<StockLevelResponse> searchLevels(UUID warehouseId, UUID productId, boolean onlyInStock,
                                                 String query, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalize(query);
        return levelRepository.search(companyId, warehouseId != null, warehouseId, productId != null, productId,
                        onlyInStock, normalizedQuery != null, normalizedQuery == null ? "" : normalizedQuery,
                        pageable)
                .map(StockLevelResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> searchMovements(UUID warehouseId, UUID productId, StockMovementType type,
                                                       Instant occurredFrom, Instant occurredTo, String query,
                                                       Pageable pageable) {
        return searchMovements(warehouseId, productId, type, occurredFrom, occurredTo, query, false, pageable);
    }

    /** Con {@code mine}, solo los movimientos del usuario de la sesión ("lo que he hecho hoy"). */
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> searchMovements(UUID warehouseId, UUID productId, StockMovementType type,
                                                       Instant occurredFrom, Instant occurredTo, String query,
                                                       boolean mine, Pageable pageable) {
        UUID performedBy = mine && userProvider != null ? userProvider.requireUser().id() : null;
        if (occurredFrom != null && occurredTo != null && occurredTo.isBefore(occurredFrom)) {
            throw new BusinessRuleException("El final del intervalo de búsqueda no puede ser anterior al inicio.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalize(query);
        return movementRepository.search(companyId, warehouseId != null, warehouseId, productId != null, productId,
                        type != null, type, occurredFrom != null, occurredFrom, occurredTo != null, occurredTo,
                        performedBy != null, performedBy, normalizedQuery != null, normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(StockMovementResponse::from);
    }

    @Transactional
    public StockMovementResponse adjust(StockAdjustmentRequest request) {
        if (!request.type().isManualAdjustment()) {
            throw new BusinessRuleException("Solo se pueden registrar a mano ajustes de entrada o de salida.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        return StockMovementResponse.from(post(companyId, new StockPosting(request.warehouseId(),
                request.productId(), request.productCode(), request.productName(), request.unitOfMeasure(),
                request.type(), request.quantity(), null, null, StockSourceType.MANUAL, null, null,
                request.note().trim())));
    }

    /**
     * Entrada o retirada desde el móvil. Una salida no puede dejar existencias negativas: si el
     * operario retira más de lo que consta, se le avisa para que revise el recuento.
     */
    @Transactional
    public StockMovementResponse scan(StockScanRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (request.clientReference() != null) {
            var existing = movementRepository.findByCompanyIdAndClientReference(companyId, request.clientReference());
            if (existing.isPresent()) {
                return StockMovementResponse.from(existing.get());
            }
        }
        StockMovementType type = request.direction() == ScanDirection.IN
                ? StockMovementType.ADJUSTMENT_IN : StockMovementType.ADJUSTMENT_OUT;
        if (!request.reason().allows(type)) {
            throw new BusinessRuleException(type.isInbound()
                    ? "Ese motivo es de retirada; elige un motivo de entrada."
                    : "Ese motivo es de entrada; elige un motivo de retirada.");
        }
        UUID warehouseId = request.warehouseId() != null ? request.warehouseId()
                : warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(companyId).filter(Warehouse::isActive)
                .map(Warehouse::getId)
                .orElseThrow(() -> new BusinessRuleException("No hay un almacén predeterminado activo; elige uno."));
        return StockMovementResponse.from(post(companyId, new StockPosting(warehouseId, request.productId(),
                request.productCode(), request.productName(), request.unitOfMeasure(), type, request.quantity(), null,
                null, StockSourceType.SCAN, null, null, normalize(request.note()), request.reason(),
                request.clientReference())));
    }

    @Transactional
    public List<StockMovementResponse> transfer(StockTransferRequest request) {
        if (request.sourceWarehouseId().equals(request.targetWarehouseId())) {
            throw new BusinessRuleException("El almacén de origen y el de destino deben ser distintos.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        UUID transferId = UUID.randomUUID();
        String note = normalize(request.note());
        StockMovement out = post(companyId, new StockPosting(request.sourceWarehouseId(), request.productId(),
                request.productCode(), request.productName(), request.unitOfMeasure(),
                StockMovementType.TRANSFER_OUT, request.quantity(), null, null, StockSourceType.TRANSFER,
                transferId, null, note));
        StockMovement in = post(companyId, new StockPosting(request.targetWarehouseId(), request.productId(),
                request.productCode(), request.productName(), request.unitOfMeasure(),
                StockMovementType.TRANSFER_IN, request.quantity(), null, null, StockSourceType.TRANSFER,
                transferId, null, note));
        return List.of(StockMovementResponse.from(out), StockMovementResponse.from(in));
    }

    /**
     * Registra un apunte y actualiza la existencia dentro de la transacción de quien llama. La fecha
     * del apunte es siempre la del servidor: admitir fechas pasadas dejaría incoherente el saldo
     * acumulado de los apuntes posteriores.
     */
    @Transactional
    public StockMovement post(UUID companyId, StockPosting posting) {
        Warehouse warehouse = warehouseRepository.findByIdAndCompanyId(posting.warehouseId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Almacén", posting.warehouseId()));
        if (!warehouse.isActive() && !posting.type().isReversal()) {
            throw new BusinessRuleException("El almacén " + warehouse.getCode() + " está inactivo.");
        }
        String productCode = posting.productCode().trim();
        String productName = posting.productName().trim();
        String unitOfMeasure = posting.unitOfMeasure().trim().toUpperCase(Locale.ROOT);
        StockLevel level = levelRepository.findForUpdate(companyId, warehouse.getId(), posting.productId())
                .orElse(null);
        if (level == null) {
            level = new StockLevel(companyId, warehouse.getId(), posting.productId(), productCode, productName,
                    unitOfMeasure);
        } else {
            if (!level.getUnitOfMeasureSnapshot().equals(unitOfMeasure)) {
                throw new BusinessRuleException("Las existencias de " + level.getProductCodeSnapshot()
                        + " se llevan en " + level.getUnitOfMeasureSnapshot() + " y el movimiento viene en "
                        + unitOfMeasure + ".");
            }
            level.refreshProductSnapshot(productCode, productName);
        }
        BigDecimal balance;
        try {
            balance = level.apply(posting.type(), posting.quantity());
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        levelRepository.save(level);
        // Quien registra el apunte queda anotado; la lectura periódica de ventas no tiene usuario.
        var user = userProvider == null ? java.util.Optional.<CurrentUserProvider.CurrentUser>empty()
                : userProvider.findUser();
        return movementRepository.save(new StockMovement(level, posting.type(), posting.quantity(), balance,
                posting.unitCost(), posting.unitCost() == null ? null : posting.costCurrencyCode(), Instant.now(),
                posting.sourceType(), posting.sourceId(), posting.sourceNumber(), posting.note())
                .attribute(user.map(CurrentUserProvider.CurrentUser::id).orElse(null),
                        user.map(CurrentUserProvider.CurrentUser::name).orElse(null), posting.reason(),
                        posting.clientReference()));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
