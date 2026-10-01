package com.peraerp.operations.purchasing;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.inventory.StockMovementType;
import com.peraerp.operations.inventory.StockService;
import com.peraerp.operations.inventory.StockService.StockPosting;
import com.peraerp.operations.inventory.StockSourceType;
import com.peraerp.operations.inventory.Warehouse;
import com.peraerp.operations.inventory.WarehouseRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.peraerp.operations.purchasing.PurchaseDtos.ConvertPurchaseDocumentRequest;
import static com.peraerp.operations.purchasing.PurchaseDtos.PurchaseDocumentRequest;
import static com.peraerp.operations.purchasing.PurchaseDtos.PurchaseDocumentResponse;
import static com.peraerp.operations.purchasing.PurchaseDtos.PurchaseLineRequest;

@Service
public class PurchaseDocumentService {

    private static final List<PurchaseDocumentStatus> IN_FORCE = List.of(
            PurchaseDocumentStatus.CONFIRMED, PurchaseDocumentStatus.CONVERTED);

    private final PurchaseDocumentRepository repository;
    private final PurchaseDocumentLineRepository lineRepository;
    private final PurchaseDocumentSequenceRepository sequenceRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockService stockService;
    private final CurrentCompanyProvider companyProvider;

    public PurchaseDocumentService(PurchaseDocumentRepository repository,
                                   PurchaseDocumentLineRepository lineRepository,
                                   PurchaseDocumentSequenceRepository sequenceRepository,
                                   WarehouseRepository warehouseRepository, StockService stockService,
                                   CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.sequenceRepository = sequenceRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockService = stockService;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public PurchaseDocumentResponse create(PurchaseDocumentRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        validate(companyId, request);
        PurchaseDocument document = new PurchaseDocument(companyId, request.type(),
                nextNumber(companyId, request.type(), request.issueDate().getYear()), null, false);
        applyHeader(document, request);
        repository.saveAndFlush(document);
        List<PurchaseDocumentLine> lines = createLines(document, request.lines());
        lineRepository.saveAll(lines);
        document.applyTotals(PurchaseAmounts.totals(lines));
        return PurchaseDocumentResponse.from(document, lines);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseDocumentResponse> search(PurchaseDocumentType type, PurchaseDocumentStatus status,
                                                 UUID supplierId, LocalDate fromDate, LocalDate toDate,
                                                 String query, Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BusinessRuleException("El final del intervalo de búsqueda no puede ser anterior al inicio.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalize(query);
        return repository.search(companyId, type != null, type, status != null, status, supplierId != null,
                        supplierId, fromDate != null, fromDate, toDate != null, toDate, normalizedQuery != null,
                        normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(this::response);
    }

    @Transactional(readOnly = true)
    public PurchaseDocumentResponse findById(UUID id) {
        return response(require(id));
    }

    @Transactional
    public PurchaseDocumentResponse update(UUID id, PurchaseDocumentRequest request) {
        PurchaseDocument document = require(id);
        if (document.getType() != request.type()) {
            throw new BusinessRuleException("El tipo de un documento de compra no se puede modificar.");
        }
        if (document.getSourceDocumentId() != null && !document.getSupplierId().equals(request.supplierId())) {
            throw new BusinessRuleException(
                    "El proveedor no se puede cambiar en un documento que procede de otro.");
        }
        validate(document.getCompanyId(), request);
        try {
            applyHeader(document, request);
            lineRepository.deleteAllByCompanyIdAndDocumentId(document.getCompanyId(), document.getId());
            lineRepository.flush();
            List<PurchaseDocumentLine> lines = createLines(document, request.lines());
            lineRepository.saveAll(lines);
            document.applyTotals(PurchaseAmounts.totals(lines));
            return PurchaseDocumentResponse.from(document, lines);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
    }

    @Transactional
    public void delete(UUID id) {
        PurchaseDocument document = require(id);
        if (document.getStatus() != PurchaseDocumentStatus.DRAFT) {
            throw new BusinessRuleException("Solo se puede eliminar un documento en borrador. Anúlalo en su lugar.");
        }
        reopenSource(document);
        lineRepository.deleteAllByCompanyIdAndDocumentId(document.getCompanyId(), id);
        lineRepository.flush();
        repository.delete(document);
    }

    /**
     * Confirma el documento. Un albarán de entrada da de alta las existencias en su almacén. Una
     * factura solo lo hace cuando no viene de un albarán y tiene almacén: es el caso de la mercancía
     * que llega directamente con factura.
     */
    @Transactional
    public PurchaseDocumentResponse confirm(UUID id) {
        PurchaseDocument document = require(id);
        if (document.getStatus() != PurchaseDocumentStatus.DRAFT) {
            throw new BusinessRuleException("Solo se puede confirmar un documento en borrador.");
        }
        UUID companyId = document.getCompanyId();
        List<PurchaseDocumentLine> lines = lines(document);
        if (document.getType() == PurchaseDocumentType.SUPPLIER_INVOICE) {
            if (document.getSupplierReference() == null) {
                throw new BusinessRuleException("Indica el número de factura del proveedor antes de confirmarla.");
            }
            if (repository.existsSupplierInvoice(companyId, document.getSupplierId(),
                    document.getSupplierReference(), IN_FORCE, document.getId())) {
                throw new BusinessRuleException("La factura " + document.getSupplierReference()
                        + " de este proveedor ya está registrada.");
            }
        }
        boolean postsStock = switch (document.getType()) {
            case PURCHASE_ORDER -> false;
            case GOODS_RECEIPT -> true;
            case SUPPLIER_INVOICE -> !document.isStockReceivedUpstream() && document.getWarehouseId() != null;
        };
        if (postsStock) {
            if (document.getWarehouseId() == null) {
                throw new BusinessRuleException("Indica el almacén de entrada antes de confirmar el albarán.");
            }
            postStock(document, lines, StockMovementType.PURCHASE_RECEIPT);
        }
        document.confirm(postsStock);
        return PurchaseDocumentResponse.from(document, lines);
    }

    /** Anula el documento y, si había dado entrada en almacén, la deshace con apuntes de signo contrario. */
    @Transactional
    public PurchaseDocumentResponse cancel(UUID id) {
        PurchaseDocument document = require(id);
        List<PurchaseDocumentLine> lines = lines(document);
        boolean reverseStock = document.isStockPosted();
        try {
            document.cancel();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        if (reverseStock) {
            postStock(document, lines, StockMovementType.PURCHASE_REVERSAL);
        }
        reopenSource(document);
        return PurchaseDocumentResponse.from(document, lines);
    }

    @Transactional
    public PurchaseDocumentResponse convert(UUID id, ConvertPurchaseDocumentRequest request) {
        PurchaseDocument source = require(id);
        if (source.getStatus() != PurchaseDocumentStatus.CONFIRMED) {
            throw new BusinessRuleException("Solo se puede convertir un documento confirmado.");
        }
        if (!source.getType().canConvertTo(request.targetType())) {
            throw new BusinessRuleException("Este documento no se puede convertir al tipo solicitado.");
        }
        UUID companyId = source.getCompanyId();
        LocalDate issueDate = request.issueDate() == null ? LocalDate.now() : request.issueDate();
        // La mercancía de un albarán ya entró en almacén: su factura no debe volver a darla de alta.
        boolean receivedUpstream = source.getType() == PurchaseDocumentType.GOODS_RECEIPT
                || source.isStockReceivedUpstream();
        PurchaseDocument target = new PurchaseDocument(companyId, request.targetType(),
                nextNumber(companyId, request.targetType(), issueDate.getYear()), source.getId(), receivedUpstream);
        target.updateHeader(source.getSupplierId(), source.getSupplierCodeSnapshot(),
                source.getSupplierNameSnapshot(), source.getSupplierTaxIdSnapshot(), null, issueDate, null,
                source.getWarehouseId(), source.getCurrencyCode(), source.getNotes());
        repository.saveAndFlush(target);
        List<PurchaseDocumentLine> lines = lines(source).stream()
                .map(line -> new PurchaseDocumentLine(companyId, target.getId(), line.getLineSequence(),
                        line.getProductId(), line.getProductCodeSnapshot(), line.getDescription(),
                        line.getUnitOfMeasureSnapshot(), line.getQuantity(), line.getUnitPrice(),
                        line.getDiscountPercentage(), line.getTaxPercentage(), line.getNetAmount()))
                .toList();
        lineRepository.saveAll(lines);
        target.applyTotals(PurchaseAmounts.totals(lines));
        source.markConverted();
        return PurchaseDocumentResponse.from(target, lines);
    }

    private void postStock(PurchaseDocument document, List<PurchaseDocumentLine> lines, StockMovementType type) {
        // Orden estable por producto: dos confirmaciones simultáneas bloquean las existencias en el mismo orden.
        lines.stream()
                .filter(line -> line.getProductId() != null)
                .sorted(Comparator.comparing(PurchaseDocumentLine::getProductId)
                        .thenComparingInt(PurchaseDocumentLine::getLineSequence))
                .forEach(line -> stockService.post(document.getCompanyId(), new StockPosting(
                        document.getWarehouseId(), line.getProductId(), line.getProductCodeSnapshot(),
                        line.getDescription(), line.getUnitOfMeasureSnapshot(), type, line.getQuantity(),
                        PurchaseAmounts.unitCost(line), document.getCurrencyCode(),
                        StockSourceType.PURCHASE_DOCUMENT, document.getId(), document.getNumber(), null)));
    }

    private void reopenSource(PurchaseDocument document) {
        UUID sourceId = document.getSourceDocumentId();
        if (sourceId == null) {
            return;
        }
        UUID companyId = document.getCompanyId();
        boolean stillConverted = repository.existsByCompanyIdAndSourceDocumentIdAndStatusNotAndIdNot(
                companyId, sourceId, PurchaseDocumentStatus.CANCELLED, document.getId());
        if (!stillConverted) {
            repository.findByIdAndCompanyId(sourceId, companyId)
                    .ifPresent(PurchaseDocument::reopenAfterDiscardedConversion);
        }
    }

    private void validate(UUID companyId, PurchaseDocumentRequest request) {
        if (request.expectedDate() != null && request.expectedDate().isBefore(request.issueDate())) {
            throw new BusinessRuleException("La fecha prevista no puede ser anterior a la fecha del documento.");
        }
        if (request.warehouseId() != null) {
            Warehouse warehouse = warehouseRepository.findByIdAndCompanyId(request.warehouseId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Almacén", request.warehouseId()));
            if (!warehouse.isActive()) {
                throw new BusinessRuleException("El almacén " + warehouse.getCode() + " está inactivo.");
            }
        }
        for (PurchaseLineRequest line : request.lines()) {
            if (line.productId() != null && normalize(line.productCode()) == null) {
                throw new BusinessRuleException("Las líneas con producto deben incluir su código.");
            }
        }
    }

    private void applyHeader(PurchaseDocument document, PurchaseDocumentRequest request) {
        document.updateHeader(request.supplierId(), request.supplierCode().trim(), request.supplierName().trim(),
                normalize(request.supplierTaxId()), normalize(request.supplierReference()), request.issueDate(),
                request.expectedDate(), request.warehouseId(),
                request.currencyCode().trim().toUpperCase(Locale.ROOT), normalize(request.notes()));
    }

    private List<PurchaseDocumentLine> createLines(PurchaseDocument document, List<PurchaseLineRequest> requests) {
        List<PurchaseDocumentLine> lines = new ArrayList<>(requests.size());
        int sequence = 1;
        for (PurchaseLineRequest request : requests) {
            BigDecimal discount = request.discountPercentage() == null
                    ? BigDecimal.ZERO : request.discountPercentage();
            BigDecimal tax = request.taxPercentage() == null ? BigDecimal.ZERO : request.taxPercentage();
            lines.add(new PurchaseDocumentLine(document.getCompanyId(), document.getId(), sequence++,
                    request.productId(), request.productId() == null ? null : request.productCode().trim(),
                    request.description().trim(), request.unitOfMeasure().trim().toUpperCase(Locale.ROOT),
                    request.quantity(), request.unitPrice(), discount, tax,
                    PurchaseAmounts.lineNet(request.quantity(), request.unitPrice(), discount)));
        }
        return lines;
    }

    private String nextNumber(UUID companyId, PurchaseDocumentType type, int year) {
        PurchaseDocumentSequence sequence = sequenceRepository.findForUpdate(companyId, type, year)
                .orElseGet(() -> sequenceRepository.save(new PurchaseDocumentSequence(companyId, type, year)));
        return "%s-%d-%06d".formatted(type.numberPrefix(), year, sequence.next());
    }

    private PurchaseDocumentResponse response(PurchaseDocument document) {
        return PurchaseDocumentResponse.from(document, lines(document));
    }

    private List<PurchaseDocumentLine> lines(PurchaseDocument document) {
        return lineRepository.findAllByCompanyIdAndDocumentIdOrderByLineSequenceAsc(
                document.getCompanyId(), document.getId());
    }

    private PurchaseDocument require(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Documento de compra", id));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
