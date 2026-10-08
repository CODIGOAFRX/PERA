package com.peraerp.sales.integration;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.naming.ldap.LdapName;
import javax.security.auth.x500.X500Principal;

@Component
public class AeatTestTransport {
    static final String SF="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroInformacion.xsd";
    static final String LR="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/SuministroLR.xsd";
    static final String RESPONSE="https://www2.agenciatributaria.gob.es/static_files/common/internet/dep/aplicaciones/es/aeat/tike/cont/ws/RespuestaSuministro.xsd";
    public record Result(String state,String csv,String message,int waitSeconds,String response) {}
    /** Resultado de un registro dentro de un envío: se identifica por número y fecha de la factura. */
    public record LineResult(String number,String date,String state,String errorCode,String message,String response) {}
    /** Respuesta a un envío de varios registros. */
    public record BatchResult(String csv,int waitSeconds,List<LineResult> lines,String response) {}
    /**
     * La AEAT rechazó el envío entero (fallo SOAP): no ha registrado ninguno de sus registros, así que
     * se pueden volver a remitir sin riesgo de duplicado.
     */
    public static class EnvelopeRejected extends IOException {
        public EnvelopeRejected(String message) { super(message); }
    }
    /** Máximo de registros por envío que admite la AEAT. */
    public static final int MAX_BATCH=1000;
    /**
     * Solo para pruebas: un servicio local que hace de AEAT de preproducción
     * ({@code pera.verifactu.aeat.test-endpoint}). Nunca se usa con empresas en producción.
     */
    private final URI endpointOverride;
    public AeatTestTransport() { this((URI)null); }
    @org.springframework.beans.factory.annotation.Autowired
    public AeatTestTransport(@org.springframework.beans.factory.annotation.Value("${pera.verifactu.aeat.test-endpoint:}") String testEndpoint) {
        this(testEndpoint==null || testEndpoint.isBlank()?null:URI.create(testEndpoint));
    }
    AeatTestTransport(URI endpointOverride) { this.endpointOverride=endpointOverride; }
    /**
     * Dirección del servicio según el entorno y el tipo de certificado, tal y como figuran en el WSDL
     * oficial (SistemaFacturacion.wsdl): www1/prewww1 con certificado normal y www10/prewww10 con sello.
     */
    static URI endpoint(VerifactuEnvironment environment,boolean seal) {
        String host=environment==VerifactuEnvironment.PRODUCTION
                ?(seal?"www10.agenciatributaria.gob.es":"www1.agenciatributaria.gob.es")
                :(seal?"prewww10.aeat.es":"prewww1.aeat.es");
        return URI.create("https://"+host+"/wlpl/TIKE-CONT/ws/SistemaFacturacion/VerifactuSOAP");
    }
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
    /**
     * Remite varios registros de un mismo obligado en un solo envío y devuelve el resultado de cada uno.
     *
     * <p>Un fallo de red o una respuesta ilegible lanzan {@link IOException}: el envío pudo llegar o no.
     * Solo {@link EnvelopeRejected} garantiza que la AEAT no registró nada.</p>
     */
    public BatchResult sendBatch(List<VerifactuRecord> records,String base64,String password,boolean seal,
                                 VerifactuEnvironment environment) throws Exception {
        URI uri=target(environment,seal);
        var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15));
        if(!uri.equals(endpointOverride)) builder.sslContext(certificate(base64,password));
        var req=HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(120)).header("Content-Type","text/xml; charset=UTF-8").header("SOAPAction","\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope(records.stream().map(VerifactuRecord::getPayloadXml).toList()))).build();
        var res=builder.build().send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()!=200) {
            String fault=fault(res.body());
            if(fault!=null) throw new EnvelopeRejected("La AEAT rechazó el envío: "+fault);
            throw new IOException("AEAT HTTP "+res.statusCode());
        }
        return parseBatch(res.body(),records.getFirst().getIssuerTaxId());
    }
    /** Adónde se remite: la AEAT oficial, salvo el servicio local de pruebas, que nunca se usa en producción. */
    URI target(VerifactuEnvironment environment,boolean seal) {
        return endpointOverride!=null && environment!=VerifactuEnvironment.PRODUCTION?endpointOverride:endpoint(environment,seal);
    }
    /** Texto de un fallo SOAP, o nulo si la respuesta no es un fallo reconocible. */
    static String fault(String body) {
        try {
            var faults=xml(body).getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/","Fault");
            if(faults.getLength()!=1) return null;
            var strings=((Element)faults.item(0)).getElementsByTagName("faultstring");
            String text=strings.getLength()==1?strings.item(0).getTextContent().trim():"";
            return text.isEmpty()?"fallo SOAP sin descripción":abbreviate(text,1400);
        } catch(Exception e) { return null; }
    }
    static String abbreviate(String value,int max) { return value.length()<=max?value:value.substring(0,max); }
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
    static String envelope(String payload) throws Exception { return envelope(List.of(payload)); }
    /** Sobre SOAP con varios registros. Todos tienen que ser del mismo obligado: va una sola cabecera. */
    static String envelope(List<String> payloads) throws Exception {
        if(payloads.isEmpty() || payloads.size()>MAX_BATCH) throw new IOException("Un envío lleva entre 1 y "+MAX_BATCH+" registros");
        String issuer=null,nif=null; var body=new StringBuilder();
        for(String payload:payloads) {
            var root=xml(payload).getDocumentElement();
            String name=value(root,SF,"NombreRazonEmisor");
            var ids=root.getElementsByTagNameNS(SF,"IDFactura");
            if(!SF.equals(root.getNamespaceURI()) || ids.getLength()!=1 || name.isBlank()) throw new IOException("Registro fiscal no válido");
            String id=value((Element)ids.item(0),SF,"IDEmisorFactura");
            if(nif==null) { nif=id; issuer=name; }
            else if(!nif.equals(id)) throw new IOException("Un envío solo puede llevar registros de un mismo emisor");
            body.append("<lr:RegistroFactura>").append(payload.replaceFirst("^\\s*<\\?xml[^?]*\\?>","")).append("</lr:RegistroFactura>");
        }
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:lr=\""+LR+"\" xmlns:sf=\""+SF+"\"><soap:Body><lr:RegFactuSistemaFacturacion><lr:Cabecera><sf:ObligadoEmision><sf:NombreRazon>"+escape(issuer)+"</sf:NombreRazon><sf:NIF>"+escape(nif)+"</sf:NIF></sf:ObligadoEmision></lr:Cabecera>"+body+"</lr:RegFactuSistemaFacturacion></soap:Body></soap:Envelope>";
    }
    /**
     * Lee la respuesta de un envío. Cada línea se devuelve con su número y fecha para casarla con su
     * registro; las líneas de otro emisor invalidan la respuesta entera.
     *
     * <p>Un registro «duplicado» es uno que la AEAT ya tenía: pasa al reenviar tras una respuesta que
     * no llegó. Se toma el estado que la AEAT tiene guardado, que es la forma de reconciliar que
     * propone la propia AEAT. Si no lo indica, queda sin confirmar.</p>
     */
    static BatchResult parseBatch(String body,String nif) throws Exception {
        var doc=xml(body); var nodes=doc.getElementsByTagNameNS(RESPONSE,"RespuestaLinea");
        if(doc.getElementsByTagNameNS(RESPONSE,"EstadoEnvio").getLength()==0 && nodes.getLength()==0)
            throw new IOException("La AEAT no ha devuelto una respuesta reconocible");
        int wait=60; try { wait=Math.max(60,Integer.parseInt(value(doc.getDocumentElement(),RESPONSE,"TiempoEsperaEnvio"))); } catch(NumberFormatException ignored) {}
        var lines=new ArrayList<LineResult>();
        for(int i=0;i<nodes.getLength();i++) {
            var line=(Element)nodes.item(i);
            if(!nif.equals(value(line,SF,"IDEmisorFactura"))) throw new IOException("La respuesta no corresponde al emisor remitido");
            String state=switch(value(line,RESPONSE,"EstadoRegistro")) { case "Correcto"->"ACCEPTED"; case "AceptadoConErrores"->"ACCEPTED_WITH_ERRORS"; case "Incorrecto"->"REJECTED"; default->throw new IOException("Estado AEAT desconocido"); };
            String code=value(line,RESPONSE,"CodigoErrorRegistro"), message=value(line,RESPONSE,"DescripcionErrorRegistro");
            var duplicates=line.getElementsByTagNameNS(RESPONSE,"RegistroDuplicado");
            if(duplicates.getLength()>0) {
                var duplicate=(Element)duplicates.item(0);
                state=switch(value(duplicate,SF,"EstadoRegistroDuplicado")) { case "Correcta"->"ACCEPTED"; case "AceptadaConErrores"->"ACCEPTED_WITH_ERRORS"; default->"UNKNOWN"; };
                String previous=value(duplicate,SF,"DescripcionErrorRegistro");
                message="La AEAT ya tenía este registro"+(previous.isBlank()?"":": "+previous);
                code=value(duplicate,SF,"CodigoErrorRegistro");
            }
            lines.add(new LineResult(value(line,SF,"NumSerieFactura"),value(line,SF,"FechaExpedicionFactura"),state,
                    code.isBlank()?null:code,message.isBlank()?null:abbreviate(message,1500),serialize(line)));
        }
        return new BatchResult(value(doc.getDocumentElement(),RESPONSE,"CSV"),wait,lines,body);
    }
    /** La línea de respuesta de un registro, para guardarla con él sin repetir la respuesta entera. */
    static String serialize(Element element) throws Exception {
        var transformer=javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION,"yes");
        var out=new StringWriter();
        transformer.transform(new javax.xml.transform.dom.DOMSource(element),new javax.xml.transform.stream.StreamResult(out));
        return out.toString();
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
