package com.peraerp.sales.integration;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.document.*;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;

@Component
public class B2bTestTransport {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI base;
    @org.springframework.beans.factory.annotation.Autowired
    public B2bTestTransport(ObjectMapper mapper) { this(mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),URI.create("https://api.b2brouter.net/")); }
    B2bTestTransport(ObjectMapper mapper,HttpClient client,URI base) { this.mapper=mapper;this.client=client;this.base=base; }
    JsonNode request(String key,String method,String path,Object body) throws Exception {
        if(key==null || !key.startsWith("test_")) throw new BusinessRuleException("Solo se admiten claves sandbox de B2Brouter con prefijo test_.");
        var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(45))
                .header("X-B2B-API-Key",key).header("X-B2B-API-Version","2026-06-26").header("Accept","application/json");
        if(body!=null) builder.header("Content-Type","application/json");
        var res=client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()<200 || res.statusCode()>299) throw new IOException("B2Brouter HTTP "+res.statusCode()+". Consulta la cuenta sandbox para el detalle.");
        if(res.body().length()>2_000_000) throw new IOException("Respuesta demasiado grande");
        return res.body().isBlank()?mapper.createObjectNode():mapper.readTree(res.body());
    }
    void verify(String key,String account,String issuer) throws Exception {
        var a=request(key,"GET","accounts/"+account,null).path("account");
        if(!normalize(a.path("tin_value").asText()).equals(normalize(issuer)) || issuer==null || issuer.isBlank())
            throw new BusinessRuleException("El NIF de la cuenta B2Brouter no coincide con el emisor configurado en PERA.");
        var settings=request(key,"GET","accounts/"+account+"/tax_report_settings",null).path("tax_report_settings");
        if(!settings.isArray()) throw new IOException("No se pudo comprobar la configuración fiscal de B2Brouter");
        for(var setting:settings) if(setting.path("auto_generate").asBoolean()) throw new BusinessRuleException("Desactiva los informes fiscales automáticos de B2Brouter: VeriFactu se remite directamente desde PERA.");
    }
    static String normalize(String value) { return value==null?"":value.replaceAll("[\\s.-]", "").toUpperCase(Locale.ROOT); }
    void verifyContact(String key,String account,long contact,CommercialDocument d) throws Exception {
        var c=request(key,"GET","contacts/"+contact,null).path("contact");
        if(d.getCustomerTaxIdSnapshot()==null || !normalize(d.getCustomerTaxIdSnapshot()).equals(normalize(c.path("tin_value").asText())))
            throw new BusinessRuleException("El NIF del contacto B2Brouter no coincide con el cliente de esta factura.");
    }
    static Map<String,Object> payload(CommercialDocument d,long contact) {
        if(!d.isIssued() || d.getType()!=DocumentType.INVOICE || d.getInvoiceKind()!=InvoiceKind.F1)
            throw new BusinessRuleException("El envío B2B de pruebas admite facturas ordinarias F1 emitidas. No admite borradores, simplificadas ni rectificativas todavía.");
        var lines=new ArrayList<Map<String,Object>>();
        for(var l:d.getLines()) {
            if(l.getQuantity().signum()<=0 || l.getNetAmount().signum()<0 || Boolean.TRUE.equals(l.getTaxExemptSnapshot())
                    || (l.getTaxQualificationSnapshot()!=null && !"S1".equals(l.getTaxQualificationSnapshot().code()))
                    || (l.getTaxRegimeKeySnapshot()!=null && !l.getTaxRegimeKeySnapshot().equals("01")))
                throw new BusinessRuleException("Este envío de pruebas admite líneas positivas de IVA ordinario. Revisa el régimen y la calificación fiscal.");
            // Price per base quantity preserves resolved tariffs and discounts without dividing and losing precision.
            lines.add(Map.of("description",l.getDescription(),"quantity",l.getQuantity(),"base_quantity",l.getQuantity(),
                    "price",l.getNetAmount(),"taxes_attributes",List.of(Map.of("name","IVA","percent",l.getTaxPercentage(),"category",l.getTaxPercentage().signum()==0?"Z":"S"))));
        }
        var invoice=new LinkedHashMap<String,Object>();
        invoice.put("type","IssuedInvoice"); invoice.put("number",d.getDocumentNumber()); invoice.put("date",d.getIssueDate().toString());
        if(d.getDueDate()!=null) invoice.put("due_date",d.getDueDate().toString());
        invoice.put("currency",d.getCurrency()); invoice.put("contact_id",contact); invoice.put("file_reference",d.getId().toString());
        invoice.put("invoice_lines_attributes",lines);
        return Map.of("send_after_import",false,"issue_after_import",false,"invoice",invoice);
    }
    static void verifyTotals(JsonNode invoice,CommercialDocument d) {
        try {
            if(new BigDecimal(invoice.path("total").asText()).compareTo(d.getTotalAmount())!=0
                    || new BigDecimal(invoice.path("subtotal").asText()).compareTo(d.getNetAmount())!=0
                    || !invoice.path("currency").asText().equals(d.getCurrency())) throw new IllegalArgumentException();
        } catch(Exception e) { throw new BusinessRuleException("Los importes o la moneda calculados por B2Brouter no coinciden con PERA. El borrador remoto no se ha enviado."); }
    }
}
