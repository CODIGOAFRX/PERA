package com.peraerp.sales.accounting;

import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.CommercialDocumentRepository;
import com.peraerp.sales.document.DocumentStatus;
import com.peraerp.sales.document.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAccountingInvoiceControllerTest {
    @Test
    void requiresTheInternalKeyAndScopesTheQueryToTheRequestedCompany() {
        CommercialDocumentRepository documents = mock(CommercialDocumentRepository.class);
        InternalAccountingInvoiceController controller = new InternalAccountingInvoiceController(documents, "secret");
        UUID companyId = UUID.randomUUID();
        CommercialDocument invoice = new CommercialDocument(companyId, "FAC-1", DocumentType.INVOICE,
                UUID.randomUUID(), "C001", "Cliente", LocalDate.now(), null, "EUR", null, null, null);
        ReflectionTestUtils.setField(invoice, "id", UUID.randomUUID());
        when(documents.findAllByCompanyIdAndTypeInAndStatusInOrderByIssueDateDesc(companyId,
                List.of(DocumentType.INVOICE, DocumentType.RECTIFYING_INVOICE),
                List.of(DocumentStatus.DRAFT, DocumentStatus.CONFIRMED, DocumentStatus.CONVERTED,
                        DocumentStatus.CANCELLED))).thenReturn(List.of(invoice));

        assertThatThrownBy(() -> controller.findAll("wrong", companyId)).isInstanceOf(ResponseStatusException.class);
        assertThat(controller.findAll("secret", companyId)).singleElement()
                .satisfies(snapshot -> assertThat(snapshot.number()).isEqualTo("FAC-1"));
        verify(documents).findAllByCompanyIdAndTypeInAndStatusInOrderByIssueDateDesc(companyId,
                List.of(DocumentType.INVOICE, DocumentType.RECTIFYING_INVOICE),
                List.of(DocumentStatus.DRAFT, DocumentStatus.CONFIRMED, DocumentStatus.CONVERTED,
                        DocumentStatus.CANCELLED));
    }
}
