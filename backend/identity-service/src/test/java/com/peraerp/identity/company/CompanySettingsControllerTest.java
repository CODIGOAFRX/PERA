package com.peraerp.identity.company;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanySettingsControllerTest {

    @Test
    void servesPrivateCacheHeadersAndEtag() {
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CompanyLogoService logoService = mock(CompanyLogoService.class);
        String sha = "a".repeat(64);
        byte[] content = new byte[]{1, 2, 3};
        when(logoService.downloadCurrent(null)).thenReturn(
                CompanyLogoService.CompanyLogoDownload.content("image/png", sha, "\"" + sha + "\"", content));
        CompanySettingsController controller = new CompanySettingsController(settingsService, logoService);

        ResponseEntity<byte[]> response = controller.downloadCurrentLogo(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"" + sha + "\"");
        assertThat(response.getHeaders().getCacheControl()).contains("private").contains("max-age=3600");
        assertThat(response.getBody()).containsExactly(content);
    }

    @Test
    void returns304WithoutABodyForMatchingEtag() {
        CompanySettingsService settingsService = mock(CompanySettingsService.class);
        CompanyLogoService logoService = mock(CompanyLogoService.class);
        String sha = "b".repeat(64);
        when(logoService.downloadCurrent("\"" + sha + "\"")).thenReturn(
                CompanyLogoService.CompanyLogoDownload.notModified("image/png", sha, "\"" + sha + "\""));
        CompanySettingsController controller = new CompanySettingsController(settingsService, logoService);

        ResponseEntity<byte[]> response = controller.downloadCurrentLogo("\"" + sha + "\"");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"" + sha + "\"");
        assertThat(response.getBody()).isNull();
    }
}
