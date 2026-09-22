package com.peraerp.sales.mail;
import org.junit.jupiter.api.Test;
import java.util.*;
import com.peraerp.platform.domain.BusinessRuleException;
import static org.assertj.core.api.Assertions.*;
class MailSecretCipherTest {
    @Test void encryptsWithRandomNonceAndBindsCredentialToCompany() {
        var cipher=new MailSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        UUID company=UUID.randomUUID();
        String a=cipher.encrypt(company,"test-secret"),b=cipher.encrypt(company,"test-secret");
        assertThat(a).doesNotContain("test-secret").isNotEqualTo(b);
        assertThat(cipher.decrypt(company,a)).isEqualTo("test-secret");
        assertThatThrownBy(()->cipher.decrypt(UUID.randomUUID(),a)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->cipher.decrypt(company,a.substring(1))).isInstanceOf(BusinessRuleException.class);
    }
    @Test void refusesToStoreSecretsWithoutServerKey() {
        var cipher=new MailSecretCipher("");
        assertThat(cipher.ready()).isFalse();
        assertThatThrownBy(()->cipher.encrypt(UUID.randomUUID(),"secret")).isInstanceOf(BusinessRuleException.class);
    }
}
