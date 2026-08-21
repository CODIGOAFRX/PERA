package com.peraerp.sales.verifactu.xml;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.verifactu.domain.ExemptionCause;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.OperationQualification;
import com.peraerp.sales.verifactu.domain.RectificationType;
import com.peraerp.sales.verifactu.domain.TaxIdentificationType;
import com.peraerp.sales.verifactu.xml.RegistroAltaContent.BreakdownDetail;
import com.peraerp.sales.verifactu.xml.RegistroAltaContent.PreviousRecord;
import com.peraerp.sales.verifactu.xml.RegistroAltaContent.RectifiedInvoice;
import com.peraerp.sales.verifactu.xml.RegistroAltaContent.Recipient;
import com.peraerp.sales.verifactu.xml.RegistroAltaContent.SoftwareSystem;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serialización del registro de alta.
 *
 * <p>El esquema declara los elementos como {@code sequence}: escribirlos en otro orden invalida el
 * documento aunque el contenido sea correcto, y la AEAT devuelve un rechazo que no dice cuál es el
 * elemento fuera de sitio. Por eso hay pruebas del orden y no solo del contenido.</p>
 */
class RegistroAltaXmlWriterTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 8, 19);
    private static final String FINGERPRINT =
            "7E3551C74ABC781AE84E179AF0D3AA1ACEC5F8D4FDE21164E61233536FA823AD";

    private final RegistroAltaXmlWriter writer = new RegistroAltaXmlWriter();

    private SoftwareSystem software() {
        return software(false);
    }

    private SoftwareSystem software(boolean multipleTaxpayers) {
        return new SoftwareSystem("EMPRESA DE PRUEBAS S.L.", "89890001K", "PERA ERP", "01", "0.1.0",
                "INST-1", multipleTaxpayers);
    }

    private Recipient resident() {
        return new Recipient("ALUMINIOS FAMA S.L.", "B75777847", TaxIdentificationType.NIF, "ES");
    }

    private List<BreakdownDetail> generalRate() {
        return List.of(new BreakdownDetail("01", OperationQualification.SUBJECT_NOT_EXEMPT, null,
                new BigDecimal("21"), new BigDecimal("200.00"), new BigDecimal("42.00")));
    }

    private RegistroAltaContent content(Recipient recipient, List<BreakdownDetail> breakdown,
                                        String totalTax, String total, PreviousRecord previous,
                                        RectificationType rectificationType,
                                        List<RectifiedInvoice> rectified, InvoiceKind kind) {
        return new RegistroAltaContent("89890001K", "EMPRESA DE PRUEBAS S.L.", "FAC-2026-000001", ISSUE_DATE,
                kind, rectificationType, rectified, "Venta de bienes o servicios", recipient, breakdown,
                new BigDecimal(totalTax), new BigDecimal(total), previous, software(),
                ZonedDateTime.of(2026, 8, 19, 11, 54, 20, 0, MADRID), FINGERPRINT);
    }

    private RegistroAltaContent ordinaryInvoice() {
        return content(resident(), generalRate(), "42.00", "242.00", null, null, null, InvoiceKind.F1);
    }

    private void assertWellFormed(String xml) {
        assertThatCode(() -> {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }).doesNotThrowAnyException();
    }

    private List<String> elementOrder(String xml) {
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("<sf:([A-Za-z0-9]+)>").matcher(xml);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private RegistroAltaContent withSoftware(SoftwareSystem software) {
        RegistroAltaContent base = ordinaryInvoice();
        return new RegistroAltaContent(base.issuerTaxId(), base.issuerLegalName(), base.invoiceNumber(),
                base.issueDate(), base.invoiceKind(), base.rectificationType(), base.rectifiedInvoices(),
                base.operationDescription(), base.recipient(), base.breakdown(), base.totalTaxAmount(),
                base.totalAmount(), base.previousRecord(), software, base.generatedAt(), base.fingerprint());
    }

    // --- contenido ---

    @Test
    void producesWellFormedXmlInTheSchemaNamespace() {
        String xml = writer.write(ordinaryInvoice());

        assertWellFormed(xml);
        assertThat(xml).contains(RegistroAltaXmlWriter.NAMESPACE);
    }

    @Test
    void declaresTheRecordVersionAndTheFingerprintAlgorithm() {
        String xml = writer.write(ordinaryInvoice());

        assertThat(xml).contains("<sf:IDVersion>1.0</sf:IDVersion>");
        assertThat(xml).contains("<sf:TipoHuella>01</sf:TipoHuella>");
        assertThat(xml).contains("<sf:Huella>" + FINGERPRINT + "</sf:Huella>");
    }

    @Test
    void formatsDatesAndAmountsExactlyAsTheFingerprintDoes() {
        String xml = writer.write(ordinaryInvoice());

        assertThat(xml).contains("<sf:FechaExpedicionFactura>19-08-2026</sf:FechaExpedicionFactura>");
        assertThat(xml).contains("2026-08-19T11:54:20+02:00");
        assertThat(xml).contains("<sf:ImporteTotal>242.00</sf:ImporteTotal>");
        assertThat(xml).contains("<sf:CuotaTotal>42.00</sf:CuotaTotal>");
    }

    // --- orden ---

    @Test
    void writesElementsInTheOrderRequiredByTheSchema() {
        List<String> order = elementOrder(writer.write(ordinaryInvoice()));

        assertThat(order.indexOf("IDVersion")).isLessThan(order.indexOf("IDFactura"));
        assertThat(order.indexOf("IDFactura")).isLessThan(order.indexOf("NombreRazonEmisor"));
        assertThat(order.indexOf("NombreRazonEmisor")).isLessThan(order.indexOf("TipoFactura"));
        assertThat(order.indexOf("TipoFactura")).isLessThan(order.indexOf("DescripcionOperacion"));
        assertThat(order.indexOf("DescripcionOperacion")).isLessThan(order.indexOf("Destinatarios"));
        assertThat(order.indexOf("Destinatarios")).isLessThan(order.indexOf("Desglose"));
        assertThat(order.indexOf("Desglose")).isLessThan(order.indexOf("CuotaTotal"));
        assertThat(order.indexOf("CuotaTotal")).isLessThan(order.indexOf("ImporteTotal"));
        assertThat(order.indexOf("ImporteTotal")).isLessThan(order.indexOf("Encadenamiento"));
        assertThat(order.indexOf("Encadenamiento")).isLessThan(order.indexOf("SistemaInformatico"));
        assertThat(order.indexOf("SistemaInformatico")).isLessThan(order.indexOf("FechaHoraHusoGenRegistro"));
        assertThat(order.indexOf("FechaHoraHusoGenRegistro")).isLessThan(order.indexOf("TipoHuella"));
    }

    // --- encadenamiento ---

    @Test
    void theFirstRecordOfTheChainSaysSo() {
        assertThat(writer.write(ordinaryInvoice())).contains("<sf:PrimerRegistro>S</sf:PrimerRegistro>");
    }

    @Test
    void aChainedRecordIdentifiesThePreviousInvoiceInstead() {
        PreviousRecord previous = new PreviousRecord("89890001K", "FAC-2026-000000", ISSUE_DATE, "AAAA1111");

        String xml = writer.write(content(resident(), generalRate(), "42.00", "242.00", previous,
                null, null, InvoiceKind.F1));

        assertWellFormed(xml);
        assertThat(xml).contains("<sf:RegistroAnterior>");
        assertThat(xml).contains("<sf:Huella>AAAA1111</sf:Huella>");
        assertThat(xml).doesNotContain("PrimerRegistro");
    }

    // --- destinatario ---

    @Test
    void aResidentRecipientIsIdentifiedByTaxNumber() {
        assertThat(writer.write(ordinaryInvoice())).contains("<sf:NIF>B75777847</sf:NIF>");
    }

    @Test
    void aForeignRecipientUsesTheOtherIdentifierBlock() {
        Recipient foreign = new Recipient("ACME SARL", "FR40303265045",
                TaxIdentificationType.VAT_NUMBER, "FR");

        String xml = writer.write(content(foreign, generalRate(), "42.00", "242.00", null, null, null,
                InvoiceKind.F1));

        assertWellFormed(xml);
        assertThat(xml).contains("<sf:IDOtro>");
        assertThat(xml).contains("<sf:CodigoPais>FR</sf:CodigoPais>");
        assertThat(xml).contains("<sf:IDType>02</sf:IDType>");
        assertThat(xml).contains("<sf:ID>FR40303265045</sf:ID>");
    }

    // --- desglose ---

    @Test
    void anExemptDetailReportsTheCauseAndOmitsTheRate() {
        List<BreakdownDetail> exempt = List.of(new BreakdownDetail("01", OperationQualification.EXEMPT,
                ExemptionCause.ARTICLE_21, BigDecimal.ZERO, new BigDecimal("500.00"), BigDecimal.ZERO));

        String xml = writer.write(content(resident(), exempt, "0.00", "500.00", null, null, null,
                InvoiceKind.F1));

        assertWellFormed(xml);
        assertThat(xml).contains("<sf:OperacionExenta>E2</sf:OperacionExenta>");
        assertThat(xml).doesNotContain("CalificacionOperacion");
        assertThat(xml).doesNotContain("CuotaRepercutida");
    }

    @Test
    void aTaxedDetailReportsQualificationRateAndAmount() {
        String xml = writer.write(ordinaryInvoice());

        assertThat(xml).contains("<sf:CalificacionOperacion>S1</sf:CalificacionOperacion>");
        assertThat(xml).contains("<sf:TipoImpositivo>21.00</sf:TipoImpositivo>");
        assertThat(xml).contains("<sf:BaseImponibleOimporteNoSujeto>200.00</sf:BaseImponibleOimporteNoSujeto>");
        assertThat(xml).contains("<sf:CuotaRepercutida>42.00</sf:CuotaRepercutida>");
    }

    // --- rectificativas ---

    @Test
    void aRectifyingInvoiceReportsItsCriterionAndTheInvoiceItCorrects() {
        List<RectifiedInvoice> rectified = List.of(
                new RectifiedInvoice("89890001K", "FAC-2026-000001", ISSUE_DATE));

        String xml = writer.write(content(resident(), generalRate(), "42.00", "242.00", null,
                RectificationType.SUBSTITUTION, rectified, InvoiceKind.R1));

        assertWellFormed(xml);
        assertThat(xml).contains("<sf:TipoRectificativa>S</sf:TipoRectificativa>");
        assertThat(xml).contains("<sf:IDFacturaRectificada>");
    }

    // --- escapado ---

    @Test
    void specialCharactersInNamesAreEscaped() {
        Recipient tricky = new Recipient("Pérez & Hijos <S.L.>", "B75777847", TaxIdentificationType.NIF, "ES");

        String xml = writer.write(content(tricky, generalRate(), "42.00", "242.00", null, null, null,
                InvoiceKind.F1));

        assertWellFormed(xml);
        assertThat(xml).contains("&amp;");
        assertThat(xml).contains("&lt;S.L.&gt;");
    }

    // --- coherencia ---

    @Test
    void aBreakdownThatDoesNotAddUpToTheDeclaredTaxIsRejected() {
        assertThatThrownBy(() -> writer.write(content(resident(), generalRate(), "99.00", "242.00",
                null, null, null, InvoiceKind.F1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cuota");
    }

    @Test
    void aBreakdownThatDoesNotAddUpToTheDeclaredTotalIsRejected() {
        assertThatThrownBy(() -> writer.write(content(resident(), generalRate(), "42.00", "999.00",
                null, null, null, InvoiceKind.F1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("total");
    }

    @Test
    void aRectifyingInvoiceWithoutACriterionIsRejected() {
        List<RectifiedInvoice> rectified = List.of(
                new RectifiedInvoice("89890001K", "FAC-2026-000001", ISSUE_DATE));

        assertThatThrownBy(() -> writer.write(content(resident(), generalRate(), "42.00", "242.00",
                null, null, rectified, InvoiceKind.R1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anEmptyBreakdownIsRejected() {
        assertThatThrownBy(() -> writer.write(content(resident(), List.of(), "0.00", "0.00",
                null, null, null, InvoiceKind.F1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- sistema informático ---

    /**
     * Las dos primeras marcas describen lo que el programa sabe hacer y no dependen del despliegue.
     */
    @Test
    void theSoftwareDeclaresItsCapabilitiesAsConstants() {
        String xml = writer.write(ordinaryInvoice());

        assertThat(xml).contains("<sf:TipoUsoPosibleSoloVerifactu>S</sf:TipoUsoPosibleSoloVerifactu>");
        assertThat(xml).contains("<sf:TipoUsoPosibleMultiOT>S</sf:TipoUsoPosibleMultiOT>");
    }

    /**
     * La tercera no: dice si esta instalación sirve de verdad a varios obligados tributarios en el
     * momento de generar el registro. Una instalación de una sola empresa que declarase «S» estaría
     * afirmando algo falso ante la AEAT, y es un dato que nadie revisa después.
     */
    @Test
    void multipleTaxpayersIsAFactOfTheInstallationAndNotAConstant() {
        assertThat(writer.write(withSoftware(software(false))))
                .contains("<sf:IndicadorMultiplesOT>N</sf:IndicadorMultiplesOT>");
        assertThat(writer.write(withSoftware(software(true))))
                .contains("<sf:IndicadorMultiplesOT>S</sf:IndicadorMultiplesOT>");
    }

    /**
     * El número de instalación identifica la instalación del programa, no a la empresa que lo usa.
     * Sin él, varias empresas de la misma instalación declararían instalaciones distintas mientras
     * afirman compartirla.
     */
    @Test
    void theInstallationNumberIsMandatory() {
        RegistroAltaContent withoutInstallation = withSoftware(new SoftwareSystem(
                "EMPRESA DE PRUEBAS S.L.", "89890001K", "PERA ERP", "01", "0.1.0", "  ", false));

        assertThatThrownBy(() -> writer.write(withoutInstallation))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("número de instalación");
    }

    /** La razón social del productor es la de quien comercializa PERA, y no tiene valor por defecto. */
    @Test
    void theSoftwareProducerIsMandatory() {
        RegistroAltaContent withoutProducer = withSoftware(new SoftwareSystem(
                null, "89890001K", "PERA ERP", "01", "0.1.0", "INST-1", false));

        assertThatThrownBy(() -> writer.write(withoutProducer))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("razón social del productor");
    }
}
