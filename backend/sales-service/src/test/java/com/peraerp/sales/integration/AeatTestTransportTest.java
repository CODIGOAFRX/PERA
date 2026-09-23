package com.peraerp.sales.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class AeatTestTransportTest {
    String response(String state,String number) {
        return "<r:RespuestaRegFactuSistemaFacturacion xmlns:r=\""+AeatTestTransport.RESPONSE+"\" xmlns:sf=\""+AeatTestTransport.SF+"\"><r:CSV>CSV-TEST</r:CSV><r:TiempoEsperaEnvio>120</r:TiempoEsperaEnvio><r:RespuestaLinea><r:IDFactura><sf:IDEmisorFactura>89890001K</sf:IDEmisorFactura><sf:NumSerieFactura>"+number+"</sf:NumSerieFactura><sf:FechaExpedicionFactura>23-09-2026</sf:FechaExpedicionFactura></r:IDFactura><r:EstadoRegistro>"+state+"</r:EstadoRegistro></r:RespuestaLinea></r:RespuestaRegFactuSistemaFacturacion>";
    }
    @Test void distinguishesAcceptedAndRejectedAndRespectsWait() throws Exception {
        var accepted=AeatTestTransport.parse(response("Correcto","FAC1"),"89890001K","FAC1","23-09-2026");
        assertThat(accepted.state()).isEqualTo("ACCEPTED"); assertThat(accepted.csv()).isEqualTo("CSV-TEST"); assertThat(accepted.waitSeconds()).isEqualTo(120);
        assertThat(AeatTestTransport.parse(response("Incorrecto","FAC1"),"89890001K","FAC1","23-09-2026").state()).isEqualTo("REJECTED");
        assertThat(AeatTestTransport.parse(response("AceptadoConErrores","FAC1"),"89890001K","FAC1","23-09-2026").state()).isEqualTo("ACCEPTED_WITH_ERRORS");
    }
    @Test void rejectsAnotherInvoiceOrUnknownState() {
        assertThatThrownBy(()->AeatTestTransport.parse(response("Correcto","OTHER"),"89890001K","FAC1","23-09-2026")).hasMessageContaining("no corresponde");
        assertThatThrownBy(()->AeatTestTransport.parse(response("NewState","FAC1"),"89890001K","FAC1","23-09-2026")).hasMessageContaining("desconocido");
    }
    @Test void doesNotTreatDuplicateAsAcceptance() throws Exception {
        String response=response("Incorrecto","FAC1").replace("</r:RespuestaLinea>","<r:RegistroDuplicado/></r:RespuestaLinea>");
        assertThat(AeatTestTransport.parse(response,"89890001K","FAC1","23-09-2026").state()).isEqualTo("UNKNOWN");
    }
    @Test void rejectsExternalEntitiesAndInvalidCertificates() {
        assertThatThrownBy(()->AeatTestTransport.xml("<!DOCTYPE foo [<!ENTITY x SYSTEM 'file:///secret'>]><foo>&x;</foo>"));
        assertThatThrownBy(()->new AeatTestTransport().certificate("not-a-pfx","secret")).hasMessageContaining("P12/PFX");
    }
    @Test void wrapsUnchangedRecordAndEscapesIssuer() throws Exception {
        String record="<sf:RegistroAlta xmlns:sf=\""+AeatTestTransport.SF+"\"><sf:IDFactura><sf:IDEmisorFactura>89890001K</sf:IDEmisorFactura></sf:IDFactura><sf:NombreRazonEmisor>A &amp; B</sf:NombreRazonEmisor></sf:RegistroAlta>";
        String envelope=AeatTestTransport.envelope(record);
        assertThat(envelope).contains(record,"<sf:NombreRazon>A &amp; B</sf:NombreRazon>");
        assertThat(AeatTestTransport.xml(envelope).getElementsByTagNameNS(AeatTestTransport.LR,"RegFactuSistemaFacturacion").getLength()).isEqualTo(1);
    }
    @Test void describesHolderTaxIdsAndExpiryWithoutKeyMaterial(@TempDir Path dir) throws Exception {
        Path store=dir.resolve("test.p12");
        String keytool=Path.of(System.getProperty("java.home"),"bin","keytool").toString();
        var process=new ProcessBuilder(keytool,"-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048","-validity","30",
                "-storetype","PKCS12","-keystore",store.toString(),"-storepass","changeit","-keypass","changeit",
                "-dname","CN=PRUEBA DEMO - 99999999R, SERIALNUMBER=IDCES-99999999R, OID.2.5.4.97=VATES-B00000000, C=ES").redirectErrorStream(true).start();
        assertThat(process.waitFor()).isZero();
        String base64=Base64.getEncoder().encodeToString(Files.readAllBytes(store));
        var info=new AeatTestTransport().describe(base64,"changeit");
        assertThat(info.holder()).isEqualTo("PRUEBA DEMO - 99999999R");
        assertThat(info.personalTaxId()).isEqualTo("99999999R");
        assertThat(info.entityTaxId()).isEqualTo("B00000000");
        assertThat(info.expired()).isFalse();
        assertThat(info.validUntil()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(new AeatTestTransport().describe(base64,"wrong")).isNull();
    }
    @Test void normalisesSpanishTaxIdPrefixes() {
        assertThat(AeatTestTransport.taxId("IDCES-12345678Z")).isEqualTo("12345678Z");
        assertThat(AeatTestTransport.taxId("VATES-B12345678")).isEqualTo("B12345678");
        assertThat(AeatTestTransport.taxId("es12345678z")).isEqualTo("12345678Z");
        assertThat(AeatTestTransport.taxId(null)).isNull();
    }
}
