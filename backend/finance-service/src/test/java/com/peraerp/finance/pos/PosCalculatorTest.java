package com.peraerp.finance.pos;

import com.peraerp.finance.pos.PosCalculator.LineInput;
import com.peraerp.finance.pos.PosCalculator.Payment;
import com.peraerp.finance.pos.PosCalculator.TaxLine;
import com.peraerp.finance.pos.PosCalculator.Totals;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PosCalculatorTest {

    @Test
    void theRetailPriceAddsTheTaxToTheCataloguePriceRoundedToCents() {
        assertThat(PosCalculator.retailPrice(new BigDecimal("0.8264"), new BigDecimal("21"))).isEqualByComparingTo("1.00");
        assertThat(PosCalculator.retailPrice(new BigDecimal("2.50"), new BigDecimal("10"))).isEqualByComparingTo("2.75");
        assertThat(PosCalculator.retailPrice(new BigDecimal("1.20"), BigDecimal.ZERO)).isEqualByComparingTo("1.20");
    }

    @Test
    void lineAndTicketDiscountsAreAppliedAndBaseAndTaxAddUpToWhatIsCharged() {
        Totals totals = PosCalculator.saleTotals(List.of(
                line("3", "1.21", "0", "21"),
                line("2", "2.75", "10", "10"),
                line("1", "0.99", "0", "4")), new BigDecimal("5"));

        // 3,63 · 0,95 = 3,45 | 5,50 · 0,90 = 4,95 · 0,95 = 4,70 | 0,99 · 0,95 = 0,94
        assertThat(totals.lineTotals()).containsExactly(new BigDecimal("3.45"), new BigDecimal("4.70"),
                new BigDecimal("0.94"));
        assertThat(totals.grossAmount()).isEqualByComparingTo("10.12");
        assertThat(totals.totalAmount()).isEqualByComparingTo("9.09");
        assertThat(totals.discountAmount()).isEqualByComparingTo("1.03");
        assertThat(totals.taxBaseAmount().add(totals.taxAmount())).isEqualByComparingTo(totals.totalAmount());
        assertThat(totals.taxes()).extracting(TaxLine::rate)
                .containsExactly(new BigDecimal("4"), new BigDecimal("10"), new BigDecimal("21"));
        TaxLine general = totals.taxes().get(2);
        assertThat(general.base()).isEqualByComparingTo("2.85");
        assertThat(general.tax()).isEqualByComparingTo("0.60");
    }

    @Test
    void theTaxBreakdownGroupsEqualRatesWrittenDifferently() {
        List<TaxLine> taxes = PosCalculator.taxBreakdown(List.of(new BigDecimal("12.10"), new BigDecimal("1.21")),
                List.of(new BigDecimal("21.0000"), new BigDecimal("21")));

        assertThat(taxes).hasSize(1);
        assertThat(taxes.getFirst().base()).isEqualByComparingTo("11.00");
        assertThat(taxes.getFirst().tax()).isEqualByComparingTo("2.31");
    }

    @Test
    void cashGivesChangeAndRejectsTooLittleMoney() {
        Payment payment = PosCalculator.payment(PosPaymentMethod.CASH, new BigDecimal("7.35"), new BigDecimal("20"), null);

        assertThat(payment.cashAmount()).isEqualByComparingTo("7.35");
        assertThat(payment.changeAmount()).isEqualByComparingTo("12.65");
        assertThat(payment.cardAmount()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> PosCalculator.payment(PosPaymentMethod.CASH, new BigDecimal("7.35"),
                new BigDecimal("5"), null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void withoutTenderedCashTheExactAmountIsAssumed() {
        Payment payment = PosCalculator.payment(PosPaymentMethod.CASH, new BigDecimal("3.10"), null, null);

        assertThat(payment.cashTendered()).isEqualByComparingTo("3.10");
        assertThat(payment.changeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void cardChargesTheExactTotal() {
        Payment payment = PosCalculator.payment(PosPaymentMethod.CARD, new BigDecimal("42.00"), new BigDecimal("50"), null);

        assertThat(payment.cardAmount()).isEqualByComparingTo("42.00");
        assertThat(payment.cashAmount()).isEqualByComparingTo("0");
        assertThat(payment.cashTendered()).isNull();
    }

    @Test
    void aMixedPaymentSplitsBetweenCardAndCash() {
        Payment payment = PosCalculator.payment(PosPaymentMethod.MIXED, new BigDecimal("50.00"), new BigDecimal("20"),
                new BigDecimal("35"));

        assertThat(payment.cardAmount()).isEqualByComparingTo("35");
        assertThat(payment.cashAmount()).isEqualByComparingTo("15.00");
        assertThat(payment.changeAmount()).isEqualByComparingTo("5.00");
        assertThatThrownBy(() -> PosCalculator.payment(PosPaymentMethod.MIXED, new BigDecimal("50"), null,
                new BigDecimal("50"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PosCalculator.payment(PosPaymentMethod.MIXED, new BigDecimal("50"), null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anEmptyTicketIsRejected() {
        assertThatThrownBy(() -> PosCalculator.saleTotals(List.of(), BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static LineInput line(String quantity, String price, String discount, String rate) {
        return new LineInput(UUID.randomUUID(), "P", "Producto", null, "UNIT", new BigDecimal(quantity),
                new BigDecimal(price), false, new BigDecimal(discount), new BigDecimal(rate));
    }
}
