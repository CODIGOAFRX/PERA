package com.peraerp.sales.verifactu;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.DocumentAmountsCalculator;
import com.peraerp.sales.document.DocumentLine;
import com.peraerp.sales.document.DocumentType;
import com.peraerp.sales.verifactu.chain.ChainedRecordRequest;
import com.peraerp.sales.verifactu.chain.VerifactuChainService;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuMode;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enganche entre la emisión de una factura y la cadena de Veri*Factu.
 *
 * <p>Dos reglas mandan aquí. Una empresa sin Veri*Factu activado sigue facturando como siempre: la
 * activación es decisión suya, no del producto. Y una empresa con Veri*Factu activado no puede
 * expedir una factura sin su registro, porque una factura sin registro es un agujero que después
 * no se puede rellenar.</p>
 */
class VerifactuIssuanceServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 8, 19);
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-19T09:30:00Z"), ZoneId.of("UTC"));

    private VerifactuSettingsRepository settings;
    private VerifactuChainService chain;
    private VerifactuIssuanceService service;

    @BeforeEach
    void setUp() {
        settings = mock(VerifactuSettingsRepository.class);
        chain = mock(VerifactuChainService.class);
        // El encadenado devuelve siempre un registro; el mock, sin esto, devolvería null y
        // enmascararía el contrato real del servicio.
        when(chain.append(any(), any())).thenReturn(mock(VerifactuRecord.class));
        service = new VerifactuIssuanceService(settings, chain, FIXED);
    }

    private VerifactuSettings enabledSettings() {
        VerifactuSettings value = new VerifactuSettings(COMPANY, "89890001K", "EMPRESA DE PRUEBAS S.L.",
                "PERA ERP", "01", "0.1.0", "B00000000");
        value.configure(true, VerifactuMode.VERIFACTU, VerifactuEnvironment.TEST, "89890001K",
                "EMPRESA DE PRUEBAS S.L.", "01", "S1", "Europe/Madrid");
        return value;
    }

    private CommercialDocument invoice(DocumentType type, String baseCurrency) {
        CommercialDocument document = new CommercialDocument(COMPANY, "F-2026-0000015", type,
                UUID.randomUUID(), "9435", "ALUMINIOS FAMA S.L.", ISSUE_DATE, null, "EUR", null, null, null);
        document.addLine(new DocumentLine(null, "P-1", "Producto de prueba", new BigDecimal("2"),
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("21")));
        document.recalculate(new DocumentAmountsCalculator());
        document.applyCurrencySnapshot(baseCurrency, BigDecimal.ONE, ISSUE_DATE, "TEST");
        return document;
    }

    private CommercialDocument issuedInvoice() {
        CommercialDocument document = invoice(DocumentType.INVOICE, "EUR");
        document.confirm();
        return document;
    }

    // --- cuándo NO se genera registro ---

    @Test
    void companyWithoutSettingsKeepsInvoicingNormally() {
        when(settings.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThat(service.recordIssuance(issuedInvoice())).isEmpty();
        verify(chain, never()).append(any(), any());
    }

    @Test
    void disabledCompanyGeneratesNoRecord() {
        VerifactuSettings disabled = new VerifactuSettings(COMPANY, "89890001K", "EMPRESA DE PRUEBAS S.L.",
                "PERA ERP", "01", "0.1.0", "B00000000");
        when(settings.findByCompanyId(COMPANY)).thenReturn(Optional.of(disabled));

        assertThat(service.recordIssuance(issuedInvoice())).isEmpty();
        verify(chain, never()).append(any(), any());
    }

    @Test
    void deliveryNotesNeverGenerateRecords() {
        CommercialDocument deliveryNote = invoice(DocumentType.DELIVERY_NOTE, "EUR");
        deliveryNote.confirm();

        assertThat(service.recordIssuance(deliveryNote)).isEmpty();
        verify(chain, never()).append(any(), any());
    }

    // --- cuándo SÍ ---

    @Test
    void issuedInvoiceIsChainedWithTheIssuerFromSettings() {
        when(settings.findByCompanyId(COMPANY)).thenReturn(Optional.of(enabledSettings()));

        service.recordIssuance(issuedInvoice());

        ArgumentCaptor<ChainedRecordRequest> captor = ArgumentCaptor.forClass(ChainedRecordRequest.class);
        verify(chain).append(any(), captor.capture());
        ChainedRecordRequest request = captor.getValue();
        assertThat(request.recordType()).isEqualTo(VerifactuRecordType.ALTA);
        assertThat(request.issuerTaxId()).isEqualTo("89890001K");
        assertThat(request.invoiceNumber()).isEqualTo("F-2026-0000015");
        assertThat(request.invoiceDate()).isEqualTo(ISSUE_DATE);
        assertThat(request.invoiceKind()).isEqualTo(InvoiceKind.F1);
    }

    @Test
    void amountsComeFromTheBaseCurrencyFields() {
        when(settings.findByCompanyId(COMPANY)).thenReturn(Optional.of(enabledSettings()));

        service.recordIssuance(issuedInvoice());

        ArgumentCaptor<ChainedRecordRequest> captor = ArgumentCaptor.forClass(ChainedRecordRequest.class);
        verify(chain).append(any(), captor.capture());
        assertThat(captor.getValue().totalTaxAmount()).isEqualByComparingTo("42.00");
        assertThat(captor.getValue().totalAmount()).isEqualByComparingTo("242.00");
    }

    @Test
    void generationTimestampUsesTheCompanyTimeZoneNotTheServerOne() {
        when(settings.findByCompanyId(COMPANY)).thenReturn(Optional.of(enabledSettings()));

        service.recordIssuance(issuedInvoice());

        ArgumentCaptor<ChainedRecordRequest> captor = ArgumentCaptor.forClass(ChainedRecordRequest.class);
        verify(chain).append(any(), captor.capture());
        // 09:30 UTC son las 11:30 en Madrid con horario de verano.
        assertThat(captor.getValue().generatedAt().getZone()).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(captor.getValue().generatedAt().getHour()).isEqualTo(11);
        assertThat(captor.getValue().generatedAt().getOffset().getTotalSeconds()).isEqualTo(7200);
    }

    @Test
    void payloadIsLeftEmptyUntilTheXmlSerializerExists() {
        when(settings.findByCompanyId(COMPANY)).thenReturn(Optional.of(enabledSettings()));

        service.recordIssuance(issuedInvoice());

        ArgumentCaptor<ChainedRecordRequest> captor = ArgumentCaptor.forClass(ChainedRecordRequest.class);
        verify(chain).append(any(), captor.capture());
        assertThat(captor.getValue().payloadXml()).isNull();
    }

    // --- divisa ---

    @Test
    void nonEuroBaseCurrencyIsRejectedInsteadOfSendingWrongAmounts() {
        when(settings.findByCompanyId(COMPANY)).thenReturn(Optional.of(enabledSettings()));
        CommercialDocument document = invoice(DocumentType.INVOICE, "USD");
        document.confirm();

        assertThatThrownBy(() -> service.recordIssuance(document))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("euros");
        verify(chain, never()).append(any(), any());
    }
}
