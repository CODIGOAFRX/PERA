package com.peraerp.sales.integration;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.*;
import com.peraerp.sales.mail.MailSecretCipher;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Claim and configuration races of the sandbox deliveries, without network or database. */
class TestIntegrationServiceTest {
    private final UUID company = UUID.randomUUID();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final B2bTestTransport b2b = mock(B2bTestTransport.class);
    private final CommercialDocumentRepository documents = mock(CommercialDocumentRepository.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private TestIntegrationService service;
    /** Row returned by the plain read and by the locked read of fiscal_connections. */
    private TestIntegrationService.Connection read, locked;
    private final List<Object> deliveries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var companies = mock(CurrentCompanyProvider.class);
        when(companies.requireCompanyId()).thenReturn(company);
        var cipher = mock(MailSecretCipher.class);
        when(cipher.decrypt(any(), any())).thenReturn("{\"provider\":\"B2B\",\"apiKey\":\"test_key\"}");
        when(cipher.encrypt(any(), any())).thenReturn("cipher-new");
        when(cipher.ready()).thenReturn(true);
        var settings = mock(VerifactuSettingsRepository.class);
        var identity = mock(VerifactuSettings.class);
        when(identity.getIssuerTaxId()).thenReturn("B12345678");
        when(settings.findByCompanyId(company)).thenReturn(Optional.of(identity));
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());
        read = locked = new TestIntegrationService.Connection("42", "cipher-1", true);
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.startsWith("SELECT state")) return deliveries;
            var row = sql.contains("FOR UPDATE") ? locked : read;
            return row == null ? List.of() : List.of(row);
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        service = new TestIntegrationService(jdbc, companies, cipher, mapper, mock(AeatTestTransport.class), b2b,
                settings, mock(VerifactuRecordRepository.class), documents, transactions);
    }

    private CommercialDocument invoice() {
        var d = new CommercialDocument(company, "FAC1", DocumentType.INVOICE, UUID.randomUUID(), "C1", "Cliente",
                LocalDate.of(2026, 9, 23), null, "EUR", null, null, null);
        d.addLine(new DocumentLine(null, "P1", "Producto", new BigDecimal("2"), new BigDecimal("27.95"), BigDecimal.ZERO, new BigDecimal("21")));
        org.springframework.test.util.ReflectionTestUtils.setField(d, "id", UUID.randomUUID());
        d.recalculate(new DocumentAmountsCalculator());
        d.confirm();
        when(documents.findByIdAndCompanyId(d.getId(), company)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void refusesToClaimWhenTheConnectionChangedAfterItWasRead() throws Exception {
        var d = invoice();
        locked = new TestIntegrationService.Connection("99", "cipher-2", true);

        assertThatThrownBy(() -> service.sendB2b(d.getId(), 7)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ha cambiado");
        verify(jdbc, never()).update(startsWith("INSERT INTO fiscal_deliveries"), any(Object[].class));
        verify(b2b, never()).request(any(), any(), any(), any());
    }

    @Test
    void refusesToClaimWhenTheConnectionWasDisabledMeanwhile() throws Exception {
        var d = invoice();
        locked = new TestIntegrationService.Connection("42", "cipher-1", false);

        assertThatThrownBy(() -> service.sendB2b(d.getId(), 7)).hasMessageContaining("ha cambiado");
        verify(b2b, never()).request(any(), any(), any(), any());
    }

    @Test
    void claimsWithTheLockedAccountBeforeCreatingTheRemoteDraft() throws Exception {
        var d = invoice();
        when(b2b.request(eq("test_key"), eq("POST"), eq("accounts/42/invoices"), any()))
                .thenReturn(mapper.readTree("{\"invoice\":{\"id\":5,\"total\":67.64,\"subtotal\":55.90,\"currency\":\"EUR\"}}"));
        when(b2b.request(eq("test_key"), eq("POST"), eq("invoices/send_invoice/5"), isNull()))
                .thenReturn(mapper.createObjectNode());

        service.sendB2b(d.getId(), 7);

        var order = inOrder(jdbc, b2b);
        order.verify(jdbc).query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class));
        order.verify(jdbc).update(startsWith("INSERT INTO fiscal_deliveries"), any(Object[].class));
        order.verify(b2b).request(eq("test_key"), eq("POST"), eq("accounts/42/invoices"), any());
        order.verify(b2b).request(eq("test_key"), eq("POST"), eq("invoices/send_invoice/5"), isNull());
    }

    @Test
    void refreshKeepsATotalsReviewVisible() throws Exception {
        UUID source = UUID.randomUUID();
        deliveries.add(new TestIntegrationService.Delivery("REVIEW", "5", "Los importes no coinciden", "2026-09-23T10:00:00Z"));
        when(b2b.request("test_key", "GET", "invoices/5", null))
                .thenReturn(mapper.readTree("{\"invoice\":{\"file_reference\":\"" + source + "\",\"state\":\"draft\"}}"));

        service.refreshB2b(source);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(startsWith("UPDATE fiscal_deliveries"), args.capture());
        assertThat(args.getValue()[0]).isEqualTo("REVIEW");
        assertThat((String) args.getValue()[2]).contains("draft", "Los importes no coinciden");
    }

    @Test
    void saveReadsTheExistingIdentityUnderLockAndBlocksAccountSwapsWithDeliveries() {
        when(jdbc.queryForObject(startsWith("SELECT count(*)"), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertThatThrownBy(() -> service.save(new TestIntegrationService.Config("B2B", "77", null, null, null, true)))
                .hasMessageContaining("envíos vinculados");
        verify(jdbc).query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class));
        verify(jdbc, never()).update(startsWith("INSERT INTO fiscal_connections"), any(Object[].class));
    }

    @Test
    void statusTellsWhetherTheProviderIsActiveWithoutAnyDelivery() {
        assertThat(service.status("B2B", UUID.randomUUID())).extracting(TestIntegrationService.Delivery::state, TestIntegrationService.Delivery::connectionActive)
                .containsExactly("NOT_SENT", true);
        read = new TestIntegrationService.Connection("42", "cipher-1", false);
        assertThat(service.status("B2B", UUID.randomUUID()).connectionActive()).isFalse();
        read = null;
        assertThat(service.status("B2B", UUID.randomUUID()).connectionActive()).isFalse();
    }
}
