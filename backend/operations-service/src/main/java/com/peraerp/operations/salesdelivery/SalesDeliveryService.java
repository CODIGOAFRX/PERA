package com.peraerp.operations.salesdelivery;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.inventory.StockLevel;
import com.peraerp.operations.inventory.StockLevelRepository;
import com.peraerp.operations.inventory.StockMovement;
import com.peraerp.operations.inventory.StockMovementRepository;
import com.peraerp.operations.inventory.StockMovementType;
import com.peraerp.operations.inventory.StockService;
import com.peraerp.operations.inventory.StockService.StockPosting;
import com.peraerp.operations.inventory.StockSourceType;
import com.peraerp.operations.inventory.Warehouse;
import com.peraerp.operations.inventory.WarehouseRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Salidas de almacén por ventas.
 *
 * <p>Lee de sales-service los albaranes y las facturas sin albarán, y descuenta sus productos del
 * almacén predeterminado. Solo se descuentan los productos que ya tienen ficha de existencias: una
 * empresa que no lleva inventario, o un producto que nunca ha entrado en almacén, no se ve afectada.
 * Si faltan existencias la venta no se bloquea; la salida queda pendiente y visible para resolverla.</p>
 */
@Service
public class SalesDeliveryService {

    /** Margen de relectura: cubre ventas confirmadas en una transacción que tardó en cerrarse. */
    private static final Duration OVERLAP = Duration.ofMinutes(2);

    public record SalesDeliveryLineResponse(int sequence, UUID productId, String productCode, String description,
                                            BigDecimal quantity) {
    }

    public record SalesDeliveryResponse(UUID id, UUID sourceDocumentId, String sourceType, String sourceNumber,
                                        java.time.LocalDate sourceDate, String sourceStatus, String customerCode,
                                        String customerName, SalesDeliveryStatus status, UUID warehouseId,
                                        String problem, Instant postedAt, List<SalesDeliveryLineResponse> lines) {
    }

    private final SalesDeliveryRepository repository;
    private final SalesDeliveryLineRepository lineRepository;
    private final SalesDeliverySyncRepository syncRepository;
    private final SalesDeliveryClient client;
    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository levelRepository;
    private final StockMovementRepository movementRepository;
    private final StockService stockService;
    private final CurrentCompanyProvider companyProvider;
    private final PosTicketSyncRepository posSyncRepository;
    private final PosTicketDeliveryClient posClient;

    public SalesDeliveryService(SalesDeliveryRepository repository, SalesDeliveryLineRepository lineRepository,
                                SalesDeliverySyncRepository syncRepository, SalesDeliveryClient client,
                                WarehouseRepository warehouseRepository, StockLevelRepository levelRepository,
                                StockMovementRepository movementRepository, StockService stockService,
                                CurrentCompanyProvider companyProvider) {
        this(repository, lineRepository, syncRepository, client, warehouseRepository, levelRepository,
                movementRepository, stockService, companyProvider, null, null);
    }

    @Autowired
    public SalesDeliveryService(SalesDeliveryRepository repository, SalesDeliveryLineRepository lineRepository,
                                SalesDeliverySyncRepository syncRepository, SalesDeliveryClient client,
                                WarehouseRepository warehouseRepository, StockLevelRepository levelRepository,
                                StockMovementRepository movementRepository, StockService stockService,
                                CurrentCompanyProvider companyProvider, PosTicketSyncRepository posSyncRepository,
                                PosTicketDeliveryClient posClient) {
        this.posSyncRepository = posSyncRepository;
        this.posClient = posClient;
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.syncRepository = syncRepository;
        this.client = client;
        this.warehouseRepository = warehouseRepository;
        this.levelRepository = levelRepository;
        this.movementRepository = movementRepository;
        this.stockService = stockService;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public void synchronizeCurrentCompany() {
        synchronize(companyProvider.requireCompanyId());
    }

    /**
     * Lee las entregas nuevas de Ventas y los tickets nuevos del TPV, y reintenta las salidas que
     * seguían pendientes. Es idempotente.
     */
    @Transactional
    public void synchronize(UUID companyId) {
        boolean salesRead = readSales(companyId);
        boolean posRead = readPointOfSale(companyId);
        if (!salesRead && !posRead) {
            return;
        }
        Warehouse defaultWarehouse = warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(companyId)
                .filter(Warehouse::isActive).orElse(null);
        for (SalesDelivery pending : repository.findAllByCompanyIdAndStatusOrderBySourceDateAscCreatedAtAsc(
                companyId, SalesDeliveryStatus.PENDING)) {
            tryPost(pending, defaultWarehouse);
        }
    }

    private boolean readSales(UUID companyId) {
        Optional<SalesDeliverySync> existing = syncRepository.findByCompanyId(companyId);
        if (existing.isEmpty()) {
            // Primera vez: se empieza a contar desde ahora. El histórico de ventas no se descuenta.
            syncRepository.save(new SalesDeliverySync(companyId, Instant.now()));
            return false;
        }
        SalesDeliverySync sync = existing.get();
        Optional<List<SalesDeliverySnapshot>> snapshots = client.findUpdatedSince(companyId,
                sync.getLastSourceUpdate().minus(OVERLAP));
        if (snapshots.isEmpty()) {
            return false;
        }
        for (SalesDeliverySnapshot snapshot : snapshots.get()) {
            apply(companyId, snapshot);
            sync.advanceTo(snapshot.updatedAt());
        }
        return true;
    }

    /** Lo mismo con los tickets del TPV, con su propia marca de lectura. */
    private boolean readPointOfSale(UUID companyId) {
        if (posClient == null || posSyncRepository == null) {
            return false;
        }
        Optional<PosTicketSync> existing = posSyncRepository.findByCompanyId(companyId);
        if (existing.isEmpty()) {
            posSyncRepository.save(new PosTicketSync(companyId, Instant.now()));
            return false;
        }
        PosTicketSync sync = existing.get();
        Optional<List<SalesDeliverySnapshot>> snapshots = posClient.findUpdatedSince(companyId,
                sync.getLastSourceUpdate().minus(OVERLAP));
        if (snapshots.isEmpty()) {
            return false;
        }
        for (SalesDeliverySnapshot snapshot : snapshots.get()) {
            apply(companyId, snapshot);
            sync.advanceTo(snapshot.updatedAt());
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Page<SalesDeliveryResponse> search(SalesDeliveryStatus status, String query, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return repository.search(companyId, status != null, status, normalizedQuery != null,
                normalizedQuery == null ? "" : normalizedQuery, pageable).map(this::response);
    }

    /** Da la salida desde el almacén elegido. Falla si sigue sin poder darse. */
    @Transactional
    public SalesDeliveryResponse post(UUID id, UUID warehouseId) {
        SalesDelivery delivery = requirePending(id);
        Warehouse warehouse = warehouseRepository.findByIdAndCompanyId(warehouseId, delivery.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Almacén", warehouseId));
        if (!warehouse.isActive()) {
            throw new BusinessRuleException("El almacén " + warehouse.getCode() + " está inactivo.");
        }
        tryPost(delivery, warehouse);
        if (delivery.getStatus() == SalesDeliveryStatus.PENDING) {
            throw new BusinessRuleException(delivery.getProblem());
        }
        return response(delivery);
    }

    @Transactional
    public SalesDeliveryResponse dismiss(UUID id) {
        SalesDelivery delivery = requirePending(id);
        delivery.dismiss();
        return response(delivery);
    }

    private void apply(UUID companyId, SalesDeliverySnapshot snapshot) {
        SalesDelivery delivery = repository.findByCompanyIdAndSourceDocumentId(companyId, snapshot.id()).orElse(null);
        if (delivery == null) {
            if (snapshot.cancelled()) {
                return;
            }
            delivery = repository.saveAndFlush(new SalesDelivery(companyId, snapshot));
            replaceLines(delivery, snapshot);
            return;
        }
        delivery.refresh(snapshot);
        if (snapshot.cancelled()) {
            if (delivery.getStatus() == SalesDeliveryStatus.POSTED) {
                reverse(delivery);
            } else if (delivery.getStatus() == SalesDeliveryStatus.PENDING) {
                delivery.dismiss();
            }
        } else if (delivery.getStatus() == SalesDeliveryStatus.PENDING) {
            // Mientras no ha salido, la entrega sigue lo que diga el documento de venta.
            lineRepository.deleteAllByCompanyIdAndDeliveryId(companyId, delivery.getId());
            lineRepository.flush();
            replaceLines(delivery, snapshot);
        }
    }

    private void replaceLines(SalesDelivery delivery, SalesDeliverySnapshot snapshot) {
        List<SalesDeliveryLine> lines = new ArrayList<>();
        int sequence = 1;
        for (SalesDeliverySnapshot.Line line : snapshot.lines()) {
            if (line.productId() == null || line.quantity() == null || line.quantity().signum() <= 0) {
                continue;
            }
            lines.add(new SalesDeliveryLine(delivery.getCompanyId(), delivery.getId(), sequence++, line.productId(),
                    line.productCode() == null ? "" : line.productCode(), line.description(), line.quantity()));
        }
        lineRepository.saveAll(lines);
    }

    private void tryPost(SalesDelivery delivery, Warehouse warehouse) {
        UUID companyId = delivery.getCompanyId();
        // Cantidad total por producto, en orden estable para bloquear siempre las existencias igual.
        Map<UUID, BigDecimal> required = new TreeMap<>();
        Map<UUID, String> codes = new TreeMap<>();
        for (SalesDeliveryLine line : lines(delivery)) {
            if (levelRepository.existsByCompanyIdAndProductId(companyId, line.getProductId())) {
                required.merge(line.getProductId(), line.getQuantity(), BigDecimal::add);
                codes.putIfAbsent(line.getProductId(), line.getProductCodeSnapshot());
            }
        }
        if (required.isEmpty()) {
            delivery.markNotApplicable();
            return;
        }
        if (warehouse == null) {
            delivery.reportProblem("No hay un almacén predeterminado activo desde el que dar la salida.");
            return;
        }
        if (isPosReturn(delivery)) {
            // Una devolución en el TPV vuelve a entrar en el almacén: no puede faltar nada.
            for (Map.Entry<UUID, BigDecimal> entry : required.entrySet()) {
                StockLevel level = levelRepository.findForUpdate(companyId, warehouse.getId(), entry.getKey())
                        .orElse(null);
                String code = level == null ? codes.get(entry.getKey()) : level.getProductCodeSnapshot();
                String name = level == null ? codes.get(entry.getKey()) : level.getProductNameSnapshot();
                String unit = level == null ? unitOf(companyId, entry.getKey()) : level.getUnitOfMeasureSnapshot();
                stockService.post(companyId, new StockPosting(warehouse.getId(), entry.getKey(), code, name, unit,
                        StockMovementType.SALES_RETURN, entry.getValue(), null, null, StockSourceType.POS_TICKET,
                        delivery.getSourceDocumentId(), delivery.getSourceNumber(), "Devolución en el TPV"));
            }
            delivery.markPosted(warehouse.getId(), Instant.now());
            return;
        }
        Map<UUID, StockLevel> levels = new TreeMap<>();
        List<String> shortages = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : required.entrySet()) {
            StockLevel level = levelRepository.findForUpdate(companyId, warehouse.getId(), entry.getKey())
                    .orElse(null);
            BigDecimal available = level == null ? BigDecimal.ZERO : level.getQuantity();
            if (available.compareTo(entry.getValue()) < 0) {
                shortages.add(codes.get(entry.getKey()) + " (disponibles " + plain(available) + ", necesarias "
                        + plain(entry.getValue()) + ")");
            } else {
                levels.put(entry.getKey(), level);
            }
        }
        if (!shortages.isEmpty()) {
            delivery.reportProblem("Faltan existencias en " + warehouse.getCode() + ": "
                    + String.join("; ", shortages) + ".");
            return;
        }
        for (Map.Entry<UUID, BigDecimal> entry : required.entrySet()) {
            StockLevel level = levels.get(entry.getKey());
            stockService.post(companyId, new StockPosting(warehouse.getId(), entry.getKey(),
                    level.getProductCodeSnapshot(), level.getProductNameSnapshot(), level.getUnitOfMeasureSnapshot(),
                    StockMovementType.SALES_ISSUE, entry.getValue(), null, null, stockSource(delivery),
                    delivery.getSourceDocumentId(), delivery.getSourceNumber(), null));
        }
        delivery.markPosted(warehouse.getId(), Instant.now());
    }

    /** Devuelve al almacén exactamente lo que salió con esta venta. */
    private void reverse(SalesDelivery delivery) {
        UUID companyId = delivery.getCompanyId();
        for (StockMovement issue : movementRepository.findAllByCompanyIdAndSourceTypeAndSourceIdAndType(companyId,
                stockSource(delivery), delivery.getSourceDocumentId(), StockMovementType.SALES_ISSUE)) {
            stockService.post(companyId, new StockPosting(issue.getWarehouseId(), issue.getProductId(),
                    issue.getProductCodeSnapshot(), issue.getProductNameSnapshot(), issue.getUnitOfMeasureSnapshot(),
                    StockMovementType.SALES_RETURN, issue.getQuantity(), null, null,
                    stockSource(delivery), delivery.getSourceDocumentId(), delivery.getSourceNumber(),
                    "Venta anulada"));
        }
        delivery.markReversed();
    }

    private SalesDelivery requirePending(UUID id) {
        SalesDelivery delivery = repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Salida de venta", id));
        if (delivery.getStatus() != SalesDeliveryStatus.PENDING) {
            throw new BusinessRuleException("La salida ya no está pendiente.");
        }
        return delivery;
    }

    private List<SalesDeliveryLine> lines(SalesDelivery delivery) {
        return lineRepository.findAllByCompanyIdAndDeliveryIdOrderByLineSequenceAsc(
                delivery.getCompanyId(), delivery.getId());
    }

    private SalesDeliveryResponse response(SalesDelivery delivery) {
        return new SalesDeliveryResponse(delivery.getId(), delivery.getSourceDocumentId(), delivery.getSourceType(),
                delivery.getSourceNumber(), delivery.getSourceDate(), delivery.getSourceStatus(),
                delivery.getCustomerCode(), delivery.getCustomerName(), delivery.getStatus(),
                delivery.getWarehouseId(), delivery.getProblem(), delivery.getPostedAt(),
                lines(delivery).stream().map(line -> new SalesDeliveryLineResponse(line.getLineSequence(),
                        line.getProductId(), line.getProductCodeSnapshot(), line.getDescription(),
                        line.getQuantity())).toList());
    }

    private static boolean isPosReturn(SalesDelivery delivery) {
        return "POS_RETURN".equals(delivery.getSourceType());
    }

    private static StockSourceType stockSource(SalesDelivery delivery) {
        return delivery.getSourceType() != null && delivery.getSourceType().startsWith("POS_")
                ? StockSourceType.POS_TICKET : StockSourceType.SALES_DOCUMENT;
    }

    /** Unidad con la que se lleva el producto en cualquier almacén, para dar entrada a una devolución. */
    private String unitOf(UUID companyId, UUID productId) {
        return levelRepository.findFirstByCompanyIdAndProductId(companyId, productId)
                .map(StockLevel::getUnitOfMeasureSnapshot).orElse("UNIT");
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
