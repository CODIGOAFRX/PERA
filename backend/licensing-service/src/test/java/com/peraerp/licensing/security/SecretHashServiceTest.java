package com.peraerp.licensing.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecretHashServiceTest {
    private final SecretHashService service = new SecretHashService(
            "test-pepper-that-is-longer-than-thirty-two-bytes", new SecureRandom());

    @Test
    void generatesUniqueHighEntropySecrets() {
        Set<String> activationCodes = new HashSet<>();
        Set<String> installationTokens = new HashSet<>();

        for (int index = 0; index < 200; index++) {
            activationCodes.add(service.generateActivationCode());
            installationTokens.add(service.generateInstallationToken());
        }

        assertThat(activationCodes).hasSize(200).allMatch(value -> value.startsWith("PERA-"));
        assertThat(installationTokens).hasSize(200).allMatch(value -> value.startsWith("perat_"));
    }

    @Test
    void storesIrreversibleDomainSeparatedHashesAndComparesThemSafely() {
        String secret = service.generateActivationCode();
        byte[] activationHash = service.hashActivationCode(secret);
        byte[] tokenHash = service.hashInstallationToken(secret);

        assertThat(activationHash).hasSize(32).isNotEqualTo(secret.getBytes(StandardCharsets.UTF_8));
        assertThat(tokenHash).hasSize(32).isNotEqualTo(activationHash);
        assertThat(service.activationCodeMatches(secret, activationHash)).isTrue();
        assertThat(service.activationCodeMatches(secret + "x", activationHash)).isFalse();
        assertThat(service.installationTokenMatches(secret, tokenHash)).isTrue();
    }
}
