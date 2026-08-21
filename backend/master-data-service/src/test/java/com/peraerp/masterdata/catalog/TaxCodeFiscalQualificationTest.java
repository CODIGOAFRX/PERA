package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calificación fiscal del código de impuesto.
 *
 * <p>Un booleano no distingue una exportación exenta de una operación con inversión del sujeto
 * pasivo, y ambas van al 0 %. Declarar la equivocada es declarar mal, así que el código fiscal
 * tiene que decir exactamente qué es.</p>
 */
class TaxCodeFiscalQualificationTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final LocalDate VALID_FROM = LocalDate.of(2026, 1, 1);

    private TaxCode taxCode(BigDecimal percentage, OperationQualification qualification,
                            ExemptionCause cause, String regimeKey) {
        return new TaxCode(COMPANY, "ES", "IVA21", "IVA general", percentage, VALID_FROM, null,
                qualification, cause, regimeKey, true);
    }

    // --- compatibilidad con el booleano heredado ---

    @Test
    void legacyConstructorTreatsNonExemptAsTheOrdinaryCase() {
        TaxCode general = new TaxCode(COMPANY, "ES", "IVA21", "IVA general", new BigDecimal("21"),
                VALID_FROM, null, false, true);

        assertThat(general.getOperationQualification()).isEqualTo(OperationQualification.SUBJECT_NOT_EXEMPT);
        assertThat(general.getExemptionCause()).isNull();
        assertThat(general.getRegimeKey()).isEqualTo("01");
        assertThat(general.isExempt()).isFalse();
    }

    @Test
    void legacyConstructorGivesExemptCodesTheLeastCommittalCause() {
        TaxCode exempt = new TaxCode(COMPANY, "ES", "EXE", "Exento", BigDecimal.ZERO,
                VALID_FROM, null, true, true);

        assertThat(exempt.getOperationQualification()).isEqualTo(OperationQualification.EXEMPT);
        assertThat(exempt.getExemptionCause()).isEqualTo(ExemptionCause.OTHER);
        assertThat(exempt.isExempt()).isTrue();
    }

    // --- calificación explícita ---

    @Test
    void exportsCarryTheirOwnExemptionCause() {
        TaxCode export = taxCode(BigDecimal.ZERO, OperationQualification.EXEMPT, ExemptionCause.ARTICLE_21, "01");

        assertThat(export.getExemptionCause().code()).isEqualTo("E2");
        assertThat(export.isExempt()).isTrue();
    }

    @Test
    void reverseChargeIsSubjectAndNotExempt() {
        TaxCode reverseCharge = taxCode(BigDecimal.ZERO, OperationQualification.REVERSE_CHARGE, null, "01");

        assertThat(reverseCharge.getOperationQualification().code()).isEqualTo("S2");
        assertThat(reverseCharge.isExempt()).isFalse();
        assertThat(reverseCharge.getExemptionCause()).isNull();
    }

    @Test
    void operationsOutsideScopeAreNotExemptEither() {
        assertThat(taxCode(BigDecimal.ZERO, OperationQualification.NOT_SUBJECT, null, "01")
                .getOperationQualification().code()).isEqualTo("N1");
        assertThat(taxCode(BigDecimal.ZERO, OperationQualification.NOT_SUBJECT_LOCATION, null, "01")
                .getOperationQualification().code()).isEqualTo("N2");
    }

    // --- validaciones ---

    @Test
    void anExemptCodeMustSayWhy() {
        assertThatThrownBy(() -> taxCode(BigDecimal.ZERO, OperationQualification.EXEMPT, null, "01"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("causa de exención");
    }

    @Test
    void anExemptCodeCannotCarryAPercentage() {
        assertThatThrownBy(() -> taxCode(new BigDecimal("21"), OperationQualification.EXEMPT,
                ExemptionCause.OTHER, "01"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("porcentaje");
    }

    @Test
    void regimeKeyMustBeTwoDigits() {
        assertThatThrownBy(() -> taxCode(new BigDecimal("21"), OperationQualification.SUBJECT_NOT_EXEMPT, null, "1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("clave de régimen");
    }

    @Test
    void aCauseOnANonExemptCodeIsDiscardedInsteadOfContradicting() {
        TaxCode general = taxCode(new BigDecimal("21"), OperationQualification.SUBJECT_NOT_EXEMPT,
                ExemptionCause.ARTICLE_20, "01");

        assertThat(general.getExemptionCause()).isNull();
    }

    @Test
    void blankRegimeKeyFallsBackToTheGeneralRegime() {
        assertThat(taxCode(new BigDecimal("21"), OperationQualification.SUBJECT_NOT_EXEMPT, null, "  ")
                .getRegimeKey()).isEqualTo("01");
    }

    // --- actualización ---

    @Test
    void legacyUpdateKeepsTheQualificationAlreadyStored() {
        TaxCode export = taxCode(BigDecimal.ZERO, OperationQualification.EXEMPT, ExemptionCause.ARTICLE_21, "02");

        export.update("ES", "Exportaciones", BigDecimal.ZERO, VALID_FROM, null, true, true);

        assertThat(export.getExemptionCause()).isEqualTo(ExemptionCause.ARTICLE_21);
        assertThat(export.getRegimeKey()).isEqualTo("02");
    }

    @Test
    void turningACodeNonExemptClearsTheCause() {
        TaxCode export = taxCode(BigDecimal.ZERO, OperationQualification.EXEMPT, ExemptionCause.ARTICLE_21, "01");

        export.update("ES", "Ya no exento", new BigDecimal("21"), VALID_FROM, null, false, true);

        assertThat(export.isExempt()).isFalse();
        assertThat(export.getExemptionCause()).isNull();
        assertThat(export.getOperationQualification()).isEqualTo(OperationQualification.SUBJECT_NOT_EXEMPT);
    }

    // --- códigos remitidos a la AEAT ---

    @Test
    void qualificationCodesMatchTheSpecification() {
        assertThat(OperationQualification.SUBJECT_NOT_EXEMPT.code()).isEqualTo("S1");
        assertThat(OperationQualification.REVERSE_CHARGE.code()).isEqualTo("S2");
        assertThat(OperationQualification.NOT_SUBJECT.code()).isEqualTo("N1");
        assertThat(OperationQualification.NOT_SUBJECT_LOCATION.code()).isEqualTo("N2");
        assertThat(OperationQualification.EXEMPT.code())
                .as("una operación exenta no lleva CalificacionOperacion, lleva OperacionExenta")
                .isNull();
    }

    @Test
    void exemptionCauseCodesMatchTheSpecification() {
        assertThat(ExemptionCause.ARTICLE_20.code()).isEqualTo("E1");
        assertThat(ExemptionCause.ARTICLE_21.code()).isEqualTo("E2");
        assertThat(ExemptionCause.ARTICLE_22.code()).isEqualTo("E3");
        assertThat(ExemptionCause.ARTICLES_23_AND_24.code()).isEqualTo("E4");
        assertThat(ExemptionCause.ARTICLE_25.code()).isEqualTo("E5");
        assertThat(ExemptionCause.OTHER.code()).isEqualTo("E6");
    }
}
