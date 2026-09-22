package com.peraerp.platform.domain;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ContactDetailsTest {
    @Test void normalizesOptionalBankAndPostalData() {
        var d=new ContactDetails(" Calle Mayor 1 ","Madrid","Madrid","28001","es","es91 2100 0418 4502 0005 1332"," Demo ");
        assertThat(d.iban()).isEqualTo("ES9121000418450200051332");
        assertThat(d.postalAddress()).isEqualTo("Calle Mayor 1, 28001, Madrid, Madrid, ES");
        assertThat(d.bankAccountHolder()).isEqualTo("Demo");
        assertThat(ContactDetails.normalizeIban("  ")).isNull();
    }
    @Test void rejectsInvalidChecksumAndSpanishLength() {
        assertThatThrownBy(()->ContactDetails.normalizeIban("ES9021000418450200051332")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->ContactDetails.normalizeIban("ES912100041845020005133")).isInstanceOf(BusinessRuleException.class);
    }
    @Test void acceptsInternationalIban() {
        assertThat(ContactDetails.normalizeIban("GB82 WEST 1234 5698 7654 32")).isEqualTo("GB82WEST12345698765432");
    }
}
