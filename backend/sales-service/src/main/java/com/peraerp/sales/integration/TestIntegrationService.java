package com.peraerp.sales.integration;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.*;
import com.peraerp.sales.mail.MailSecretCipher;
import com.peraerp.sales.verifactu.domain.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Explicit sandbox submissions. Claims are committed before HTTP so an uncertain result cannot create a duplicate. */
@Service
public class TestIntegrationService {
    public record Config(@NotBlank @Pattern(regexp="AEAT|B2B") String provider,
            @NotNull @Pattern(regexp="[A-Za-z0-9_-]{0,80}") String account,
            @Size(max=3000000) String certificate,@Size(max=1000) String password,@Size(max=1000) String apiKey,boolean enabled) {}
    public record View(String provider,boolean configured,boolean encryptionReady,String account,boolean enabled,String environment,
            AeatTestTransport.CertificateInfo certificate,Boolean issuerMatches) {}
    /** connectionActive lets the UI hide a provider the company has not enabled; it never reveals credentials. */
    public record Delivery(String state,String remoteId,String message,String updatedAt,boolean connectionActive) {
        public Delivery(String state,String remoteId,String message,String updatedAt) { this(state,remoteId,message,updatedAt,false); }
    }
    record Connection(String account,String secret,boolean enabled) {}
    private final JdbcTemplate jdbc; private final CurrentCompanyProvider company; private final MailSecretCipher cipher;
    private final ObjectMapper mapper; private final AeatTestTransport aeat; private final B2bTestTransport b2b;
    private final VerifactuSettingsRepository settings; private final VerifactuRecordRepository records;
    private final CommercialDocumentRepository documents; private final TransactionTemplate tx;
    public TestIntegrationService(JdbcTemplate jdbc,CurrentCompanyProvider company,MailSecretCipher cipher,ObjectMapper mapper,
            AeatTestTransport aeat,B2bTestTransport b2b,VerifactuSettingsRepository settings,VerifactuRecordRepository records,
            CommercialDocumentRepository documents,PlatformTransactionManager transactions) {
        this.jdbc=jdbc;this.company=company;this.cipher=cipher;this.mapper=mapper;this.aeat=aeat;this.b2b=b2b;
        this.settings=settings;this.records=records;this.documents=documents;this.tx=new TransactionTemplate(transactions);
    }
    private static void provider(String p) { if(!Set.of("AEAT","B2B").contains(p)) throw new BusinessRuleException("Proveedor desconocido"); }
    Connection connection(UUID id,String p) { return connection(id,p,false); }
    private Connection connection(UUID id,String p,boolean lock) {
        provider(p);
        return jdbc.query("SELECT account,secret_cipher,enabled FROM fiscal_connections WHERE company_id=? AND provider=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new Connection(rs.getString(1),rs.getString(2),rs.getBoolean(3)),id,p).stream().findFirst().orElse(null);
    }
    public View view(String p) {
        UUID id=company.requireCompanyId(); var c=connection(id,p);
        AeatTestTransport.CertificateInfo info=null; Boolean matches=null;
        if(c!=null && p.equals("AEAT")) {
            info=certificateInfo(id,c);
            String issuer=settings.findByCompanyId(id).map(VerifactuSettings::getIssuerTaxId).map(AeatTestTransport::taxId).orElse(null);
            if(info!=null && issuer!=null) matches=issuer.equals(info.personalTaxId()) || issuer.equals(info.entityTaxId());
        }
        return new View(p,c!=null,cipher.ready(),c==null?"":c.account(),c!=null&&c.enabled(),"TEST",info,matches);
    }
    /** Informative only: a missing server key or a damaged secret must not break the configuration screen. */
    private AeatTestTransport.CertificateInfo certificateInfo(UUID id,Connection c) {
        try { var s=secret(id,"AEAT",c); return aeat.describe(s.path("certificate").asText(),s.path("password").asText()); }
        catch(RuntimeException e) { return null; }
    }
    public View save(Config r) {
        provider(r.provider()); UUID id=company.requireCompanyId();
        // Same row lock as the delivery claim: an identity change cannot slip between the check and a claim.
        tx.executeWithoutResult(ignored->store(id,r));
        return view(r.provider());
    }
    private void store(UUID id,Config r) {
        var old=connection(id,r.provider(),true);
        String secret;
        if(r.provider().equals("AEAT")) {
            if(!Set.of("","CERTIFICATE","SEAL").contains(r.account())) throw new BusinessRuleException("Tipo de certificado no válido");
            if(r.certificate()!=null && !r.certificate().isBlank()) {
                String password=r.password()==null?"":r.password(); aeat.certificate(r.certificate(),password);
                secret=cipher.encrypt(id,mapper.writeValueAsString(Map.of("provider","AEAT","certificate",r.certificate(),"password",password)));
            } else secret=old==null?null:old.secret();
        } else {
            if(r.account().isBlank()) throw new BusinessRuleException("Indica el identificador de cuenta B2Brouter");
            if(r.apiKey()!=null && !r.apiKey().isBlank()) {
                if(!r.apiKey().startsWith("test_")) throw new BusinessRuleException("Introduce una clave sandbox con prefijo test_");
                secret=cipher.encrypt(id,mapper.writeValueAsString(Map.of("provider","B2B","apiKey",r.apiKey())));
            } else secret=old==null?null:old.secret();
        }
        if(secret==null) throw new BusinessRuleException("Introduce las credenciales de pruebas");
        if(old!=null && !old.account().equals(r.account()) && jdbc.queryForObject("SELECT count(*) FROM fiscal_deliveries WHERE company_id=? AND provider=?",Integer.class,id,r.provider())>0)
            throw new BusinessRuleException("Hay envíos vinculados a esta cuenta. No se puede sustituir su identidad.");
        jdbc.update("INSERT INTO fiscal_connections(company_id,provider,account,secret_cipher,enabled) VALUES(?,?,?,?,?) ON CONFLICT(company_id,provider) DO UPDATE SET account=excluded.account,secret_cipher=excluded.secret_cipher,enabled=excluded.enabled",id,r.provider(),r.account(),secret,r.enabled());
    }
    private tools.jackson.databind.JsonNode secret(UUID id,String p,Connection c) {
        var node=mapper.readTree(cipher.decrypt(id,c.secret()));
        if(!p.equals(node.path("provider").asText())) throw new BusinessRuleException("Credencial de otro proveedor");
        return node;
    }
    private Connection ready(UUID id,String p) {
        var c=connection(id,p); if(c==null || !c.enabled()) throw new BusinessRuleException("Configura y activa la conexión de pruebas en Conexiones"); return c;
    }
    private VerifactuSettings settings(UUID id) {
        return settings.findByCompanyId(id).orElseThrow(()->new BusinessRuleException("Configura primero la identidad fiscal de la empresa"));
    }
    public Map<String,String> test(String p) {
        UUID id=company.requireCompanyId(); var c=connection(id,p);
        if(c==null) throw new BusinessRuleException("Guarda primero la conexión");
        var s=secret(id,p,c);
        try {
            if(p.equals("AEAT")) { aeat.certificate(s.path("certificate").asText(),s.path("password").asText());
                return Map.of("message","Certificado y clave privada válidos. La autorización ante la AEAT se comprobará al remitir un registro de pruebas."); }
            b2b.verify(s.path("apiKey").asText(),c.account(),settings(id).getIssuerTaxId());
            return Map.of("message","Cuenta sandbox accesible, NIF correcto e informes fiscales automáticos desactivados.");
        } catch(BusinessRuleException e) { throw e; } catch(Exception e) { throw new BusinessRuleException("No se pudo verificar la conexión de pruebas. Revisa la cuenta, permisos y credenciales."); }
    }
    public Delivery status(String p,UUID source) {
        UUID id=company.requireCompanyId(); provider(p);
        var c=connection(id,p); boolean active=c!=null && c.enabled();
        return jdbc.query("SELECT state,remote_id,message,updated_at FROM fiscal_deliveries WHERE company_id=? AND provider=? AND source_id=?",
                (rs,n)->new Delivery(rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant().toString(),active),id,p,source)
                .stream().findFirst().orElse(new Delivery("NOT_SENT",null,"",null,active));
    }
    /** Must run inside a transaction: locks the connection so the claimed account is the one being used. */
    private void claim(UUID id,String p,UUID source,Connection used) {
        var current=connection(id,p,true);
        if(current==null || !current.enabled() || !current.account().equals(used.account()) || !current.secret().equals(used.secret()))
            throw new BusinessRuleException("La conexión de pruebas ha cambiado mientras se preparaba el envío. Revisa la configuración y vuelve a intentarlo.");
        int claimed=jdbc.update("INSERT INTO fiscal_deliveries(company_id,provider,source_id,state,account) VALUES(?,?,?,'IN_PROGRESS',?) ON CONFLICT DO NOTHING",id,p,source,used.account());
        if(claimed==0) throw new BusinessRuleException("Este envío ya está registrado. Consulta su estado antes de cualquier otra acción.");
    }
    private void finish(UUID id,String p,UUID source,String state,String remote,String message,String response) {
        jdbc.update("UPDATE fiscal_deliveries SET state=?,remote_id=COALESCE(?,remote_id),message=?,response=?,updated_at=now() WHERE company_id=? AND provider=? AND source_id=?",state,remote,message,response,id,p,source);
    }
    public Delivery sendAeat(UUID source) {
        UUID id=company.requireCompanyId(); var c=ready(id,"AEAT"); var s=secret(id,"AEAT",c);
        if(settings(id).getEnvironment()!=VerifactuEnvironment.TEST) throw new BusinessRuleException("Solo se permite el entorno TEST de VeriFactu");
        var r=records.findByIdAndCompanyId(source,id).orElseThrow(()->new BusinessRuleException("Registro no encontrado"));
        if(r.getState()!=VerifactuState.PENDING || r.getRecordType()!=VerifactuRecordType.ALTA) throw new BusinessRuleException("Solo se remiten altas pendientes");
        aeat.certificate(s.path("certificate").asText(),s.path("password").asText());
        try { AeatSchema.validate(AeatTestTransport.envelope(r.getPayloadXml())); }
        catch(Exception e) { throw new BusinessRuleException("El registro guardado no supera el esquema oficial de la AEAT: "+e.getMessage()); }
        // Serialise claims and the per-company wait window without holding a transaction across HTTP.
        tx.executeWithoutResult(ignored->{
            claim(id,"AEAT",source,c);
            int claimed=jdbc.update("UPDATE fiscal_connections SET next_send_at=now()+interval '60 seconds' WHERE company_id=? AND provider='AEAT' AND (next_send_at IS NULL OR next_send_at<=now())",id);
            if(claimed!=1) throw new BusinessRuleException("Espera al menos 60 segundos entre remisiones a la AEAT");
            int pending=jdbc.update("UPDATE verifactu_records SET state='SENT',attempt_count=attempt_count+1,last_attempt_at=now(),version=version+1 WHERE id=? AND company_id=? AND state='PENDING'",source,id);
            if(pending!=1) throw new BusinessRuleException("El registro ya no está pendiente de remisión. Consulta su estado.");
        });
        AeatTestTransport.Result result;
        try { result=aeat.send(r,s.path("certificate").asText(),s.path("password").asText(),c.account().equals("SEAL")); }
        catch(Exception e) {
            finish(id,"AEAT",source,"UNKNOWN",null,"No se pudo confirmar la respuesta. Revisa el registro en la AEAT de pruebas antes de repetir la remisión.",null);
            return status("AEAT",source);
        }
        try {
            tx.executeWithoutResult(ignored->{
                finish(id,"AEAT",source,result.state(),null,result.message(),result.response());
                jdbc.update("UPDATE fiscal_connections SET next_send_at=now()+(? * interval '1 second') WHERE company_id=? AND provider='AEAT'",result.waitSeconds(),id);
                jdbc.update("UPDATE verifactu_records SET state=?,aeat_csv=?,aeat_response=?,version=version+1 WHERE id=? AND company_id=?",result.state().equals("UNKNOWN")?"SENT":result.state(),result.csv(),result.response(),source,id);
            });
        } catch(RuntimeException e) {
            // The AEAT did answer: keep its response for reconciliation instead of discarding it.
            finish(id,"AEAT",source,"UNKNOWN",null,"La AEAT respondió, pero no se pudo actualizar el registro local. Revisa la respuesta guardada antes de repetir la remisión.",result.response());
        }
        return status("AEAT",source);
    }
    public Delivery sendB2b(UUID source,long contact) {
        UUID id=company.requireCompanyId(); var c=ready(id,"B2B"); var s=secret(id,"B2B",c); String key=s.path("apiKey").asText();
        var d=tx.execute(ignored->{var doc=documents.findByIdAndCompanyId(source,id).orElseThrow(()->new BusinessRuleException("Factura no encontrada"));doc.getLines().size();return doc;});
        var payload=B2bTestTransport.payload(d,contact);
        try { b2b.verify(key,c.account(),settings(id).getIssuerTaxId()); b2b.verifyContact(key,c.account(),contact,d); }
        catch(BusinessRuleException e) { throw e; } catch(Exception e) { throw new BusinessRuleException("No se pudo verificar la cuenta y el contacto sandbox. No se ha enviado la factura."); }
        tx.executeWithoutResult(ignored->claim(id,"B2B",source,c));
        String remote=null;
        try {
            var invoice=b2b.request(key,"POST","accounts/"+c.account()+"/invoices",payload).path("invoice");
            remote=invoice.path("id").asText();
            if(!remote.matches("[1-9][0-9]*")) throw new IllegalStateException();
            finish(id,"B2B",source,"DRAFT",remote,"Borrador creado; comprobando importes",null);
            B2bTestTransport.verifyTotals(invoice,d);
            // Persist before dispatch: a timeout must never trigger a second creation or send.
            finish(id,"B2B",source,"IN_PROGRESS",remote,"Envío solicitado",null);
            b2b.request(key,"POST","invoices/send_invoice/"+remote,null);
            finish(id,"B2B",source,"SUBMITTED",remote,"Envío sandbox solicitado. Actualiza para consultar el estado del destinatario.",null);
        } catch(BusinessRuleException e) { finish(id,"B2B",source,"REVIEW",remote,e.getMessage(),null); }
        catch(Exception e) { finish(id,"B2B",source,"UNKNOWN",remote,"Resultado sin confirmar. Consulta B2Brouter y actualiza el estado; no se repetirá automáticamente.",null); }
        return status("B2B",source);
    }
    public Delivery refreshB2b(UUID source) {
        UUID id=company.requireCompanyId(); var c=ready(id,"B2B"); var current=status("B2B",source);
        if(current.remoteId()==null) return current;
        try {
            var invoice=b2b.request(secret(id,"B2B",c).path("apiKey").asText(),"GET","invoices/"+current.remoteId(),null).path("invoice");
            if(!source.toString().equals(invoice.path("file_reference").asText())) throw new IllegalStateException();
            String state=invoice.path("state").asText();
            if(state.isBlank()) throw new IllegalStateException();
            // A totals mismatch stays visible: consulting the provider does not resolve the review.
            boolean review="REVIEW".equals(current.state());
            finish(id,"B2B",source,review?"REVIEW":"REMOTE",current.remoteId(),"Estado en B2Brouter: "+state+(review?". Pendiente de revisión: "+current.message():""),mapper.writeValueAsString(invoice));
        } catch(Exception e) { throw new BusinessRuleException("No se pudo actualizar el estado de B2Brouter"); }
        return status("B2B",source);
    }
}
