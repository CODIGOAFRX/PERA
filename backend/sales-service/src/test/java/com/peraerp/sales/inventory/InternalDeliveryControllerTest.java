package com.peraerp.sales.inventory;

import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.CommercialDocumentRepository;
import com.peraerp.sales.document.DocumentLine;
import com.peraerp.sales.document.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalDeliveryControllerTest {

    @Test
    void requiresTheInternalKeyAndReturnsOnlyTheProductLinesOfTheRequestedCompany() {
        CommercialDocumentRepository documents = mock(CommercialDocumentRepository.class);
        InternalDeliveryController controller = new InternalDeliveryController(documents, "secret");
        UUID companyId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant since = Instant.parse("2026-10-01T10:00:00Z");
        CommercialDocument note = new CommercialDocument(companyId, "ALB-1", DocumentType.DELIVERY_NOTE,
                UUID.randomUUID(), "C001", "Cliente", LocalDate.of(2026, 10, 1), null, "EUR", null, null, null);
        ReflectionTestUtils.setField(note, "id", UUID.randomUUID());
        note.addLine(new DocumentLine(productId, "P-1", "Tornillo", new BigDecimal("3"), BigDecimal.ONE,
                BigDecimal.ZERO, new BigDecimal("21")));
        note.addLine(new DocumentLine(null, null, "Portes", BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO, new BigDecimal("21")));
        when(documents.findDeliveriesUpdatedSince(companyId, since,
                PageRequest.of(0, InternalDeliveryController.PAGE_SIZE))).thenReturn(List.of(note));

        assertThatThrownBy(() -> controller.findUpdatedSince("wrong", companyId, since))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.findUpdatedSince(null, companyId, since))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(documents);

        assertThat(controller.findUpdatedSince("secret", companyId, since)).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.number()).isEqualTo("ALB-1");
            assertThat(snapshot.type()).isEqualTo("DELIVERY_NOTE");
            assertThat(snapshot.lines()).singleElement().satisfies(line -> {
                assertThat(line.productId()).isEqualTo(productId);
                assertThat(line.productCode()).isEqualTo("P-1");
                assertThat(line.quantity()).isEqualByComparingTo("3");
            });
        });
    }
}
