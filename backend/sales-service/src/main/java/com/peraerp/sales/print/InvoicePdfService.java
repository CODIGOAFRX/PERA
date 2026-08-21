package com.peraerp.sales.print;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.CommercialDocumentRepository;
import com.peraerp.sales.document.DocumentLine;
import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import com.peraerp.sales.verifactu.qr.VerifactuQrPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reúne lo que se imprime en una factura y produce el PDF.
 *
 * <p>Separado del renderizador a propósito: aquí está de dónde sale cada dato y allí cómo se
 * dibuja. Cambiar la maqueta no debería obligar a tocar consultas, ni al revés.</p>
 */
@Service
public class InvoicePdfService {

    private static final String ADDRESS_NOTICE =
            "Domicilio pendiente: PERA todavía no guarda la dirección de los clientes.";

    private final CommercialDocumentRepository documents;
    private final VerifactuRecordRepository records;
    private final VerifactuSettingsRepository settings;
    private final HttpCompanyProfileClient companyProfile;
    private final HttpPaymentMethodClient paymentMethods;
    private final InvoicePdfRenderer renderer;
    private final CurrentCompanyProvider companyProvider;

    public InvoicePdfService(CommercialDocumentRepository documents, VerifactuRecordRepository records,
                             VerifactuSettingsRepository settings, HttpCompanyProfileClient companyProfile,
                             HttpPaymentMethodClient paymentMethods, InvoicePdfRenderer renderer,
                             CurrentCompanyProvider companyProvider) {
        this.documents = documents;
        this.records = records;
        this.settings = settings;
        this.companyProfile = companyProfile;
        this.paymentMethods = paymentMethods;
        this.renderer = renderer;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public InvoicePdf render(UUID documentId) {
        UUID companyId = companyProvider.requireCompanyId();
        CommercialDocument invoice = documents.findByIdAndCompanyId(documentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento", documentId));
        if (!invoice.getType().isInvoice()) {
            throw new BusinessRuleException(
                    "Solo se pueden imprimir facturas; este documento es " + invoice.getType() + ".");
        }

        Optional<VerifactuSettings> configuration = settings.findByCompanyId(companyId);
        CompanyProfile company = companyProfile.profile();
        InvoicePdfContent content = new InvoicePdfContent(
                issuer(company, configuration.orElse(null)),
                recipient(invoice),
                invoice.getType() == com.peraerp.sales.document.DocumentType.RECTIFYING_INVOICE
                        ? "Factura rectificativa" : "Factura",
                invoice.getDocumentNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getInvoiceKind() == null ? null : invoice.getInvoiceKind().code(),
                invoice.getRectifiedNumberSnapshot(),
                invoice.getRectifiedIssueDateSnapshot(),
                invoice.getCurrency(),
                lines(invoice),
                taxes(invoice),
                invoice.getNetAmount(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                paymentMethods.nameOf(invoice.getPaymentMethodId()),
                invoice.getNotes(),
                verifactu(invoice, companyId, configuration.orElse(null)),
                companyProfile.logo());

        return new InvoicePdf(fileName(invoice.getDocumentNumber()), renderer.render(content));
    }

    /**
     * La razón social y el NIF salen de la configuración Veri*Factu cuando existe, porque es la
     * identidad con la que la empresa se declara ante la AEAT y tiene que coincidir con la de la
     * factura. Si no está configurada se cae al nombre visible de la empresa, que al menos permite
     * emitir.
     */
    private InvoicePdfContent.Issuer issuer(CompanyProfile company, VerifactuSettings configuration) {
        String legalName = configuration == null || isBlank(configuration.getIssuerLegalName())
                ? company.displayName() : configuration.getIssuerLegalName();
        String taxId = configuration == null ? null : configuration.getIssuerTaxId();
        return new InvoicePdfContent.Issuer(legalName, taxId, company.addressLine1(), company.addressLine2(),
                company.postalCode(), company.city(), company.region(), company.phone(),
                company.preferredEmail(), company.website());
    }

    private InvoicePdfContent.Recipient recipient(CommercialDocument invoice) {
        return new InvoicePdfContent.Recipient(invoice.getCustomerNameSnapshot(),
                invoice.getCustomerTaxIdSnapshot(), invoice.getCustomerCodeSnapshot(), ADDRESS_NOTICE);
    }

    private List<InvoicePdfContent.Line> lines(CommercialDocument invoice) {
        List<InvoicePdfContent.Line> printable = new ArrayList<>();
        for (DocumentLine line : invoice.getLines()) {
            printable.add(new InvoicePdfContent.Line(line.getLineOrder(), line.getProductCodeSnapshot(),
                    line.getDescription(), line.getQuantity(), line.getUnitPrice(),
                    line.getDiscountPercentage(), line.getTaxPercentage(), line.getNetAmount()));
        }
        return printable;
    }

    /**
     * Desglose por tipo de IVA para el pie de la factura.
     *
     * <p>No reutiliza el agregador del registro Veri*Factu: aquel agrupa además por régimen y
     * calificación, que no se imprimen, y forzar que coincidan acabaría cambiando uno para arreglar
     * el otro. Los importes vienen ya calculados de la factura; aquí solo se suman.</p>
     */
    private List<InvoicePdfContent.TaxRow> taxes(CommercialDocument invoice) {
        Map<BigDecimal, BigDecimal[]> byRate = new LinkedHashMap<>();
        for (DocumentLine line : invoice.getLines()) {
            BigDecimal rate = line.getTaxPercentage() == null ? BigDecimal.ZERO : line.getTaxPercentage();
            BigDecimal[] sums = byRate.computeIfAbsent(rate.stripTrailingZeros(),
                    key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(zeroIfNull(line.getNetAmount()));
            sums[1] = sums[1].add(zeroIfNull(line.getTaxAmount()));
        }
        List<InvoicePdfContent.TaxRow> rows = new ArrayList<>();
        byRate.forEach((rate, sums) -> rows.add(new InvoicePdfContent.TaxRow(rate, sums[0], sums[1])));
        return rows;
    }

    /**
     * El bloque de cotejo solo se imprime si la factura generó registro. Poner el QR y la leyenda
     * en una factura que no lo generó sería afirmar algo que no ha pasado.
     */
    private InvoicePdfContent.Verifactu verifactu(CommercialDocument invoice, UUID companyId,
                                                  VerifactuSettings configuration) {
        if (configuration == null || !configuration.isEnabled()) {
            return null;
        }
        VerifactuRecord record = records
                .findByCompanyIdAndDocumentIdOrderBySequenceNumberAsc(companyId, invoice.getId()).stream()
                .filter(item -> item.getRecordType() == VerifactuRecordType.ALTA)
                .findFirst()
                .orElse(null);
        if (record == null) {
            return null;
        }
        VerifactuEnvironment environment = configuration.getEnvironment();
        String payload = VerifactuQrPayload.of(environment, record.getIssuerTaxId(),
                record.getInvoiceNumber(), record.getInvoiceDate(), record.getTotalAmount());
        return new InvoicePdfContent.Verifactu(payload, record.getFingerprint(),
                environment.qrValidationUrl());
    }

    /** El nombre del fichero acaba en el disco del cliente: nada de barras ni de espacios raros. */
    private static String fileName(String documentNumber) {
        String safe = documentNumber == null ? "factura" : documentNumber.replaceAll("[^A-Za-z0-9._-]", "-");
        return "Factura-" + safe + ".pdf";
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** El PDF y el nombre con el que se descarga. */
    public record InvoicePdf(String fileName, byte[] bytes) {
    }
}
