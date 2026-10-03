package com.peraerp.operations.claims;

import com.peraerp.operations.claims.ClaimDtos.CatalogItemRequest;
import com.peraerp.operations.claims.ClaimDtos.CatalogItemResponse;
import com.peraerp.operations.claims.ClaimDtos.ClaimRequest;
import com.peraerp.operations.claims.ClaimDtos.ClaimResponse;
import com.peraerp.operations.claims.ClaimDtos.CloseClaimRequest;
import com.peraerp.operations.claims.ClaimDtos.CommentRequest;
import com.peraerp.operations.config.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class ClaimController {

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/claim-catalog")
    List<CatalogItemResponse> catalog() {
        return service.catalog();
    }

    @PostMapping("/api/v1/claim-catalog")
    @ResponseStatus(HttpStatus.CREATED)
    CatalogItemResponse createCatalogItem(@Valid @RequestBody CatalogItemRequest request) {
        return service.createCatalogItem(request);
    }

    @PutMapping("/api/v1/claim-catalog/{id}")
    CatalogItemResponse updateCatalogItem(@PathVariable UUID id, @Valid @RequestBody CatalogItemRequest request) {
        return service.updateCatalogItem(id, request);
    }

    @GetMapping("/api/v1/claims")
    PageResponse<ClaimResponse> search(@RequestParam(required = false) ClaimStatus status,
                                       @RequestParam(required = false) UUID customerId,
                                       @RequestParam(required = false) UUID reasonId,
                                       @RequestParam(required = false) LocalDate fromDate,
                                       @RequestParam(required = false) LocalDate toDate,
                                       @RequestParam(defaultValue = "false") boolean overdue,
                                       @RequestParam(required = false) String query,
                                       Pageable pageable) {
        return PageResponse.from(service.search(status, customerId, reasonId, fromDate, toDate, overdue, query,
                pageable));
    }

    @GetMapping("/api/v1/claims/{id}")
    ClaimResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/api/v1/claims")
    @ResponseStatus(HttpStatus.CREATED)
    ClaimResponse create(@Valid @RequestBody ClaimRequest request) {
        return service.create(request);
    }

    @PutMapping("/api/v1/claims/{id}")
    ClaimResponse update(@PathVariable UUID id, @Valid @RequestBody ClaimRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/v1/claims/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/api/v1/claims/{id}/close")
    ClaimResponse close(@PathVariable UUID id, @Valid @RequestBody CloseClaimRequest request) {
        return service.close(id, request);
    }

    @PostMapping("/api/v1/claims/{id}/reopen")
    ClaimResponse reopen(@PathVariable UUID id) {
        return service.reopen(id);
    }

    @PostMapping("/api/v1/claims/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    ClaimResponse comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest request) {
        return service.addComment(id, request.text());
    }
}
