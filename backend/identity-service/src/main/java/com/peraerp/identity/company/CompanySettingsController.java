package com.peraerp.identity.company;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/company-settings")
public class CompanySettingsController {

    private final CompanySettingsService settingsService;
    private final CompanyLogoService logoService;

    public CompanySettingsController(CompanySettingsService settingsService, CompanyLogoService logoService) {
        this.settingsService = settingsService;
        this.logoService = logoService;
    }

    @GetMapping("/current")
    CompanySettingsResponse findCurrent() {
        return settingsService.findCurrent();
    }

    @PutMapping("/current")
    CompanySettingsResponse updateCurrent(@Valid @RequestBody CompanySettingsRequest request) {
        return settingsService.updateCurrent(request);
    }

    @PutMapping(path = "/current/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    CompanySettingsResponse uploadCurrentLogo(@RequestPart("file") MultipartFile file) {
        return logoService.uploadCurrent(file);
    }

    @GetMapping("/current/logo")
    ResponseEntity<byte[]> downloadCurrentLogo(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        CompanyLogoService.CompanyLogoDownload logo = logoService.downloadCurrent(ifNoneMatch);
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(logo.etag());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().noTransform());
        if (logo.notModified()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        }
        byte[] content = logo.content();
        headers.setContentType(MediaType.parseMediaType(logo.contentType()));
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @DeleteMapping("/current/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCurrentLogo() {
        logoService.deleteCurrent();
    }
}
