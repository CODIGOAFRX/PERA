package com.peraerp.sales.numbering;

import com.peraerp.sales.document.DocumentType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/numbering-schemes")
public class NumberingSchemeController {

    private final NumberingSchemeService service;

    public NumberingSchemeController(NumberingSchemeService service) {
        this.service = service;
    }

    @GetMapping
    Page<NumberingSchemeResponse> search(@RequestParam(required = false) String query,
                                         @RequestParam(required = false) DocumentType documentType,
                                         @RequestParam(required = false) Boolean active,
                                         Pageable pageable) {
        return service.search(query, documentType, active, pageable);
    }

    @GetMapping("/{id}")
    NumberingSchemeResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NumberingSchemeResponse create(@Valid @RequestBody NumberingSchemeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    NumberingSchemeResponse update(@PathVariable UUID id, @Valid @RequestBody NumberingSchemeRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/preview")
    NumberingPreviewResponse preview(@PathVariable UUID id,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(required = false) Long sequence) {
        return service.preview(id, date, sequence);
    }
}
