package com.peraerp.sales.mail;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.*;
import com.peraerp.sales.masterdata.MasterDataClient;
import com.peraerp.sales.outbox.DomainEventRecorder;
import com.peraerp.sales.print.InvoicePdfService;
import com.peraerp.platform.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class DocumentMailServiceTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    CommercialDocumentRepository docs=mock(CommercialDocumentRepository.class);
    MailConnectionService connections=mock(MailConnectionService.class);
    InvoicePdfService pdf=mock(InvoicePdfService.class);
    CurrentCompanyProvider company=mock(CurrentCompanyProvider.class);
    MasterDataClient masters=mock(MasterDataClient.class);
    UUID tenant=UUID.randomUUID(),id=UUID.randomUUID();
    DocumentMailService service=new DocumentMailService(jdbc,docs,connections,pdf,company,masters);
    @BeforeEach void setup() {when(company.requireCompanyId()).thenReturn(tenant);}
    @Test void refusesDocumentsFromAnotherTenant() {
        when(docs.findByIdAndCompanyId(id,tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.queue(id,false)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(pdf,masters,connections);
    }
    @Test void doesNotQueueDraftInvoices() {
        var d=document(false); when(docs.findByIdAndCompanyId(id,tenant)).thenReturn(Optional.of(d));
        service.onRecorded(new DomainEventRecorder.Recorded(id,"DocumentCreated"));
        verifyNoInteractions(connections,pdf,jdbc);
    }
    @Test void onlyAutoQueuesWhenCompanyHasOptedIn() {
        var d=document(true); when(docs.findByIdAndCompanyId(id,tenant)).thenReturn(Optional.of(d));
        when(connections.find(tenant)).thenReturn(connection(false));
        service.onRecorded(new DomainEventRecorder.Recorded(id,"DocumentCreated"));
        verifyNoInteractions(pdf,jdbc);
    }
    @Test void automaticDeliveryUsesSavedPdfAndTrustedRecipient() {
        var d=document(true); when(docs.findByIdAndCompanyId(id,tenant)).thenReturn(Optional.of(d));
        when(connections.find(tenant)).thenReturn(connection(true));
        when(pdf.prepare(id)).thenReturn(new InvoicePdfService.InvoicePdf("Factura-test.pdf",new byte[]{1,2,3}));
        service.onRecorded(new DomainEventRecorder.Recorded(id,"DocumentCreated"));
        verify(pdf).prepare(id);
        verify(jdbc).update(contains("INSERT INTO document_mail"),any(UUID.class),eq(tenant),eq(id),eq("client@example.test"),
                eq("Factura F-1"),eq("Factura-test.pdf"),eq(new byte[]{1,2,3}),eq("PENDING"),isNull());
    }
    @Test void cannotSendInvoiceUsingQuotePermissions() {
        var invoice = document(true);
        when(docs.findByIdAndCompanyId(id,tenant)).thenReturn(Optional.of(invoice));
        assertThatThrownBy(()->service.queue(id,true)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(pdf);
    }
    CommercialDocument document(boolean issued) {
        var d=mock(CommercialDocument.class);
        when(d.getId()).thenReturn(id); when(d.getCompanyId()).thenReturn(tenant); when(d.getType()).thenReturn(DocumentType.INVOICE);
        when(d.isIssued()).thenReturn(issued); when(d.getCustomerEmailSnapshot()).thenReturn("client@example.test");
        when(d.getDocumentNumber()).thenReturn("F-1"); return d;
    }
    MailConnectionService.Connection connection(boolean auto) {
        return new MailConnectionService.Connection(tenant,"smtp.example.test",587,"user","cipher","sender@example.test","Demo","STARTTLS",true,auto,Instant.now());
    }
}
