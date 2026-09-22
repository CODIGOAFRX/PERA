package com.peraerp.sales.mail;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
public class MailQueueWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MailConnectionService connections;
    private final SmtpTransport smtp;
    public MailQueueWorker(JdbcTemplate jdbc, PlatformTransactionManager manager, MailConnectionService connections, SmtpTransport smtp) {
        this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.connections=connections;this.smtp=smtp;
    }
    @Scheduled(fixedDelayString="${pera.mail.poll-ms:5000}")
    public void deliverNext() {
        // A process interrupted after SMTP acceptance must never silently duplicate an invoice email.
        jdbc.update("UPDATE document_mail SET status='UNKNOWN',error='Entrega incierta: comprueba el servidor SMTP antes de reenviar.',updated_at=now() WHERE status='SENDING' AND updated_at < now() - interval '5 minutes'");
        DocumentMailService.Job job=tx.execute(s -> {
            var jobs=jdbc.query("""
                    SELECT m.* FROM document_mail m JOIN mail_connections c ON c.company_id=m.company_id
                    WHERE m.status='PENDING' AND c.enabled=true ORDER BY m.created_at LIMIT 1 FOR UPDATE OF m SKIP LOCKED
                    """,DocumentMailService::readJob);
            if(jobs.isEmpty()) return null;
            var picked=jobs.getFirst();
            jdbc.update("UPDATE document_mail SET status='SENDING',updated_at=now() WHERE id=?",picked.id());
            return picked;
        });
        if(job==null) return;
        var c=connections.find(job.companyId());
        if(c==null || !c.enabled()) {
            jdbc.update("UPDATE document_mail SET status='FAILED',error='La conexión de correo está desactivada.',updated_at=now() WHERE id=? AND company_id=?",job.id(),job.companyId());
            return;
        }
        try {
            smtp.send(c,job);
            jdbc.update("UPDATE document_mail SET status='SENT',sent_at=now(),updated_at=now(),error=null,attachment=null WHERE id=? AND company_id=?",job.id(),job.companyId());
        } catch (Exception e) {
            // SMTP failures can occur after acceptance. Require human reconciliation rather than blind retry.
            jdbc.update("UPDATE document_mail SET status='UNKNOWN',error='No se pudo confirmar la entrega. Comprueba el servidor SMTP para evitar duplicados.',updated_at=now() WHERE id=? AND company_id=?",job.id(),job.companyId());
        }
    }
}
