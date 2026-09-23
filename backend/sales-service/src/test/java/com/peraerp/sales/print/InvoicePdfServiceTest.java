package com.peraerp.sales.print;

import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.*;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InvoicePdfServiceTest {
    @Test
    void printedTaxRowsSumToTheInvoiceTotalsAfterRounding() {
        UUID company = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var invoice = new CommercialDocument(company, "FAC-TEST", DocumentType.INVOICE, UUID.randomUUID(),
                "C1", "Cliente", LocalDate.of(2026, 9, 23), null, "EUR", null, null, null);
        invoice.addLine(new DocumentLine(null, "A", "A", BigDecimal.ONE, new BigDecimal("0.03"),
                BigDecimal.ZERO, new BigDecimal("21")));
        invoice.addLine(new DocumentLine(null, "B", "B", BigDecimal.ONE, new BigDecimal("0.06"),
                BigDecimal.ZERO, new BigDecimal("10")));
        invoice.recalculate(new DocumentAmountsCalculator());
        invoice.confirm();
        var documents = mock(CommercialDocumentRepository.class);
        when(documents.findByIdAndCompanyId(id, company)).thenReturn(Optional.of(invoice));
        var provider = mock(CurrentCompanyProvider.class);
        when(provider.requireCompanyId()).thenReturn(company);
        var profile = mock(HttpCompanyProfileClient.class);
        when(profile.profile()).thenReturn(new CompanyProfile("Empresa", null, null, null, null, null,
                null, null, null, null));
        var renderer = mock(InvoicePdfRenderer.class);
        when(renderer.render(any())).thenReturn(new byte[0]);
        var service = new InvoicePdfService(documents, mock(VerifactuRecordRepository.class),
                mock(VerifactuSettingsRepository.class), profile, mock(HttpPaymentMethodClient.class), renderer, provider);

        service.render(id);

        var content = ArgumentCaptor.forClass(InvoicePdfContent.class);
        verify(renderer).render(content.capture());
        var printed = content.getValue();
        assertThat(printed.taxes().stream().map(InvoicePdfContent.TaxRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(printed.taxAmount());
        assertThat(printed.taxes().stream().map(InvoicePdfContent.TaxRow::base)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(printed.netAmount());
        assertThat(printed.totalAmount()).isEqualByComparingTo("0.10");
    }
}
