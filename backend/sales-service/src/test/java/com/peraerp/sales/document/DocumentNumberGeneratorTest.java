package com.peraerp.sales.document;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentNumberGeneratorTest {
    @Test
    void incrementsSequenceAndUsesBusinessPrefix() {
        UUID companyId = UUID.randomUUID();
        DocumentSequenceRepository repository = mock(DocumentSequenceRepository.class);
        AtomicReference<DocumentSequence> stored = new AtomicReference<>();
        when(repository.findByCompanyIdAndTypeAndYear(companyId, DocumentType.INVOICE, 2026))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(DocumentSequence.class))).thenAnswer(invocation -> {
            DocumentSequence sequence = invocation.getArgument(0);
            stored.set(sequence);
            return sequence;
        });
        DocumentNumberGenerator generator = new DocumentNumberGenerator(repository);

        assertThat(generator.next(companyId, DocumentType.INVOICE, 2026)).isEqualTo("FAC-2026-000001");
        assertThat(generator.next(companyId, DocumentType.INVOICE, 2026)).isEqualTo("FAC-2026-000002");
    }
}
