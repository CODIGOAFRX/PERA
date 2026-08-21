package com.peraerp.sales.verifactu.xml;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.verifactu.domain.TaxIdentificationType;
import com.peraerp.sales.verifactu.hash.VerifactuFieldFormat;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;

/**
 * Serializa un registro de alta en el XML que espera la AEAT.
 *
 * <p>Se escribe con {@link XMLStreamWriter} de la propia JDK en lugar de JAXB o una plantilla de
 * texto. JAXB obligaría a añadir dependencias y a generar clases desde los XSD; una plantilla de
 * texto no escaparía los caracteres especiales, y una razón social con un {@code &} produciría un
 * XML inválido que la AEAT rechazaría sin explicar por qué.</p>
 *
 * <p><strong>El orden de los elementos importa.</strong> El esquema los declara como
 * {@code sequence}, así que escribirlos en otro orden invalida el documento aunque el contenido
 * sea correcto. El orden de los métodos de esta clase es el del esquema y no debe reordenarse.</p>
 */
@Component
public class RegistroAltaXmlWriter {

    /** Espacio de nombres de {@code SuministroInformacion.xsd}. */
    public static final String NAMESPACE =
            "https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd";

    private static final String PREFIX = "sf";
    /** Versión del formato de registro. */
    private static final String ID_VERSION = "1.0";
    /** Algoritmo de la huella: 01 es SHA-256. */
    private static final String FINGERPRINT_ALGORITHM = "01";
    private static final String YES = "S";
    private static final String NO = "N";
    private static final int MAX_BREAKDOWN_DETAILS = 12;

    private final XMLOutputFactory factory = XMLOutputFactory.newFactory();

    public String write(RegistroAltaContent content) {
        validate(content);
        StringWriter output = new StringWriter();
        try {
            XMLStreamWriter xml = factory.createXMLStreamWriter(output);
            xml.setPrefix(PREFIX, NAMESPACE);
            xml.writeStartElement(NAMESPACE, "RegistroAlta");
            xml.writeNamespace(PREFIX, NAMESPACE);

            text(xml, "IDVersion", ID_VERSION);
            writeInvoiceId(xml, content);
            text(xml, "NombreRazonEmisor", content.issuerLegalName());
            text(xml, "TipoFactura", content.invoiceKind().code());
            if (content.rectificationType() != null) {
                text(xml, "TipoRectificativa", content.rectificationType().code());
            }
            writeRectifiedInvoices(xml, content);
            text(xml, "DescripcionOperacion", content.operationDescription());
            writeRecipient(xml, content);
            writeBreakdown(xml, content);
            text(xml, "CuotaTotal", VerifactuFieldFormat.amount(content.totalTaxAmount()));
            text(xml, "ImporteTotal", VerifactuFieldFormat.amount(content.totalAmount()));
            writeChaining(xml, content);
            writeSoftwareSystem(xml, content);
            text(xml, "FechaHoraHusoGenRegistro", VerifactuFieldFormat.timestamp(content.generatedAt()));
            text(xml, "TipoHuella", FINGERPRINT_ALGORITHM);
            text(xml, "Huella", content.fingerprint());

            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
            return output.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("No se pudo serializar el registro de facturación.", e);
        }
    }

    private void writeInvoiceId(XMLStreamWriter xml, RegistroAltaContent content) throws XMLStreamException {
        xml.writeStartElement(NAMESPACE, "IDFactura");
        text(xml, "IDEmisorFactura", content.issuerTaxId());
        text(xml, "NumSerieFactura", content.invoiceNumber());
        text(xml, "FechaExpedicionFactura", VerifactuFieldFormat.date(content.issueDate()));
        xml.writeEndElement();
    }

    private void writeRectifiedInvoices(XMLStreamWriter xml, RegistroAltaContent content)
            throws XMLStreamException {
        List<RegistroAltaContent.RectifiedInvoice> rectified = content.rectifiedInvoices();
        if (rectified == null || rectified.isEmpty()) {
            return;
        }
        xml.writeStartElement(NAMESPACE, "FacturasRectificadas");
        for (RegistroAltaContent.RectifiedInvoice invoice : rectified) {
            xml.writeStartElement(NAMESPACE, "IDFacturaRectificada");
            text(xml, "IDEmisorFactura", invoice.issuerTaxId());
            text(xml, "NumSerieFactura", invoice.invoiceNumber());
            text(xml, "FechaExpedicionFactura", VerifactuFieldFormat.date(invoice.issueDate()));
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    /**
     * El destinatario es opcional en el esquema porque una factura simplificada no lo lleva. Cuando
     * lo hay, se identifica por NIF si es residente y por IDOtro en cualquier otro caso.
     */
    private void writeRecipient(XMLStreamWriter xml, RegistroAltaContent content) throws XMLStreamException {
        RegistroAltaContent.Recipient recipient = content.recipient();
        if (recipient == null) {
            return;
        }
        xml.writeStartElement(NAMESPACE, "Destinatarios");
        xml.writeStartElement(NAMESPACE, "IDDestinatario");
        text(xml, "NombreRazon", recipient.legalName());
        if (recipient.identificationType() == null || recipient.identificationType() == TaxIdentificationType.NIF) {
            text(xml, "NIF", recipient.taxId());
        } else {
            xml.writeStartElement(NAMESPACE, "IDOtro");
            if (recipient.countryCode() != null && !recipient.countryCode().isBlank()) {
                text(xml, "CodigoPais", recipient.countryCode());
            }
            text(xml, "IDType", recipient.identificationType().code());
            text(xml, "ID", recipient.taxId());
            xml.writeEndElement();
        }
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void writeBreakdown(XMLStreamWriter xml, RegistroAltaContent content) throws XMLStreamException {
        xml.writeStartElement(NAMESPACE, "Desglose");
        for (RegistroAltaContent.BreakdownDetail detail : content.breakdown()) {
            xml.writeStartElement(NAMESPACE, "DetalleDesglose");
            text(xml, "ClaveRegimen", detail.regimeKey());
            if (detail.qualification().isExempt()) {
                text(xml, "OperacionExenta", detail.exemptionCause().code());
            } else {
                text(xml, "CalificacionOperacion", detail.qualification().code());
                text(xml, "TipoImpositivo", VerifactuFieldFormat.amount(detail.taxRate()));
            }
            text(xml, "BaseImponibleOimporteNoSujeto", VerifactuFieldFormat.amount(detail.taxableBase()));
            if (!detail.qualification().isExempt()) {
                text(xml, "CuotaRepercutida", VerifactuFieldFormat.amount(detail.taxAmount()));
            }
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    /**
     * El encadenamiento es una elección: o se declara que es el primer registro de la cadena, o se
     * identifica el anterior. No caben las dos cosas ni ninguna.
     */
    private void writeChaining(XMLStreamWriter xml, RegistroAltaContent content) throws XMLStreamException {
        xml.writeStartElement(NAMESPACE, "Encadenamiento");
        RegistroAltaContent.PreviousRecord previous = content.previousRecord();
        if (previous == null) {
            text(xml, "PrimerRegistro", YES);
        } else {
            xml.writeStartElement(NAMESPACE, "RegistroAnterior");
            text(xml, "IDEmisorFactura", previous.issuerTaxId());
            text(xml, "NumSerieFactura", previous.invoiceNumber());
            text(xml, "FechaExpedicionFactura", VerifactuFieldFormat.date(previous.issueDate()));
            text(xml, "Huella", previous.fingerprint());
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void writeSoftwareSystem(XMLStreamWriter xml, RegistroAltaContent content) throws XMLStreamException {
        RegistroAltaContent.SoftwareSystem software = content.software();
        xml.writeStartElement(NAMESPACE, "SistemaInformatico");
        text(xml, "NombreRazon", software.developerLegalName());
        text(xml, "NIF", software.developerTaxId());
        text(xml, "NombreSistemaInformatico", software.name());
        text(xml, "IdSistemaInformatico", software.id());
        text(xml, "Version", software.version());
        text(xml, "NumeroInstalacion", software.installationNumber());
        // Las dos primeras marcas son capacidades del programa y en PERA son constantes: solo
        // implementa la modalidad VERI*FACTU y sabe llevar varias empresas a la vez.
        text(xml, "TipoUsoPosibleSoloVerifactu", YES);
        text(xml, "TipoUsoPosibleMultiOT", YES);
        // La tercera no es una capacidad sino un hecho: si esta instalación está sirviendo de
        // verdad a más de un obligado tributario ahora mismo. Una instalación de una sola empresa
        // que declarase «S» estaría afirmando algo falso ante la AEAT.
        text(xml, "IndicadorMultiplesOT", software.multipleTaxpayers() ? YES : NO);
        xml.writeEndElement();
    }

    private void text(XMLStreamWriter xml, String element, String value) throws XMLStreamException {
        xml.writeStartElement(NAMESPACE, element);
        xml.writeCharacters(value == null ? "" : value);
        xml.writeEndElement();
    }

    private void validate(RegistroAltaContent content) {
        if (content == null) {
            throw new IllegalArgumentException("No hay contenido que serializar.");
        }
        require(content.issuerTaxId(), "el NIF del obligado");
        require(content.issuerLegalName(), "la razón social del obligado");
        require(content.invoiceNumber(), "el número de factura");
        require(content.operationDescription(), "la descripción de la operación");
        require(content.fingerprint(), "la huella");
        if (content.invoiceKind() == null || content.issueDate() == null || content.generatedAt() == null) {
            throw new BusinessRuleException("El registro exige tipo de factura, fecha de expedición y hora de generación.");
        }
        if (content.software() == null) {
            throw new BusinessRuleException("El registro exige identificar el sistema informático de facturación.");
        }
        require(content.software().developerTaxId(), "el NIF del productor del software");
        require(content.software().developerLegalName(), "la razón social del productor del software");
        // Identifica la instalación del programa, no a la empresa que lo usa: con varias empresas
        // en la misma instalación, todas tienen que declarar el mismo número.
        require(content.software().installationNumber(), "el número de instalación del programa");
        if (content.breakdown() == null || content.breakdown().isEmpty()) {
            throw new BusinessRuleException("El registro exige al menos una línea de desglose.");
        }
        if (content.breakdown().size() > MAX_BREAKDOWN_DETAILS) {
            throw new BusinessRuleException("El desglose admite " + MAX_BREAKDOWN_DETAILS
                    + " líneas como máximo y tiene " + content.breakdown().size() + ".");
        }
        if (content.invoiceKind().isRectifying() && content.rectificationType() == null) {
            throw new BusinessRuleException(
                    "Una factura rectificativa debe indicar si rectifica por sustitución o por diferencias.");
        }
        BigDecimal base = content.breakdown().stream()
                .map(RegistroAltaContent.BreakdownDetail::taxableBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = content.breakdown().stream()
                .map(RegistroAltaContent.BreakdownDetail::taxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // El desglose y los totales tienen que contar lo mismo. Si no cuadran, el registro es
        // incoherente y la AEAT lo rechaza; mejor detectarlo aquí, con los números a la vista.
        if (tax.compareTo(zeroIfNull(content.totalTaxAmount())) != 0) {
            throw new BusinessRuleException("El desglose suma una cuota de " + tax
                    + " y la factura declara " + content.totalTaxAmount() + ".");
        }
        if (base.add(tax).compareTo(zeroIfNull(content.totalAmount())) != 0) {
            throw new BusinessRuleException("El desglose suma un total de " + base.add(tax)
                    + " y la factura declara " + content.totalAmount() + ".");
        }
    }

    private static void require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("El registro exige " + what + ".");
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
