package com.peraerp.sales.verifactu.xml;

import com.peraerp.sales.verifactu.domain.ExemptionCause;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.OperationQualification;
import com.peraerp.sales.verifactu.domain.RectificationType;
import com.peraerp.sales.verifactu.domain.TaxIdentificationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Todo lo que necesita un {@code RegistroAlta} para serializarse.
 *
 * <p>Es un objeto plano a propósito: el escritor no consulta la base de datos ni conoce entidades,
 * de modo que un registro se puede reproducir años después a partir de datos guardados.</p>
 */
public record RegistroAltaContent(
        String issuerTaxId,
        String issuerLegalName,
        String invoiceNumber,
        LocalDate issueDate,
        InvoiceKind invoiceKind,
        RectificationType rectificationType,
        List<RectifiedInvoice> rectifiedInvoices,
        String operationDescription,
        Recipient recipient,
        List<BreakdownDetail> breakdown,
        BigDecimal totalTaxAmount,
        BigDecimal totalAmount,
        PreviousRecord previousRecord,
        SoftwareSystem software,
        ZonedDateTime generatedAt,
        String fingerprint) {

    /** Factura rectificada, para el bloque {@code FacturasRectificadas}. */
    public record RectifiedInvoice(String issuerTaxId, String invoiceNumber, LocalDate issueDate) {
    }

    /**
     * Destinatario de la factura.
     *
     * <p>Un residente se identifica por {@code NIF}; cualquier otro caso por {@code IDOtro} con el
     * código del documento y el país que lo expide.</p>
     */
    public record Recipient(String legalName, String taxId, TaxIdentificationType identificationType,
                            String countryCode) {
    }

    /** Una entrada del {@code Desglose}. */
    public record BreakdownDetail(String regimeKey, OperationQualification qualification,
                                  ExemptionCause exemptionCause, BigDecimal taxRate,
                                  BigDecimal taxableBase, BigDecimal taxAmount) {
    }

    /** Registro anterior de la cadena. {@code null} en el primero. */
    public record PreviousRecord(String issuerTaxId, String invoiceNumber, LocalDate issueDate,
                                 String fingerprint) {
    }

    /**
     * Identidad del sistema informático de facturación ante la AEAT.
     *
     * @param multipleTaxpayers si esta instalación está sirviendo ahora mismo a más de un obligado
     *                          tributario. No es una capacidad del programa —eso es
     *                          {@code TipoUsoPosibleMultiOT}, que en PERA siempre es sí— sino un
     *                          hecho de la instalación en el momento de generar el registro
     */
    public record SoftwareSystem(String developerLegalName, String developerTaxId, String name,
                                 String id, String version, String installationNumber,
                                 boolean multipleTaxpayers) {
    }
}
