package com.peraerp.operations.logistics;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShipmentControllerDocumentTest {

    @Test
    void downloadUsesPrivateCacheEtagAttachmentAndNoSniff() {
        ShipmentService shipmentService = mock(ShipmentService.class);
        ShipmentDocumentService documentService = mock(ShipmentDocumentService.class);
        ShipmentController controller = new ShipmentController(shipmentService, documentService);
        UUID companyId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        byte[] content = new byte[]{1, 2, 3};
        String sha = "a".repeat(64);
        ShipmentDocument document = new ShipmentDocument(companyId, shipmentId, "DELIVERY_NOTE",
                "delivery note.pdf", "opaque-key", "application/pdf", sha, content.length);
        ReflectionTestUtils.setField(document, "id", documentId);
        when(documentService.download(shipmentId, documentId))
                .thenReturn(new ShipmentDocumentService.ShipmentDocumentDownload(document, content));

        var response = controller.downloadDocument(shipmentId, documentId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"" + sha + "\"");
        assertThat(response.getHeaders().getCacheControl()).contains("private").contains("must-revalidate");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;").contains("delivery");

        var notModified = controller.downloadDocument(shipmentId, documentId, "\"" + sha + "\"");
        assertThat(notModified.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(notModified.getBody()).isNull();
    }
}
