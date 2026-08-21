package com.peraerp.sales.verifactu;

import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.verifactu.api.VerifactuRecordResponse;
import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import com.peraerp.sales.verifactu.qr.VerifactuQrPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Consulta de los registros de facturación de un documento.
 *
 * <p>Solo lectura. Los registros los crea {@code VerifactuChainService} y nadie más.</p>
 */
@Service
public class VerifactuRecordQueryService {

    private final VerifactuRecordRepository records;
    private final VerifactuSettingsRepository settings;
    private final CurrentCompanyProvider companyProvider;

    public VerifactuRecordQueryService(VerifactuRecordRepository records, VerifactuSettingsRepository settings,
                                       CurrentCompanyProvider companyProvider) {
        this.records = records;
        this.settings = settings;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<VerifactuRecordResponse> findByDocument(UUID documentId) {
        UUID companyId = companyProvider.requireCompanyId();
        VerifactuEnvironment environment = settings.findByCompanyId(companyId)
                .map(VerifactuSettings::getEnvironment)
                .orElse(VerifactuEnvironment.TEST);
        return records.findByCompanyIdAndDocumentIdOrderBySequenceNumberAsc(companyId, documentId).stream()
                .map(record -> VerifactuRecordResponse.from(record, qrPayload(record, environment)))
                .toList();
    }

    /**
     * Devuelve el XML del registro tal y como se remitirá a la AEAT.
     *
     * <p>Va en su propio recurso y no dentro del listado: son varios kilobytes por registro y el
     * listado se pide cada vez que se abre una factura. Quien quiera verlo, que lo pida.</p>
     *
     * <p>Se sirve el XML almacenado, no uno reconstruido al vuelo. Un registro es un hecho
     * fechado: reconstruirlo con el código de hoy mostraría algo que nunca se remitió.</p>
     */
    @Transactional(readOnly = true)
    public String payloadXml(UUID recordId) {
        VerifactuRecord record = records.findByIdAndCompanyId(recordId, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Registro de facturación", recordId));
        if (record.getPayloadXml() == null || record.getPayloadXml().isBlank()) {
            // Los registros anteriores a la serialización, y las anulaciones, no lo tienen.
            throw new ResourceNotFoundException("XML del registro de facturación", recordId);
        }
        return record.getPayloadXml();
    }

    /**
     * El QR solo tiene sentido en un registro de alta: identifica una factura expedida. Una
     * anulación no se coteja: lo que se coteja es la factura, y esa ya tiene su propio QR.
     */
    private String qrPayload(VerifactuRecord record, VerifactuEnvironment environment) {
        if (record.getRecordType() != com.peraerp.sales.verifactu.domain.VerifactuRecordType.ALTA) {
            return null;
        }
        return VerifactuQrPayload.of(environment, record.getIssuerTaxId(), record.getInvoiceNumber(),
                record.getInvoiceDate(), record.getTotalAmount());
    }
}
