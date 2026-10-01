package com.peraerp.operations.inventory;

import com.peraerp.operations.config.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.peraerp.operations.inventory.InventoryDtos.StockAdjustmentRequest;
import static com.peraerp.operations.inventory.InventoryDtos.StockLevelResponse;
import static com.peraerp.operations.inventory.InventoryDtos.StockMovementResponse;
import static com.peraerp.operations.inventory.InventoryDtos.StockTransferRequest;

@RestController
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/stock-levels")
    PageResponse<StockLevelResponse> searchLevels(@RequestParam(required = false) UUID warehouseId,
                                                  @RequestParam(required = false) UUID productId,
                                                  @RequestParam(defaultValue = "false") boolean onlyInStock,
                                                  @RequestParam(required = false) String query,
                                                  Pageable pageable) {
        return PageResponse.from(service.searchLevels(warehouseId, productId, onlyInStock, query, pageable));
    }

    @GetMapping("/api/v1/stock-movements")
    PageResponse<StockMovementResponse> searchMovements(@RequestParam(required = false) UUID warehouseId,
                                                        @RequestParam(required = false) UUID productId,
                                                        @RequestParam(required = false) StockMovementType type,
                                                        @RequestParam(required = false) Instant occurredFrom,
                                                        @RequestParam(required = false) Instant occurredTo,
                                                        @RequestParam(required = false) String query,
                                                        Pageable pageable) {
        return PageResponse.from(service.searchMovements(warehouseId, productId, type, occurredFrom, occurredTo,
                query, pageable));
    }

    @PostMapping("/api/v1/stock-movements/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    StockMovementResponse adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return service.adjust(request);
    }

    @PostMapping("/api/v1/stock-movements/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    List<StockMovementResponse> transfer(@Valid @RequestBody StockTransferRequest request) {
        return service.transfer(request);
    }
}
