package com.peraerp.sales.verifactu;

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
