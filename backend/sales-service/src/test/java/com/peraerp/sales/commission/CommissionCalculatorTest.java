package com.peraerp.sales.commission;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionCalculatorTest {
    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID SALESPERSON = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID GROUP = UUID.randomUUID();

    private static CommissionRule rule(UUID product, UUID group, String from, String to, String percentage) {
        CommissionRule rule = new CommissionRule(COMPANY, SALESPERSON);
        rule.update(product, null, group, null, from == null ? null : new BigDecimal(from),
                to == null ? null : new BigDecimal(to), new BigDecimal(percentage), true);
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        return rule;
    }

    private static CommissionCalculator.Line line(String amount, UUID product, UUID group) {
        return new CommissionCalculator.Line(1, "Línea", new BigDecimal(amount), product, group);
    }

    @Test
    void theMostSpecificRuleWins() {
        CommissionRule general = rule(null, null, null, null, "2");
        CommissionRule byGroup = rule(null, GROUP, null, null, "4");
        CommissionRule byProduct = rule(PRODUCT, null, null, null, "6");
        List<CommissionRule> rules = List.of(general, byGroup, byProduct);

        var forProduct = CommissionCalculator.calculate(line("100", PRODUCT, GROUP), rules, null);
        assertThat(forProduct.getPercentage()).isEqualByComparingTo("6");
        assertThat(forProduct.getRuleId()).isEqualTo(byProduct.getId());
        assertThat(CommissionCalculator.calculate(line("100", UUID.randomUUID(), GROUP), rules, null).getPercentage())
                .isEqualByComparingTo("4");
        assertThat(CommissionCalculator.calculate(line("100", null, null), rules, null).getPercentage())
                .isEqualByComparingTo("2");
    }

    @Test
    void amountRangesPickTheNarrowestAndTreatCreditsByTheirSize() {
        List<CommissionRule> rules = List.of(rule(null, null, null, null, "1"), rule(null, null, "0", "500", "3"),
                rule(null, null, "100", "200", "5"));

        assertThat(CommissionCalculator.calculate(line("150", null, null), rules, null).getPercentage())
                .isEqualByComparingTo("5");
        assertThat(CommissionCalculator.calculate(line("300", null, null), rules, null).getPercentage())
                .isEqualByComparingTo("3");
        var credit = CommissionCalculator.calculate(line("-150", null, null), rules, null);
        assertThat(credit.getPercentage()).isEqualByComparingTo("5");
        assertThat(credit.getCommissionAmount()).isEqualByComparingTo("-7.50");
        assertThat(CommissionCalculator.calculate(line("900", null, null), rules, null).getPercentage())
                .isEqualByComparingTo("1");
    }

    @Test
    void fallsBackToTheSalespersonDefaultAndThenToNothing() {
        var byDefault = CommissionCalculator.calculate(line("80", null, null), List.of(), new BigDecimal("2.5"));
        assertThat(byDefault.getOrigin()).isEqualTo(SalesCommissionLine.Origin.DEFAULT);
        assertThat(byDefault.getCommissionAmount()).isEqualByComparingTo("2.00");
        var none = CommissionCalculator.calculate(line("80", null, null), List.of(), null);
        assertThat(none.getOrigin()).isEqualTo(SalesCommissionLine.Origin.NONE);
        assertThat(none.getCommissionAmount()).isEqualByComparingTo("0");
    }

    @Test
    void inactiveRulesAreIgnored() {
        CommissionRule off = rule(null, null, null, null, "9");
        off.update(null, null, null, null, null, null, new BigDecimal("9"), false);
        assertThat(CommissionCalculator.calculate(line("100", null, null), List.of(off), null).getOrigin())
                .isEqualTo(SalesCommissionLine.Origin.NONE);
    }

    @Test
    void aSettledCommissionIsNotRecalculated() {
        SalesCommission commission = new SalesCommission(COMPANY, UUID.randomUUID());
        commission.recalculate(SALESPERSON, "Marta", List.of(CommissionCalculator.calculate(line("100", null, null),
                List.of(), new BigDecimal("3"))), java.time.Instant.now());
        assertThat(commission.getCommissionAmount()).isEqualByComparingTo("3.00");
        commission.settle(java.time.LocalDate.of(2026, 10, 9), null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> commission.recalculate(SALESPERSON, "Marta", List.of(),
                java.time.Instant.now())).hasMessageContaining("liquidada");
        commission.reopen();
        assertThat(commission.isSettled()).isFalse();
    }
}
