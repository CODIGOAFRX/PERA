package com.peraerp.operations.claims;

import com.peraerp.operations.claims.ClaimDtos.CatalogItemRequest;
import com.peraerp.operations.claims.ClaimDtos.CatalogItemResponse;
import com.peraerp.operations.claims.ClaimDtos.ClaimLineRequest;
import com.peraerp.operations.claims.ClaimDtos.ClaimRequest;
import com.peraerp.operations.claims.ClaimDtos.ClaimResponse;
import com.peraerp.operations.claims.ClaimDtos.CloseClaimRequest;
import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.config.CurrentUserProvider;
import com.peraerp.operations.config.CurrentUserProvider.CurrentUser;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Mock ClaimRepository repository;
    @Mock ClaimLineRepository lineRepository;
    @Mock ClaimCommentRepository commentRepository;
    @Mock ClaimSequenceRepository sequenceRepository;
    @Mock ClaimCatalogItemRepository catalogRepository;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock CurrentUserProvider userProvider;

    private final Map<UUID, Claim> claims = new HashMap<>();
    private final Map<UUID, ClaimCatalogItem> catalog = new HashMap<>();
    private final List<ClaimComment> comments = new ArrayList<>();
    private ClaimService service;
    private UUID companyId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        service = new ClaimService(repository, lineRepository, commentRepository, sequenceRepository,
                catalogRepository, companyProvider, userProvider);
        companyId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(userProvider.requireUser()).thenReturn(new CurrentUser(UUID.randomUUID(), "Ana Calidad"));
        ClaimSequence sequence = new ClaimSequence(companyId, 2026);
        when(sequenceRepository.findForUpdate(eq(companyId), anyInt())).thenReturn(Optional.of(sequence));

        // Repositorios en memoria.
        when(catalogRepository.save(any(ClaimCatalogItem.class))).thenAnswer(invocation -> {
            ClaimCatalogItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
            catalog.put(item.getId(), item);
            return item;
        });
        when(catalogRepository.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(catalog.get(invocation.<UUID>getArgument(0))));
        when(catalogRepository.findAllByCompanyIdAndIdIn(eq(companyId), any())).thenAnswer(invocation ->
                invocation.<Collection<UUID>>getArgument(1).stream().map(catalog::get)
                        .filter(java.util.Objects::nonNull).toList());
        when(catalogRepository.existsByCompanyIdAndKindAndNameIgnoreCase(eq(companyId), any(), any()))
                .thenAnswer(invocation -> catalog.values().stream().anyMatch(item ->
                        item.getKind() == invocation.getArgument(1)
                                && item.getName().equalsIgnoreCase(invocation.getArgument(2))));
        when(repository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
            Claim claim = invocation.getArgument(0);
            ReflectionTestUtils.setField(claim, "id", UUID.randomUUID());
            claims.put(claim.getId(), claim);
            return claim;
        });
        when(repository.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(claims.get(invocation.<UUID>getArgument(0))));
        when(lineRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.saveAndFlush(any(ClaimComment.class))).thenAnswer(invocation -> {
            ClaimComment comment = invocation.getArgument(0);
            ReflectionTestUtils.setField(comment, "createdAt", Instant.now());
            comments.add(comment);
            return comment;
        });
        when(commentRepository.findAllByCompanyIdAndClaimIdOrderByCreatedAtAsc(eq(companyId), any()))
                .thenAnswer(invocation -> comments.stream()
                        .filter(comment -> comment.getClaimId().equals(invocation.getArgument(1))).toList());
        when(commentRepository.existsByCompanyIdAndClaimId(eq(companyId), any())).thenAnswer(invocation ->
                comments.stream().anyMatch(comment -> comment.getClaimId().equals(invocation.getArgument(1))));
    }

    @Test
    void numbersTheClaimAndRecordsWhoReportedIt() {
        ClaimResponse first = service.create(request(Map.of()));
        ClaimResponse second = service.create(request(Map.of()));

        assertThat(first.number()).isEqualTo("RCL-2026-000001");
        assertThat(second.number()).isEqualTo("RCL-2026-000002");
        assertThat(first.status()).isEqualTo(ClaimStatus.OPEN);
        assertThat(first.reportedByName()).isEqualTo("Ana Calidad");
        assertThat(first.customerName()).isEqualTo("Cliente Demo");
        assertThat(first.sourceDocumentNumber()).isEqualTo("ALB-2026-000004");
        assertThat(first.lines()).singleElement().satisfies(line -> {
            assertThat(line.productCode()).isEqualTo("P-1");
            assertThat(line.quantity()).isEqualByComparingTo("2");
        });
    }

    @Test
    void thePreventiveActionSetsTheFollowUpDateFromItsDays() {
        UUID action = item(ClaimCatalogKind.PREVENTIVE_ACTION, "Revisar embalaje", 30);

        ClaimResponse claim = service.create(request(Map.of(ClaimCatalogKind.PREVENTIVE_ACTION, action)));

        assertThat(claim.preventiveActionId()).isEqualTo(action);
        assertThat(claim.followUpDate()).isEqualTo(DAY.plusDays(30));
    }

    @Test
    void eachClassificationMustBelongToItsTable() {
        UUID cause = item(ClaimCatalogKind.CAUSE, "Transporte", null);

        assertThatThrownBy(() -> service.create(request(Map.of(ClaimCatalogKind.REASON, cause))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no corresponde");
        assertThatThrownBy(() -> service.create(request(Map.of(ClaimCatalogKind.REASON, UUID.randomUUID()))))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aDeactivatedItemCannotBeChosenButStaysOnClaimsThatAlreadyHadIt() {
        UUID reason = item(ClaimCatalogKind.REASON, "Rotura", null);
        ClaimResponse claim = service.create(request(Map.of(ClaimCatalogKind.REASON, reason)));
        catalog.get(reason).update("Rotura", null, false);

        assertThat(service.update(claim.id(), request(Map.of(ClaimCatalogKind.REASON, reason))).reasonId())
                .isEqualTo(reason);
        assertThatThrownBy(() -> service.create(request(Map.of(ClaimCatalogKind.REASON, reason))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("desactivado");
    }

    @Test
    void aClaimClosesOnlyWithAResolutionAndCanBeReopened() {
        ClaimResponse claim = service.create(request(Map.of()));
        assertThatThrownBy(() -> service.close(claim.id(), new CloseClaimRequest(DAY.plusDays(3), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("resolución");

        UUID resolution = item(ClaimCatalogKind.RESOLUTION, "Reposición del material", null);
        service.update(claim.id(), request(Map.of(ClaimCatalogKind.RESOLUTION, resolution)));
        assertThatThrownBy(() -> service.close(claim.id(), new CloseClaimRequest(DAY.minusDays(1), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("anterior");

        ClaimResponse closed = service.close(claim.id(), new CloseClaimRequest(DAY.plusDays(3), " Repuesto "));
        assertThat(closed.status()).isEqualTo(ClaimStatus.CLOSED);
        assertThat(closed.closingNote()).isEqualTo("Repuesto");
        assertThat(closed.daysOpen()).isEqualTo(3);
        assertThatThrownBy(() -> service.update(claim.id(), request(Map.of()))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cerrada");

        ClaimResponse reopened = service.reopen(claim.id());
        assertThat(reopened.status()).isEqualTo(ClaimStatus.OPEN);
        assertThat(reopened.closedOn()).isNull();
    }

    @Test
    void commentsKeepTheAuthorAndAClaimWithFollowUpCannotBeDeleted() {
        ClaimResponse claim = service.create(request(Map.of()));

        ClaimResponse commented = service.addComment(claim.id(), "  Llamado el cliente, se recoge el lunes.  ");

        assertThat(commented.comments()).singleElement().satisfies(comment -> {
            assertThat(comment.authorName()).isEqualTo("Ana Calidad");
            assertThat(comment.text()).isEqualTo("Llamado el cliente, se recoge el lunes.");
        });
        assertThatThrownBy(() -> service.delete(claim.id())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ciérrala");
        verify(repository, never()).delete(any());
    }

    @Test
    void catalogNamesAreUniquePerTableAndOnlyPreventiveActionsHaveDays() {
        CatalogItemResponse created = service.createCatalogItem(
                new CatalogItemRequest(ClaimCatalogKind.AREA, " Producción ", null, null));
        assertThat(created.name()).isEqualTo("Producción");
        assertThat(created.active()).isTrue();

        assertThatThrownBy(() -> service.createCatalogItem(
                new CatalogItemRequest(ClaimCatalogKind.AREA, "producción", null, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(service.createCatalogItem(new CatalogItemRequest(ClaimCatalogKind.CAUSE, "Producción", null, null))
                .kind()).isEqualTo(ClaimCatalogKind.CAUSE);
        assertThatThrownBy(() -> service.createCatalogItem(
                new CatalogItemRequest(ClaimCatalogKind.REASON, "Retraso", 15, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("acciones preventivas");
    }

    @Test
    void validatesTheSourceDocumentAndTheFollowUpDate() {
        ClaimRequest withoutNumber = new ClaimRequest(DAY, customerId, "C001", "Cliente Demo", UUID.randomUUID(), " ",
                null, "Falta material", null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(withoutNumber)).isInstanceOf(BusinessRuleException.class);

        ClaimRequest futureDocument = new ClaimRequest(DAY, customerId, "C001", "Cliente Demo", UUID.randomUUID(),
                "ALB-1", DAY.plusDays(1), "Falta material", null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(futureDocument)).isInstanceOf(BusinessRuleException.class);

        ClaimRequest earlyFollowUp = new ClaimRequest(DAY, customerId, "C001", "Cliente Demo", null, null, null,
                "Falta material", null, null, null, null, null, null, null, DAY.minusDays(1), null);
        assertThatThrownBy(() -> service.create(earlyFollowUp)).isInstanceOf(BusinessRuleException.class);
    }

    private ClaimRequest request(Map<ClaimCatalogKind, UUID> classification) {
        return new ClaimRequest(DAY, customerId, "C001", " Cliente Demo ", UUID.randomUUID(), "ALB-2026-000004",
                DAY.minusDays(5), "Llegaron dos piezas rotas", classification.get(ClaimCatalogKind.REASON),
                classification.get(ClaimCatalogKind.NONCONFORMITY), classification.get(ClaimCatalogKind.CAUSE),
                classification.get(ClaimCatalogKind.AREA), classification.get(ClaimCatalogKind.RESPONSIBLE),
                classification.get(ClaimCatalogKind.RESOLUTION),
                classification.get(ClaimCatalogKind.PREVENTIVE_ACTION), null,
                List.of(new ClaimLineRequest(UUID.randomUUID(), "P-1", "Producto", new BigDecimal("2"))));
    }

    private UUID item(ClaimCatalogKind kind, String name, Integer days) {
        ClaimCatalogItem item = new ClaimCatalogItem(companyId, kind);
        item.update(name, days, true);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        catalog.put(item.getId(), item);
        return item.getId();
    }
}
