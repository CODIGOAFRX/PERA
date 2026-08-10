package com.peraerp.operations.logistics;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.logistics.storage.ShipmentDocumentStorage;
import com.peraerp.operations.logistics.storage.ShipmentDocumentStorageException;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentDocumentServiceTest {

    @Mock ShipmentRepository shipmentRepository;
    @Mock ShipmentDocumentRepository documentRepository;
    @Mock ShipmentDocumentStorage storage;
    @Mock CurrentCompanyProvider companyProvider;

    private ShipmentDocumentService service;
    private UUID companyId;
    private Shipment shipment;

    @BeforeEach
    void setUp() {
        service = new ShipmentDocumentService(shipmentRepository, documentRepository, storage, companyProvider, 64);
        companyId = UUID.randomUUID();
        shipment = new Shipment(companyId, "SHP-1", "EUR");
        ReflectionTestUtils.setField(shipment, "id", UUID.randomUUID());
        shipment.updatePlan(null, null, null, null, null, null, null, BigDecimal.ZERO,
                "EUR", null, null);
    }

    @Test
    void multipartUploadValidatesMagicAndPersistsServerGeneratedMetadata() {
        stubShipment();
        byte[] content = "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII);
        String key = key();
        when(storage.store(companyId, shipment.getId(), content))
                .thenReturn(new ShipmentDocumentStorage.StoredObject(key, content.length));
        when(documentRepository.saveAndFlush(any(ShipmentDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.upload(shipment.getId(), "DELIVERY_NOTE",
                new MockMultipartFile("file", "delivery-note.pdf", "application/pdf", content));

        assertThat(response.storageKey()).isEqualTo(key);
        assertThat(response.mediaType()).isEqualTo("application/pdf");
        assertThat(response.sha256()).isEqualTo(sha256(content));
        assertThat(response.sizeBytes()).isEqualTo(content.length);
    }

    @Test
    void rejectsMimeSpoofBeforeWritingAnything() {
        stubShipment();
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

        assertThatThrownBy(() -> service.upload(shipment.getId(), "DELIVERY_NOTE",
                new MockMultipartFile("file", "fake.pdf", "application/pdf", png)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("MIME");

        verify(storage, never()).store(any(), any(), any());
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void enforcesConfiguredMaximumBeforeStorage() {
        stubShipment();
        byte[] oversized = new byte[65];

        assertThatThrownBy(() -> service.upload(shipment.getId(), "DELIVERY_NOTE",
                new MockMultipartFile("file", "large.pdf", "application/pdf", oversized)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("límite");

        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void storageFailureCannotCreateDatabaseMetadata() {
        stubShipment();
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        when(storage.store(companyId, shipment.getId(), content))
                .thenThrow(new ShipmentDocumentStorageException("disk unavailable"));

        assertThatThrownBy(() -> service.upload(shipment.getId(), "DELIVERY_NOTE",
                new MockMultipartFile("file", "document.pdf", "application/pdf", content)))
                .isInstanceOf(ShipmentDocumentStorageException.class);

        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void databaseFailureCompensatesTheNewlyStoredObject() {
        stubShipment();
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        String key = key();
        when(storage.store(companyId, shipment.getId(), content))
                .thenReturn(new ShipmentDocumentStorage.StoredObject(key, content.length));
        when(documentRepository.saveAndFlush(any(ShipmentDocument.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.upload(shipment.getId(), "DELIVERY_NOTE",
                new MockMultipartFile("file", "document.pdf", "application/pdf", content)))
                .isInstanceOf(IllegalStateException.class);

        verify(storage).delete(companyId, shipment.getId(), key);
    }

    @Test
    void transactionRollbackCleansAStoredUpload() {
        stubShipment();
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        String key = key();
        when(storage.store(companyId, shipment.getId(), content))
                .thenReturn(new ShipmentDocumentStorage.StoredObject(key, content.length));
        when(documentRepository.saveAndFlush(any(ShipmentDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.upload(shipment.getId(), "DELIVERY_NOTE",
                    new MockMultipartFile("file", "document.pdf", "application/pdf", content));
            verify(storage, never()).delete(any(), any(), any());

            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                    synchronization.afterCompletion(
                            org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(storage).delete(companyId, shipment.getId(), key);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void metadataOnlyInternalFlowRejectsObjectsThatDoNotExist() {
        stubShipment();
        String key = key();
        when(storage.exists(companyId, shipment.getId(), key)).thenReturn(false);
        var request = new LogisticsDtos.ShipmentDocumentRequest("DELIVERY_NOTE", "document.pdf", key,
                "application/pdf", "a".repeat(64), 10);

        assertThatThrownBy(() -> service.registerMetadata(shipment.getId(), request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("objeto");

        verify(documentRepository, never()).save(any());
    }

    @Test
    void tenantScopedDownloadVerifiesChecksumAndNeverFallsBackToIdOnly() {
        stubShipment();
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        ShipmentDocument document = document(content);
        when(documentRepository.findByIdAndCompanyIdAndShipmentId(
                document.getId(), companyId, shipment.getId())).thenReturn(Optional.of(document));
        when(storage.read(companyId, shipment.getId(), document.getStorageKey(), 64)).thenReturn(content);

        var download = service.download(shipment.getId(), document.getId());

        assertThat(download.content()).isEqualTo(content);
        verify(documentRepository).findByIdAndCompanyIdAndShipmentId(document.getId(), companyId, shipment.getId());
        verify(documentRepository, never()).findById(document.getId());
    }

    @Test
    void crossTenantDocumentIsReportedAsNotFound() {
        stubShipment();
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findByIdAndCompanyIdAndShipmentId(documentId, companyId, shipment.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(shipment.getId(), documentId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(storage, never()).read(any(), any(), any(), anyLong());
    }

    @Test
    void deletionRemovesMetadataFirstAndThenTheTenantObject() {
        stubShipment();
        ShipmentDocument document = document("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        when(documentRepository.findByIdAndCompanyIdAndShipmentId(
                document.getId(), companyId, shipment.getId())).thenReturn(Optional.of(document));

        service.delete(shipment.getId(), document.getId());

        verify(documentRepository).delete(document);
        verify(documentRepository).flush();
        verify(storage).delete(companyId, shipment.getId(), document.getStorageKey());
    }

    @Test
    void physicalDeleteWaitsUntilTheDatabaseTransactionCommits() {
        stubShipment();
        ShipmentDocument document = document("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        when(documentRepository.findByIdAndCompanyIdAndShipmentId(
                document.getId(), companyId, shipment.getId())).thenReturn(Optional.of(document));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delete(shipment.getId(), document.getId());
            verify(storage, never()).delete(any(), any(), any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

            verify(storage).delete(companyId, shipment.getId(), document.getStorageKey());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void stubShipment() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(shipmentRepository.findByIdAndCompanyId(shipment.getId(), companyId))
                .thenReturn(Optional.of(shipment));
    }

    private ShipmentDocument document(byte[] content) {
        ShipmentDocument document = new ShipmentDocument(companyId, shipment.getId(), "DELIVERY_NOTE",
                "document.pdf", key(), "application/pdf", sha256(content), content.length);
        ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
        return document;
    }

    private String key() {
        return "companies/" + companyId + "/shipments/" + shipment.getId() + "/" + UUID.randomUUID() + ".bin";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
