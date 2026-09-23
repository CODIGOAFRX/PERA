package com.peraerp.sales.integration;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.net.ssl.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.naming.ldap.LdapName;
import javax.security.auth.x500.X500Principal;

@Component
public class AeatTestTransport {
    static final String SF="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd";
    static final String LR="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroLR.xsd";
    static final String RESPONSE="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/RespuestaSuministro.xsd";
    public record Result(String state,String csv,String message,int waitSeconds,String response) {}
    public SSLContext certificate(String base64,String password) {
        try {
            var store=KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(Base64.getDecoder().decode(base64)),password.toCharArray());
            int keys=0;
            for(var aliases=store.aliases();aliases.hasMoreElements();) {
                String alias=aliases.nextElement();
                if(store.isKeyEntry(alias)) { ((X509Certificate)store.getCertificate(alias)).checkValidity(); keys++; }
            }
            if(keys!=1) throw new IllegalArgumentException();
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(store,password.toCharArray());
            var ssl=SSLContext.getInstance("TLS"); ssl.init(km.getKeyManagers(),null,null); return ssl;
        } catch(Exception e) { throw new BusinessRuleException("El certificado debe ser un P12/PFX vigente con una única clave privada y contraseña correcta."); }
    }
    /** Public data of the stored certificate. Never exposes key material; an expired certificate is described, not rejected. */
    public record CertificateInfo(String holder,String personalTaxId,String entityTaxId,String issuer,String validUntil,boolean expired) {}
    public CertificateInfo describe(String base64,String password) {
        try {
            var store=KeyStore.getInstance("PKCS12");
            store.load(new ByteArrayInputStream(Base64.getDecoder().decode(base64)),password.toCharArray());
            for(var aliases=store.aliases();aliases.hasMoreElements();) {
                String alias=aliases.nextElement();
                if(!store.isKeyEntry(alias)) continue;
                var cert=(X509Certificate)store.getCertificate(alias);
                var subject=attributes(cert.getSubjectX500Principal()); var issuer=attributes(cert.getIssuerX500Principal());
                var notAfter=cert.getNotAfter().toInstant();
                return new CertificateInfo(subject.getOrDefault("CN",""),taxId(subject.get("SERIALNUMBER")),taxId(subject.get("ORGID")),
                        issuer.getOrDefault("CN",issuer.getOrDefault("O","")),notAfter.atOffset(ZoneOffset.UTC).toLocalDate().toString(),notAfter.isBefore(java.time.Instant.now()));
            }
            return null;
        } catch(Exception e) { return null; }
    }
    static Map<String,String> attributes(X500Principal principal) throws Exception {
        // FNMT puts the holder NIF in serialNumber (2.5.4.5) and the entity NIF in organizationIdentifier (2.5.4.97).
        var name=new LdapName(principal.getName(X500Principal.RFC2253,Map.of("2.5.4.5","SERIALNUMBER","2.5.4.97","ORGID")));
        var values=new LinkedHashMap<String,String>();
        for(var rdn:name.getRdns()) if(rdn.getValue() instanceof String value) values.putIfAbsent(rdn.getType().toUpperCase(),value);
        return values;
    }
    /** Normalises IDCES-12345678Z / VATES-B12345678 / ES12345678Z to the bare Spanish NIF. */
    static String taxId(String value) {
        if(value==null) return null;
        String v=value.trim().toUpperCase().replaceFirst("^(IDC|VAT|PAS)[A-Z]{2}-","");
        if(v.matches("ES[0-9A-Z]{9}")) v=v.substring(2);
        return v.isEmpty()?null:v;
    }
    public Result send(VerifactuRecord record,String base64,String password,boolean seal) throws Exception {
        var client=HttpClient.newBuilder().sslContext(certificate(base64,password)).connectTimeout(Duration.ofSeconds(15)).build();
        String host=seal?"prewww10.aeat.es":"prewww1.aeat.es";
        var req=HttpRequest.newBuilder(URI.create("https://"+host+"/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP"))
                .timeout(Duration.ofSeconds(45)).header("Content-Type","text/xml; charset=UTF-8").header("SOAPAction","\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope(record.getPayloadXml()))).build();
        var res=client.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()!=200) throw new IOException("AEAT HTTP "+res.statusCode());
        return parse(res.body(),record.getIssuerTaxId(),record.getInvoiceNumber(),record.getInvoiceDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
    }
    static Document xml(String value) throws Exception {
        if(value==null || value.length()>2_000_000) throw new IOException("Respuesta XML inválida");
        var f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static String value(Element element,String ns,String name) {
        var nodes=element.getElementsByTagNameNS(ns,name);
        return nodes.getLength()==1?nodes.item(0).getTextContent():"";
    }
    static String escape(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }
    static String envelope(String payload) throws Exception {
        var root=xml(payload).getDocumentElement();
        String issuer=value(root,SF,"NombreRazonEmisor");
        var ids=root.getElementsByTagNameNS(SF,"IDFactura");
        if(!SF.equals(root.getNamespaceURI()) || ids.getLength()!=1 || issuer.isBlank()) throw new IOException("Registro fiscal no válido");
        String nif=value((Element)ids.item(0),SF,"IDEmisorFactura");
        String body=payload.replaceFirst("^\\s*<\\?xml[^?]*\\?>","");
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:lr=\""+LR+"\" xmlns:sf=\""+SF+"\"><soap:Body><lr:RegFactuSistemaFacturacion><lr:Cabecera><sf:ObligadoEmision><sf:NombreRazon>"+escape(issuer)+"</sf:NombreRazon><sf:NIF>"+escape(nif)+"</sf:NIF></sf:ObligadoEmision></lr:Cabecera><lr:RegistroFactura>"+body+"</lr:RegistroFactura></lr:RegFactuSistemaFacturacion></soap:Body></soap:Envelope>";
    }
    static Result parse(String body,String nif,String number,String date) throws Exception {
        var doc=xml(body); var lines=doc.getElementsByTagNameNS(RESPONSE,"RespuestaLinea");
        if(lines.getLength()!=1) throw new IOException("La AEAT no ha devuelto una respuesta individual reconocible");
        var line=(Element)lines.item(0);
        if(!nif.equals(value(line,SF,"IDEmisorFactura")) || !number.equals(value(line,SF,"NumSerieFactura")) || !date.equals(value(line,SF,"FechaExpedicionFactura")))
            throw new IOException("La respuesta no corresponde al registro remitido");
        String status=value(line,RESPONSE,"EstadoRegistro");
        String state=switch(status) { case "Correcto"->"ACCEPTED"; case "AceptadoConErrores"->"ACCEPTED_WITH_ERRORS"; case "Incorrecto"->"REJECTED"; default->throw new IOException("Estado AEAT desconocido"); };
        // Duplicate receipts must be reconciled, never guessed to be an acceptance.
        if(line.getElementsByTagNameNS(RESPONSE,"RegistroDuplicado").getLength()>0) state="UNKNOWN";
        int wait=60; try { wait=Math.max(60,Integer.parseInt(value(doc.getDocumentElement(),RESPONSE,"TiempoEsperaEnvio"))); } catch(NumberFormatException ignored) {}
        return new Result(state,value(doc.getDocumentElement(),RESPONSE,"CSV"),value(line,RESPONSE,"CodigoErrorRegistro")+" "+value(line,RESPONSE,"DescripcionErrorRegistro"),wait,body);
    }
}
