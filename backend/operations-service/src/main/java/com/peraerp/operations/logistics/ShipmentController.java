package com.peraerp.operations.logistics;

import com.peraerp.operations.config.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static com.peraerp.operations.freight.FreightDtos.ApplyShipmentFreightRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentDocumentResponse;
import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentResponse;
import static com.peraerp.operations.logistics.LogisticsDtos.StatusNoteRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.TransitionTimeRequest;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

    private final ShipmentService service;
    private final ShipmentDocumentService documentService;

    public ShipmentController(ShipmentService service, ShipmentDocumentService documentService) {
        this.service = service;
        this.documentService = documentService;
    }

    @GetMapping
    PageResponse<ShipmentResponse> search(@RequestParam(required = false) ShipmentStatus status,
                                          @RequestParam(required = false) UUID carrierId,
                                          @RequestParam(required = false) UUID vehicleId,
                                          @RequestParam(required = false) UUID routeId,
                                          @RequestParam(required = false) UUID productId,
                                          @RequestParam(required = false) UUID sourceDocumentId,
                                          @RequestParam(required = false) Instant plannedFrom,
                                          @RequestParam(required = false) Instant plannedTo,
                                          @RequestParam(required = false) String query,
                                          Pageable pageable) {
        return PageResponse.from(service.search(status, carrierId, vehicleId, routeId, productId, sourceDocumentId,
                plannedFrom, plannedTo, query, pageable));
    }

    @GetMapping("/{id}")
    ShipmentResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ShipmentResponse create(@Valid @RequestBody ShipmentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    ShipmentResponse update(@PathVariable UUID id, @Valid @RequestBody ShipmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/freight/resolve")
    ShipmentResponse resolveFreight(@PathVariable UUID id,
                                    @Valid @RequestBody ApplyShipmentFreightRequest request) {
        return service.resolveFreight(id, request);
    }

    @PostMapping(value = "/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ShipmentDocumentResponse uploadDocument(@PathVariable UUID id,
                                            @RequestParam String documentType,
                                            @RequestParam MultipartFile file) {
        return documentService.upload(id, documentType, file);
    }

    @GetMapping("/{shipmentId}/documents/{documentId}")
    ResponseEntity<ByteArrayResource> downloadDocument(
            @PathVariable UUID shipmentId, @PathVariable UUID documentId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ShipmentDocumentService.ShipmentDocumentDownload download =
                documentService.download(shipmentId, documentId);
        ShipmentDocument document = download.document();
        String etag = "\"" + document.getSha256() + "\"";
        CacheControl cache = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate().mustRevalidate();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cache)
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.getOriginalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getMediaType()))
                .contentLength(download.content().length)
                .eTag(etag)
                .cacheControl(cache)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new ByteArrayResource(download.content()));
    }

    @DeleteMapping("/{shipmentId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDocument(@PathVariable UUID shipmentId, @PathVariable UUID documentId) {
        service.deleteDocument(shipmentId, documentId);
    }

    @PostMapping("/{id}/transitions/start-packing")
    ShipmentResponse startPacking(@PathVariable UUID id) {
        return service.startPacking(id);
    }

    @PostMapping("/{id}/transitions/mark-ready")
    ShipmentResponse markReady(@PathVariable UUID id) {
        return service.markReady(id);
    }

    @PostMapping("/{id}/transitions/dispatch")
    ShipmentResponse dispatch(@PathVariable UUID id,
                              @Valid @RequestBody(required = false) TransitionTimeRequest request) {
        return service.dispatch(id, request);
    }

    @PostMapping("/{id}/transitions/mark-in-transit")
    ShipmentResponse markInTransit(@PathVariable UUID id) {
        return service.markInTransit(id);
    }

    @PostMapping("/{id}/transitions/arrive")
    ShipmentResponse arrive(@PathVariable UUID id,
                            @Valid @RequestBody(required = false) TransitionTimeRequest request) {
        return service.arrive(id, request);
    }

    @PostMapping("/{id}/transitions/deliver")
    ShipmentResponse deliver(@PathVariable UUID id,
                             @Valid @RequestBody(required = false) TransitionTimeRequest request) {
        return service.deliver(id, request);
    }

    @PostMapping("/{id}/transitions/report-exception")
    ShipmentResponse reportException(@PathVariable UUID id,
                                     @Valid @RequestBody StatusNoteRequest request) {
        return service.reportException(id, request);
    }

    @PostMapping("/{id}/transitions/resolve-exception")
    ShipmentResponse resolveException(@PathVariable UUID id) {
        return service.resolveException(id);
    }

    @PostMapping("/{id}/transitions/cancel")
    ShipmentResponse cancel(@PathVariable UUID id, @Valid @RequestBody StatusNoteRequest request) {
        return service.cancel(id, request);
    }
}
