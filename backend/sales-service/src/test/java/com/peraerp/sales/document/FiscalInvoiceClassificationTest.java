package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.RectificationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas fiscales de la factura, previas a Veri*Factu: clasificación en F1..R5 e inmutabilidad de
 * la factura expedida.
 *
 * <p>Una factura confirmada está expedida. A partir de ahí no se toca: se corrige emitiendo una
 * rectificativa. Si estas pruebas se relajan, PERA puede alterar una factura cuyo registro ya se
 * remitió a la AEAT y romper la cadena de huellas.</p>
 */
class FiscalInvoiceClassificationTest {

    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 8, 19);

    private CommercialDocument document(DocumentType type) {
        return new CommercialDocument(UUID.randomUUID(), "F-2026-0000015", type, UUID.randomUUID(),
                "9435", "ALUMINIOS FAMA S.L.", ISSUE_DATE, null, "EUR", null, null, null);
    }

    // --- clasificación por defecto ---

    @Test
    void ordinaryInvoiceIsBornAsCompleteInvoice() {
        assertThat(document(DocumentType.INVOICE).getInvoiceKind()).isEqualTo(InvoiceKind.F1);
    }

    @Test
    void rectifyingInvoiceHasNoDefaultKindBecauseTheReasonIsADecision() {
        assertThat(document(DocumentType.RECTIFYING_INVOICE).getInvoiceKind()).isNull();
    }

    @Test
    void nonInvoiceDocumentsCarryNoFiscalClassification() {
        assertThat(document(DocumentType.DELIVERY_NOTE).getInvoiceKind()).isNull();
        assertThat(document(DocumentType.QUOTE).getInvoiceKind()).isNull();
    }

    @Test
    void rectifyingInvoiceIsCollectableLikeAnyOtherInvoice() {
        assertThat(document(DocumentType.RECTIFYING_INVOICE).getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(document(DocumentType.QUOTE).getPaymentStatus()).isEqualTo(PaymentStatus.NOT_APPLICABLE);
    }

    @Test
    void onlyInvoiceTypesAreConsideredInvoices() {
        assertThat(DocumentType.INVOICE.isInvoice()).isTrue();
        assertThat(DocumentType.RECTIFYING_INVOICE.isInvoice()).isTrue();
        assertThat(DocumentType.DELIVERY_NOTE.isInvoice()).isFalse();
        assertThat(DocumentType.QUOTE.isInvoice()).isFalse();
        assertThat(DocumentType.SALES_ORDER.isInvoice()).isFalse();
        assertThat(DocumentType.WORK_ORDER.isInvoice()).isFalse();
    }

    // --- inmutabilidad de la factura expedida ---

    @Test
    void confirmingAnInvoiceMeansIssuingIt() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        assertThat(invoice.isIssued()).isFalse();

        invoice.confirm();

        assertThat(invoice.isIssued()).isTrue();
    }

    @Test
    void issuedInvoiceRejectsRecalculation() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        invoice.confirm();

        assertThatThrownBy(() -> invoice.recalculate(new DocumentAmountsCalculator()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está expedida")
                .hasMessageContaining("rectificativa");
    }

    @Test
    void issuedInvoiceRejectsCurrencyChanges() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        invoice.confirm();

        assertThatThrownBy(() -> invoice.applyCurrencySnapshot("USD", BigDecimal.TEN, ISSUE_DATE, "MANUAL"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void issuedInvoiceRejectsReclassification() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        invoice.confirm();

        assertThatThrownBy(() -> invoice.classify(InvoiceKind.F2, null, null, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void issuedInvoiceStillAcceptsPaymentStatusChanges() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        invoice.confirm();

        invoice.updatePaymentStatus(PaymentStatus.PAID);

        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void quotesStayMutableAfterBeingConfirmed() {
        CommercialDocument quote = document(DocumentType.QUOTE);
        quote.confirm();

        quote.recalculate(new DocumentAmountsCalculator());

        assertThat(quote.isIssued()).isFalse();
    }

    // --- validación de rectificativas ---

    @Test
    void rectifyingInvoiceCannotBeIssuedWithoutAReason() {
        CommercialDocument rectifying = document(DocumentType.RECTIFYING_INVOICE);

        assertThatThrownBy(rectifying::confirm)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tipo fiscal");
    }

    @Test
    void rectifyingInvoiceRejectsANonRectifyingKind() {
        CommercialDocument rectifying = document(DocumentType.RECTIFYING_INVOICE);
        rectifying.classify(InvoiceKind.F1, RectificationType.SUBSTITUTION, UUID.randomUUID(), "F-1", ISSUE_DATE);

        assertThatThrownBy(rectifying::confirm)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("R1 a R5");
    }

    @Test
    void rectifyingInvoiceRequiresSubstitutionOrDifferences() {
        CommercialDocument rectifying = document(DocumentType.RECTIFYING_INVOICE);
        rectifying.classify(InvoiceKind.R1, null, UUID.randomUUID(), "F-1", ISSUE_DATE);

        assertThatThrownBy(rectifying::confirm)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sustitución");
    }

    @Test
    void rectifyingInvoiceRequiresTheRectifiedInvoice() {
        CommercialDocument rectifying = document(DocumentType.RECTIFYING_INVOICE);
        rectifying.classify(InvoiceKind.R1, RectificationType.DIFFERENCES, null, null, null);

        assertThatThrownBy(rectifying::confirm)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("referenciar");
    }

    @Test
    void fullyClassifiedRectifyingInvoiceIsIssuedAndFreezesTheRectifiedInvoice() {
        CommercialDocument rectifying = document(DocumentType.RECTIFYING_INVOICE);
        UUID rectifiedId = UUID.randomUUID();
        rectifying.classify(InvoiceKind.R4, RectificationType.SUBSTITUTION, rectifiedId,
                "F-2026-0000015", ISSUE_DATE);

        rectifying.confirm();

        assertThat(rectifying.isIssued()).isTrue();
        assertThat(rectifying.getInvoiceKind()).isEqualTo(InvoiceKind.R4);
        assertThat(rectifying.getRectificationType()).isEqualTo(RectificationType.SUBSTITUTION);
        assertThat(rectifying.getRectifiedDocumentId()).isEqualTo(rectifiedId);
        assertThat(rectifying.getRectifiedNumberSnapshot()).isEqualTo("F-2026-0000015");
        assertThat(rectifying.getRectifiedIssueDateSnapshot()).isEqualTo(ISSUE_DATE);
    }

    // --- validación de facturas ordinarias ---

    @Test
    void ordinaryInvoiceRejectsARectifyingKind() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        invoice.classify(InvoiceKind.R1, null, null, null, null);

        assertThatThrownBy(invoice::confirm)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no puede usar el tipo rectificativo");
    }

    @Test
    void ordinaryInvoiceCannotReferenceARectifiedInvoice() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        invoice.classify(InvoiceKind.F1, null, UUID.randomUUID(), "F-1", ISSUE_DATE);

        assertThatThrownBy(invoice::confirm)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Solo una factura rectificativa");
    }

    @Test
    void simplifiedInvoiceIsAValidOrdinaryClassification() {
        CommercialDocument invoice = document(DocumentType.INVOICE);
        invoice.classify(InvoiceKind.F2, null, null, null, null);

        invoice.confirm();

        assertThat(invoice.getInvoiceKind()).isEqualTo(InvoiceKind.F2);
    }

    @Test
    void nonInvoiceDocumentsRejectClassification() {
        CommercialDocument deliveryNote = document(DocumentType.DELIVERY_NOTE);

        assertThatThrownBy(() -> deliveryNote.classify(InvoiceKind.F1, null, null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Solo las facturas");
    }
}
