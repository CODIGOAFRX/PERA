package com.peraerp.sales.mail;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.*;
import com.peraerp.sales.masterdata.MasterDataClient;
import com.peraerp.sales.outbox.DomainEventRecorder;
import com.peraerp.sales.print.InvoicePdfService;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@Service
public class DocumentMailService {
    public record Status(String status, String recipient, String error, Instant sentAt, boolean connectionEnabled) {}
    public record Job(UUID id, UUID companyId, UUID documentId, String recipient, String subject, String filename, byte[] attachment) {}
    private final JdbcTemplate jdbc;
    private final CommercialDocumentRepository documents;
    private final MailConnectionService connections;
    private final InvoicePdfService pdf;
    private final CurrentCompanyProvider company;
    private final MasterDataClient masters;
    public DocumentMailService(JdbcTemplate jdbc, CommercialDocumentRepository documents, MailConnectionService connections,
                               InvoicePdfService pdf, CurrentCompanyProvider company, MasterDataClient masters) {
        this.jdbc=jdbc; this.documents=documents; this.connections=connections; this.pdf=pdf; this.company=company; this.masters=masters;
    }
    @EventListener
    public void onRecorded(DomainEventRecorder.Recorded event) {
        if (!event.eventType().equals("DocumentCreated") && !event.eventType().equals("DocumentConverted")) return;
        CommercialDocument d=documents.findByIdAndCompanyId(event.aggregateId(),company.requireCompanyId()).orElse(null);
        if (d==null || !d.getType().isInvoice() || !d.isIssued()) return;
        var connection=connections.find(d.getCompanyId());
        if (connection==null || !connection.enabled() || !connection.autoInvoices()) return;
        enqueue(d, false);
    }
    @Transactional(readOnly=true)
    public Status status(UUID documentId, boolean quote) {
        CommercialDocument d=require(documentId,quote);
        var connection=connections.find(d.getCompanyId());
        boolean enabled=connection!=null && connection.enabled();
        return jdbc.query("SELECT status,recipient,error,sent_at FROM document_mail WHERE company_id=? AND document_id=?",
                (rs,n)->new Status(rs.getString("status"),rs.getString("recipient"),rs.getString("error"),
                        rs.getTimestamp("sent_at")==null?null:rs.getTimestamp("sent_at").toInstant(),enabled),d.getCompanyId(),documentId)
                .stream().findFirst().orElse(new Status("NOT_SENT",d.getCustomerEmailSnapshot(),null,null,enabled));
    }
    @Transactional
    public Status queue(UUID documentId, boolean quote) {
        // Lock the document so two clicks cannot create competing deliveries.
        UUID companyId=company.requireCompanyId();
        jdbc.queryForList("SELECT id FROM commercial_documents WHERE id=? AND company_id=? FOR UPDATE",documentId,companyId);
        CommercialDocument d=require(documentId,quote);
        var c=connections.find(companyId);
        if(c==null || !c.enabled()) throw new BusinessRuleException("Configura y activa el correo en Conexiones.");
        if(!quote && !d.isIssued()) throw new BusinessRuleException("Expide la factura antes de enviarla por correo.");
        if(quote && (d.getQuoteStatus()==QuoteStatus.EXPIRED || d.getQuoteStatus()==QuoteStatus.REJECTED))
            throw new BusinessRuleException("No se puede enviar un presupuesto caducado o rechazado.");
        enqueue(d,true);
        return status(documentId,quote);
    }
    @Transactional
    public Status retry(UUID documentId, boolean quote, boolean confirmedNotDelivered) {
        CommercialDocument d = require(documentId, quote);
        if (!confirmedNotDelivered) throw new BusinessRuleException("Comprueba primero que el servidor SMTP no entregó el correo.");
        int updated = jdbc.update("UPDATE document_mail SET status='FAILED',updated_at=now() WHERE company_id=? AND document_id=? AND status='UNKNOWN'", d.getCompanyId(), documentId);
        if (updated != 1) throw new BusinessRuleException("Solo se pueden revisar entregas inciertas.");
        return queue(documentId, quote);
    }
    private void enqueue(CommercialDocument d, boolean manual) {
        var previous=jdbc.queryForList("SELECT status FROM document_mail WHERE company_id=? AND document_id=?",d.getCompanyId(),d.getId());
        if(!previous.isEmpty() && !"FAILED".equals(previous.getFirst().get("status"))) {
            if(manual) throw new BusinessRuleException("El documento ya está enviado, pendiente o requiere revisar una entrega incierta. No se reenviará automáticamente.");
            return;
        }
        String recipient=d.getCustomerEmailSnapshot();
        String error=null;
        InvoicePdfService.InvoicePdf attachment=null;
        try {
            if(recipient==null || recipient.isBlank()) recipient=masters.findCustomer(d.getCustomerId()).email();
            if(recipient==null || recipient.isBlank()) throw new BusinessRuleException("El cliente no tiene correo electrónico.");
            MailConnectionService.rejectNewlines(recipient);
            var address=new jakarta.mail.internet.InternetAddress(recipient,true); address.validate();
            if(!recipient.equals(address.getAddress())) throw new BusinessRuleException("El correo del cliente debe contener una sola dirección.");
            attachment=pdf.prepare(d.getId());
        } catch (Exception ex) {
            error="No se pudo preparar el correo. Revisa el email del cliente y los datos para generar el PDF.";
            if(manual) throw new BusinessRuleException(error);
        }
        String status=error==null ? "PENDING" : "FAILED";
        String subject=(d.getType()==DocumentType.QUOTE?"Presupuesto ":"Factura ")+d.getDocumentNumber();
        jdbc.update("""
                INSERT INTO document_mail(id,company_id,document_id,recipient,subject,filename,attachment,status,error)
                VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(company_id,document_id) DO UPDATE SET
                recipient=excluded.recipient,filename=excluded.filename,attachment=excluded.attachment,
                status=excluded.status,error=excluded.error,updated_at=now()
                """,UUID.randomUUID(),d.getCompanyId(),d.getId(),recipient,subject,
                attachment==null?null:attachment.fileName(),attachment==null?null:attachment.bytes(),status,error);
        if(error==null && d.getType()==DocumentType.QUOTE && d.getQuoteStatus()==QuoteStatus.DRAFT) d.confirm();
    }
    private CommercialDocument require(UUID id, boolean quote) {
        CommercialDocument d=documents.findByIdAndCompanyId(id,company.requireCompanyId())
                .orElseThrow(()->new ResourceNotFoundException("Documento",id));
        if(quote ? d.getType()!=DocumentType.QUOTE : !d.getType().isInvoice())
            throw new BusinessRuleException("El tipo de documento no corresponde con esta operación.");
        return d;
    }
    static Job readJob(ResultSet rs,int n) throws SQLException {
        return new Job(rs.getObject("id",UUID.class),rs.getObject("company_id",UUID.class),rs.getObject("document_id",UUID.class),
                rs.getString("recipient"),rs.getString("subject"),rs.getString("filename"),rs.getBytes("attachment"));
    }
}
