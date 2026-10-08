package com.peraerp.sales.verifactu;

import com.peraerp.sales.verifactu.api.VerifactuRemissionSummary;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.domain.VerifactuState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

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
    private final JdbcTemplate jdbc;

    public VerifactuRecordQueryService(VerifactuRecordRepository records, VerifactuSettingsRepository settings,
                                       CurrentCompanyProvider companyProvider, JdbcTemplate jdbc) {
        this.records = records;
        this.settings = settings;
        this.companyProvider = companyProvider;
        this.jdbc = jdbc;
    }

    /**
     * Registros de alta de la empresa en los estados pedidos, los más recientes primero. Sin estados,
     * todos. Es la lista de la pantalla de seguimiento.
     */
    @Transactional(readOnly = true)
    public Page<VerifactuRecordResponse> search(List<VerifactuState> states, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        VerifactuEnvironment environment = environment(companyId);
        List<VerifactuState> wanted = states == null || states.isEmpty() ? List.of(VerifactuState.values()) : states;
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100));
        return records.findByCompanyIdAndRecordTypeAndStateInOrderBySequenceNumberDesc(companyId,
                        VerifactuRecordType.ALTA, wanted, page)
                .map(record -> VerifactuRecordResponse.from(record, qrPayload(record, environment)));
    }

    /** Situación de la remisión a la AEAT: configuración, conexión, esperas y recuento por estado. */
    @Transactional(readOnly = true)
    public VerifactuRemissionSummary remission() {
        UUID companyId = companyProvider.requireCompanyId();
        var configuration = settings.findByCompanyId(companyId);
        Map<VerifactuState, Long> counts = new EnumMap<>(VerifactuState.class);
        for (VerifactuState state : VerifactuState.values()) {
            counts.put(state, 0L);
        }
        for (Object[] row : records.countAltasByState(companyId)) {
            counts.put((VerifactuState) row[0], (Long) row[1]);
        }
        var connection = jdbc.query("""
                SELECT enabled, next_send_at, last_sent_at, failures, last_error FROM fiscal_connections
                WHERE company_id = ? AND provider = 'AEAT'
                """, (rs, n) -> new Object[]{rs.getBoolean(1), instant(rs.getTimestamp(2)), instant(rs.getTimestamp(3)),
                rs.getInt(4), rs.getString(5)}, companyId).stream().findFirst().orElse(null);
        return new VerifactuRemissionSummary(
                configuration.map(VerifactuSettings::isEnabled).orElse(false),
                configuration.map(VerifactuSettings::getEnvironment).orElse(VerifactuEnvironment.TEST),
                connection != null,
                connection != null && (Boolean) connection[0],
                connection == null ? null : (Instant) connection[1],
                connection == null ? null : (Instant) connection[2],
                connection == null ? 0 : (Integer) connection[3],
                connection == null ? null : (String) connection[4],
                counts);
    }

    private static Instant instant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private VerifactuEnvironment environment(UUID companyId) {
        return settings.findByCompanyId(companyId).map(VerifactuSettings::getEnvironment)
                .orElse(VerifactuEnvironment.TEST);
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

    @Transactional(readOnly = true)
    public List<UUID> findRegisteredDocuments(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        return records.findDocumentIdsWithRegistration(companyProvider.requireCompanyId(), documentIds);
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
