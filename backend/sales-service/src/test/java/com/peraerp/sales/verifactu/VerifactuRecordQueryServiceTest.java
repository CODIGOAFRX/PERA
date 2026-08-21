package com.peraerp.sales.verifactu;

import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Consulta del XML de un registro de facturación.
 *
 * <p>Es un recurso nuevo y de solo lectura, pero devuelve el documento fiscal completo de una
 * factura: quién la emite, a quién, por cuánto y con qué desglose. Lo que se prueba aquí es sobre
 * todo que no se puede leer el de otra empresa.</p>
 */
class VerifactuRecordQueryServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final String XML = "<sf:RegistroAlta><sf:IDVersion>1.0</sf:IDVersion></sf:RegistroAlta>";

    private VerifactuRecordRepository records;
    private VerifactuRecordQueryService service;

    @BeforeEach
    void setUp() {
        records = mock(VerifactuRecordRepository.class);
        VerifactuSettingsRepository settings = mock(VerifactuSettingsRepository.class);
        CurrentCompanyProvider companyProvider = mock(CurrentCompanyProvider.class);
        when(companyProvider.requireCompanyId()).thenReturn(COMPANY);
        service = new VerifactuRecordQueryService(records, settings, companyProvider);
    }

    private static VerifactuRecord record(String payloadXml) {
        return new VerifactuRecord(COMPANY, UUID.randomUUID(), VerifactuRecordType.ALTA, 1L,
                "89890001K", "FAC-2026-000001", LocalDate.of(2026, 8, 19), InvoiceKind.F1, null,
                new BigDecimal("42.00"), new BigDecimal("242.00"), null, "7E3551C7".repeat(8),
                ZonedDateTime.now(ZoneId.of("Europe/Madrid")), payloadXml);
    }

    /**
     * Se sirve la cadena almacenada, sin reformatear. Es el documento que se remite a la AEAT y
     * cualquier retoque —una sangría, un salto de línea— lo convertiría en otro documento.
     */
    @Test
    void theStoredXmlIsReturnedAsIs() {
        UUID id = UUID.randomUUID();
        when(records.findByIdAndCompanyId(id, COMPANY)).thenReturn(Optional.of(record(XML)));

        assertThat(service.payloadXml(id)).isEqualTo(XML);
    }

    /**
     * El registro se busca por identificador Y empresa. Si bastara el identificador, cualquiera con
     * una sesión válida podría leer la factura de otra empresa sabiendo su identificador.
     */
    @Test
    void aRecordOfAnotherCompanyIsNotFound() {
        UUID id = UUID.randomUUID();
        when(records.findByIdAndCompanyId(id, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payloadXml(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Registro de facturación");
    }

    /**
     * Los registros anteriores a la serialización no tienen XML. No es un fallo del servidor: es
     * que no hay nada que enseñar, y la pantalla tiene que poder distinguir un caso del otro.
     */
    @Test
    void aRecordWithoutXmlIsNotFoundEither() {
        UUID id = UUID.randomUUID();
        when(records.findByIdAndCompanyId(id, COMPANY)).thenReturn(Optional.of(record(null)));

        assertThatThrownBy(() -> service.payloadXml(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("XML");
    }
}
