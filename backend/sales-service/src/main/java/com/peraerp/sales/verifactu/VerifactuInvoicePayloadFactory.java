package com.peraerp.sales.verifactu;

import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.DocumentLine;
import com.peraerp.sales.verifactu.chain.RecordPayloadFactory;
import com.peraerp.sales.verifactu.domain.OperationQualification;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import com.peraerp.sales.verifactu.mapping.TaxBreakdownAggregator;
import com.peraerp.sales.verifactu.mapping.TaxBreakdownEntry;
import com.peraerp.sales.verifactu.xml.RegistroAltaContent;
import com.peraerp.sales.verifactu.xml.RegistroAltaXmlWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce una factura de PERA al contenido de un registro de alta y lo serializa.
 *
 * <p>Es la última pieza del camino: el documento comercial entra por un lado y sale el XML que se
 * remitirá a la AEAT. Todo lo que necesita viene de datos ya congelados en la factura, de modo que
 * el registro puede reconstruirse años después sin depender de los maestros.</p>
 */
@Component
public class VerifactuInvoicePayloadFactory {

    private static final String DEFAULT_DESCRIPTION = "Venta de bienes o servicios";
    private static final int MAX_DESCRIPTION = 500;

    private final TaxBreakdownAggregator breakdownAggregator;
    private final RegistroAltaXmlWriter xmlWriter;
    private final VerifactuSettingsRepository settingsRepository;
    private final String installationNumber;
    private final String developerLegalName;

    public VerifactuInvoicePayloadFactory(TaxBreakdownAggregator breakdownAggregator,
                                          RegistroAltaXmlWriter xmlWriter,
                                          VerifactuSettingsRepository settingsRepository,
                                          @Value("${pera.verifactu.software.installation-number:}")
                                          String installationNumber,
                                          @Value("${pera.verifactu.software.developer-name:}")
                                          String developerLegalName) {
        this.breakdownAggregator = breakdownAggregator;
        this.xmlWriter = xmlWriter;
        this.settingsRepository = settingsRepository;
        this.installationNumber = installationNumber;
        this.developerLegalName = developerLegalName;
    }

    public RecordPayloadFactory forInvoice(CommercialDocument invoice, VerifactuSettings settings) {
        List<TaxBreakdownEntry> breakdown = breakdownAggregator.aggregate(invoice.getLines(),
                settings.getDefaultRegimeKey(), defaultQualification(settings));

        return context -> xmlWriter.write(new RegistroAltaContent(
                settings.getIssuerTaxId(),
                settings.getIssuerLegalName(),
                invoice.getDocumentNumber(),
                invoice.getIssueDate(),
                invoice.getInvoiceKind(),
                invoice.getRectificationType(),
                rectifiedInvoices(invoice, settings),
                operationDescription(invoice),
                recipient(invoice),
                breakdown.stream().map(VerifactuInvoicePayloadFactory::toDetail).toList(),
                invoice.getBaseTaxAmount(),
                invoice.getBaseTotalAmount(),
                previousRecord(context),
                software(settings),
                context.generatedAt(),
                context.fingerprint()));
    }

    /**
     * La calificación por defecto de la empresa se guarda como literal ({@code S1}, {@code S2}…)
     * porque es lo que viaja a la AEAT. Aquí se traduce al enum; un valor desconocido se trata como
     * el caso ordinario en vez de detener la emisión, porque la calificación real la lleva cada
     * línea y este valor solo cubre las que no tienen código fiscal.
     */
    private OperationQualification defaultQualification(VerifactuSettings settings) {
        String code = settings.getDefaultOperationQualification();
        for (OperationQualification qualification : OperationQualification.values()) {
            if (qualification.code() != null && qualification.code().equalsIgnoreCase(code)) {
                return qualification;
            }
        }
        return OperationQualification.SUBJECT_NOT_EXEMPT;
    }

    private static RegistroAltaContent.BreakdownDetail toDetail(TaxBreakdownEntry entry) {
        return new RegistroAltaContent.BreakdownDetail(entry.regimeKey(), entry.qualification(),
                entry.exemptionCause(), entry.taxRate(), entry.taxableBase(), entry.taxAmount());
    }

    /**
     * Una factura sin NIF del destinatario se remite sin el bloque {@code Destinatarios}. El
     * esquema lo admite —una factura simplificada no lo lleva— y es preferible a inventarse un
     * identificador.
     */
    private RegistroAltaContent.Recipient recipient(CommercialDocument invoice) {
        if (invoice.getCustomerTaxIdSnapshot() == null || invoice.getCustomerTaxIdSnapshot().isBlank()) {
            return null;
        }
        return new RegistroAltaContent.Recipient(invoice.getCustomerNameSnapshot(),
                invoice.getCustomerTaxIdSnapshot(), invoice.getCustomerTaxIdentificationTypeSnapshot(),
                invoice.getCustomerTaxCountrySnapshot());
    }

    private List<RegistroAltaContent.RectifiedInvoice> rectifiedInvoices(CommercialDocument invoice,
                                                                         VerifactuSettings settings) {
        if (invoice.getRectifiedDocumentId() == null || invoice.getRectifiedNumberSnapshot() == null) {
            return List.of();
        }
        return List.of(new RegistroAltaContent.RectifiedInvoice(settings.getIssuerTaxId(),
                invoice.getRectifiedNumberSnapshot(), invoice.getRectifiedIssueDateSnapshot()));
    }

    /**
     * El esquema exige una descripción de la operación y PERA no tiene un campo para ella. Se toman
     * por orden las observaciones del documento, la descripción de su primera línea y, si no hay
     * nada, un texto genérico. Es preferible a dejarlo vacío, que la AEAT rechaza.
     */
    private String operationDescription(CommercialDocument invoice) {
        String description = firstNonBlank(invoice.getNotes(), firstLineDescription(invoice), DEFAULT_DESCRIPTION);
        return description.length() > MAX_DESCRIPTION ? description.substring(0, MAX_DESCRIPTION) : description;
    }

    private String firstLineDescription(CommercialDocument invoice) {
        return invoice.getLines().stream().map(DocumentLine::getDescription).findFirst().orElse(null);
    }

    /**
     * Identidad del sistema informático, toda ella del despliegue y ninguna de la empresa.
     *
     * <p>El número de instalación identifica la instalación de PERA, no a quien la usa: si varias
     * empresas comparten instalación, todas tienen que declarar el mismo. Antes se caía al
     * identificador de la empresa, y eso contradecía a la propia marca de múltiples obligados: cada
     * empresa decía estar en una instalación distinta mientras declaraba compartirla.</p>
     *
     * <p>La razón social es la de quien comercializa PERA. Tampoco tiene valor por defecto: dejarla
     * caer al nombre del programa producía un registro que parecía completo y no lo estaba.</p>
     */
    private RegistroAltaContent.SoftwareSystem software(VerifactuSettings settings) {
        return new RegistroAltaContent.SoftwareSystem(developerLegalName,
                settings.getDeveloperTaxId(), settings.getSoftwareName(), settings.getSoftwareId(),
                settings.getSoftwareVersion(), installationNumber,
                settingsRepository.countByEnabledTrue() > 1);
    }

    private static RegistroAltaContent.PreviousRecord previousRecord(RecordPayloadFactory.PayloadContext context) {
        if (context.previousRecord() == null) {
            return null;
        }
        return new RegistroAltaContent.PreviousRecord(context.previousRecord().getIssuerTaxId(),
                context.previousRecord().getInvoiceNumber(), context.previousRecord().getInvoiceDate(),
                context.previousRecord().getFingerprint());
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return DEFAULT_DESCRIPTION;
    }
}
