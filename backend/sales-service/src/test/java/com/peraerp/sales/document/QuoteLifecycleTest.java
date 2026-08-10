package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteLifecycleTest {
    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 8, 10);

    @Test
    void supportsDraftSentAcceptedConvertedLifecycle() {
        CommercialDocument quote = quote();
        quote.configureQuoteValidity(ISSUE_DATE.plusDays(15));

        quote.confirm();
        quote.acceptQuote(Instant.parse("2026-08-11T10:00:00Z"), ISSUE_DATE.plusDays(1));
        quote.markConverted();

        assertThat(quote.getQuoteValidUntil()).isEqualTo(ISSUE_DATE.plusDays(15));
        assertThat(quote.getQuoteStatus()).isEqualTo(QuoteStatus.CONVERTED);
        assertThat(quote.getStatus()).isEqualTo(DocumentStatus.CONVERTED);
    }

    @Test
    void rejectsExpiredAndRejectedQuotesFromFurtherAcceptance() {
        CommercialDocument expired = quote();
        expired.configureQuoteValidity(ISSUE_DATE.plusDays(1));
        expired.confirm();

        assertThatThrownBy(() -> expired.acceptQuote(Instant.now(), ISSUE_DATE.plusDays(2)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(expired.getQuoteStatus()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(expired.getStatus()).isEqualTo(DocumentStatus.CANCELLED);

        CommercialDocument rejected = quote();
        rejected.confirm();
        rejected.rejectQuote("Sin presupuesto", Instant.now(), ISSUE_DATE);
        assertThat(rejected.getQuoteStatus()).isEqualTo(QuoteStatus.REJECTED);
        assertThat(rejected.getQuoteRejectionReason()).isEqualTo("Sin presupuesto");
    }

    @Test
    void protectsValidityAndStateTransitions() {
        CommercialDocument quote = quote();
        assertThatThrownBy(() -> quote.configureQuoteValidity(ISSUE_DATE.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class);
        quote.confirm();
        assertThatThrownBy(quote::confirm).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> quote.configureQuoteValidity(ISSUE_DATE.plusDays(60)))
                .isInstanceOf(BusinessRuleException.class);
    }

    private CommercialDocument quote() {
        return new CommercialDocument(UUID.randomUUID(), "PRE-1", DocumentType.QUOTE, UUID.randomUUID(),
                "C-1", "Cliente", ISSUE_DATE, null, "EUR", null, null, null);
    }
}
