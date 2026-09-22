package com.peraerp.sales.mail;

import org.springframework.stereotype.Component;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;
import jakarta.mail.internet.InternetAddress;
import java.util.Properties;

@Component
public class SmtpTransport {
    private final MailSecretCipher cipher;
    public SmtpTransport(MailSecretCipher cipher) { this.cipher=cipher; }
    JavaMailSenderImpl sender(MailConnectionService.Connection c) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.host()); sender.setPort(c.port()); sender.setUsername(c.username());
        sender.setPassword(cipher.decrypt(c.companyId(),c.passwordCipher())); sender.setDefaultEncoding("UTF-8");
        Properties p=sender.getJavaMailProperties();
        p.setProperty("mail.smtp.auth","true");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.connectiontimeout","10000"); p.setProperty("mail.smtp.timeout","15000");
        p.setProperty("mail.smtp.writetimeout","15000");
        p.setProperty("mail.smtp.starttls.enable",String.valueOf(c.security().equals("STARTTLS")));
        p.setProperty("mail.smtp.starttls.required",String.valueOf(c.security().equals("STARTTLS")));
        p.setProperty("mail.smtp.ssl.enable",String.valueOf(c.security().equals("TLS")));
        return sender;
    }
    public void test(MailConnectionService.Connection c) throws Exception { sender(c).testConnection(); }
    public void send(MailConnectionService.Connection c, DocumentMailService.Job job) throws Exception {
        JavaMailSenderImpl sender=sender(c);
        var message=sender.createMimeMessage();
        var helper=new MimeMessageHelper(message,true,"UTF-8");
        helper.setFrom(new InternetAddress(c.senderEmail(),c.senderName(),"UTF-8"));
        helper.setTo(job.recipient()); helper.setSubject(job.subject());
        helper.setText("Adjuntamos su documento comercial en PDF.\n\nUn saludo,\n"+c.senderName(),false);
        helper.addAttachment(job.filename(),new ByteArrayResource(job.attachment()),"application/pdf");
        sender.send(message);
    }
}
