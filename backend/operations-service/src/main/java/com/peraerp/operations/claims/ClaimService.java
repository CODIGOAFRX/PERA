package com.peraerp.operations.claims;

import com.peraerp.operations.claims.ClaimDtos.CatalogItemRequest;
import com.peraerp.operations.claims.ClaimDtos.CatalogItemResponse;
import com.peraerp.operations.claims.ClaimDtos.ClaimLineRequest;
import com.peraerp.operations.claims.ClaimDtos.ClaimLineResponse;
import com.peraerp.operations.claims.ClaimDtos.ClaimRequest;
import com.peraerp.operations.claims.ClaimDtos.ClaimResponse;
import com.peraerp.operations.claims.ClaimDtos.CloseClaimRequest;
import com.peraerp.operations.claims.ClaimDtos.CommentResponse;
import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.config.CurrentUserProvider;
import com.peraerp.operations.config.CurrentUserProvider.CurrentUser;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ClaimService {

    private final ClaimRepository repository;
    private final ClaimLineRepository lineRepository;
    private final ClaimCommentRepository commentRepository;
    private final ClaimSequenceRepository sequenceRepository;
    private final ClaimCatalogItemRepository catalogRepository;
    private final CurrentCompanyProvider companyProvider;
    private final CurrentUserProvider userProvider;

    public ClaimService(ClaimRepository repository, ClaimLineRepository lineRepository,
                        ClaimCommentRepository commentRepository, ClaimSequenceRepository sequenceRepository,
                        ClaimCatalogItemRepository catalogRepository, CurrentCompanyProvider companyProvider,
                        CurrentUserProvider userProvider) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.commentRepository = commentRepository;
        this.sequenceRepository = sequenceRepository;
        this.catalogRepository = catalogRepository;
        this.companyProvider = companyProvider;
        this.userProvider = userProvider;
    }

    // --- Tablas de clasificación

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> catalog() {
        return catalogRepository.findAllByCompanyIdOrderByKindAscNameAsc(companyProvider.requireCompanyId()).stream()
                .map(CatalogItemResponse::from).toList();
    }

    @Transactional
    public CatalogItemResponse createCatalogItem(CatalogItemRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String name = request.name().trim();
        if (catalogRepository.existsByCompanyIdAndKindAndNameIgnoreCase(companyId, request.kind(), name)) {
            throw new BusinessRuleException("Ya existe «" + name + "» en esa tabla.");
        }
        ClaimCatalogItem item = new ClaimCatalogItem(companyId, request.kind());
        applyCatalog(item, request);
        return CatalogItemResponse.from(catalogRepository.save(item));
    }

    @Transactional
    public CatalogItemResponse updateCatalogItem(UUID id, CatalogItemRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        ClaimCatalogItem item = catalogRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Elemento de clasificación", id));
        if (item.getKind() != request.kind()) {
            throw new BusinessRuleException("No se puede cambiar la tabla de un elemento.");
        }
        String name = request.name().trim();
        if (catalogRepository.existsByCompanyIdAndKindAndNameIgnoreCaseAndIdNot(companyId, item.getKind(), name, id)) {
            throw new BusinessRuleException("Ya existe «" + name + "» en esa tabla.");
        }
        applyCatalog(item, request);
        return CatalogItemResponse.from(item);
    }

    private void applyCatalog(ClaimCatalogItem item, CatalogItemRequest request) {
        try {
            item.update(request.name().trim(), request.followUpDays(), request.active() == null || request.active());
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
    }

    // --- Reclamaciones

    @Transactional
    public ClaimResponse create(ClaimRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        CurrentUser user = userProvider.requireUser();
        Claim claim = new Claim(companyId, nextNumber(companyId, request.claimDate().getYear()), user.id(), user.name());
        apply(claim, request, null);
        repository.saveAndFlush(claim);
        List<ClaimLine> lines = saveLines(claim, request.lines());
        return response(claim, lines, List.of());
    }

    @Transactional
    public ClaimResponse update(UUID id, ClaimRequest request) {
        Claim claim = require(id);
        try {
            claim.requireOpen();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        apply(claim, request, claim);
        lineRepository.deleteAllByCompanyIdAndClaimId(claim.getCompanyId(), claim.getId());
        lineRepository.flush();
        List<ClaimLine> lines = saveLines(claim, request.lines());
        return response(claim, lines, comments(claim));
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> search(ClaimStatus status, UUID customerId, UUID reasonId, LocalDate fromDate,
                                      LocalDate toDate, boolean overdue, String query, Pageable pageable) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BusinessRuleException("El final del intervalo de búsqueda no puede ser anterior al inicio.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalize(query);
        return repository.search(companyId, status != null, status, customerId != null, customerId,
                        reasonId != null, reasonId, fromDate != null, fromDate, toDate != null, toDate, overdue,
                        LocalDate.now(), normalizedQuery != null, normalizedQuery == null ? "" : normalizedQuery,
                        pageable)
                .map(claim -> response(claim, lines(claim), comments(claim)));
    }

    @Transactional(readOnly = true)
    public ClaimResponse findById(UUID id) {
        Claim claim = require(id);
        return response(claim, lines(claim), comments(claim));
    }

    @Transactional
    public ClaimResponse close(UUID id, CloseClaimRequest request) {
        return change(id, claim -> claim.close(request.closedOn(), normalize(request.closingNote())));
    }

    @Transactional
    public ClaimResponse reopen(UUID id) {
        return change(id, Claim::reopen);
    }

    @Transactional
    public ClaimResponse addComment(UUID id, String text) {
        Claim claim = require(id);
        CurrentUser user = userProvider.requireUser();
        commentRepository.saveAndFlush(new ClaimComment(claim.getCompanyId(), claim.getId(), user.id(), user.name(),
                text.trim()));
        return response(claim, lines(claim), comments(claim));
    }

    /** Solo se elimina una reclamación abierta y sin seguimiento: una vez hay historia, se cierra. */
    @Transactional
    public void delete(UUID id) {
        Claim claim = require(id);
        if (claim.getStatus() != ClaimStatus.OPEN || commentRepository.existsByCompanyIdAndClaimId(
                claim.getCompanyId(), claim.getId())) {
            throw new BusinessRuleException("La reclamación ya tiene seguimiento. Ciérrala en lugar de eliminarla.");
        }
        lineRepository.deleteAllByCompanyIdAndClaimId(claim.getCompanyId(), claim.getId());
        lineRepository.flush();
        repository.delete(claim);
    }

    private ClaimResponse change(UUID id, Consumer<Claim> operation) {
        Claim claim = require(id);
        try {
            operation.accept(claim);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return response(claim, lines(claim), comments(claim));
    }

    /**
     * Comprueba que cada clasificación pertenece a la empresa y a su tabla. Un elemento desactivado
     * se admite solo si la reclamación ya lo tenía: desactivarlo no obliga a reclasificar el histórico.
     */
    private void apply(Claim claim, ClaimRequest request, Claim existing) {
        if (request.sourceDocumentId() != null && normalize(request.sourceDocumentNumber()) == null) {
            throw new BusinessRuleException("Indica el número del documento de origen.");
        }
        if (request.sourceDocumentDate() != null && request.sourceDocumentDate().isAfter(request.claimDate())) {
            throw new BusinessRuleException("El documento de origen no puede ser posterior a la reclamación.");
        }
        Map<ClaimCatalogKind, UUID> wanted = new EnumMap<>(ClaimCatalogKind.class);
        put(wanted, ClaimCatalogKind.REASON, request.reasonId());
        put(wanted, ClaimCatalogKind.NONCONFORMITY, request.nonconformityId());
        put(wanted, ClaimCatalogKind.CAUSE, request.causeId());
        put(wanted, ClaimCatalogKind.AREA, request.areaId());
        put(wanted, ClaimCatalogKind.RESPONSIBLE, request.responsibleId());
        put(wanted, ClaimCatalogKind.RESOLUTION, request.resolutionId());
        put(wanted, ClaimCatalogKind.PREVENTIVE_ACTION, request.preventiveActionId());
        Map<UUID, ClaimCatalogItem> items = new java.util.HashMap<>();
        if (!wanted.isEmpty()) {
            catalogRepository.findAllByCompanyIdAndIdIn(claim.getCompanyId(), wanted.values())
                    .forEach(item -> items.put(item.getId(), item));
        }
        for (Map.Entry<ClaimCatalogKind, UUID> entry : wanted.entrySet()) {
            ClaimCatalogItem item = items.get(entry.getValue());
            if (item == null || item.getKind() != entry.getKey()) {
                throw new BusinessRuleException("Una de las clasificaciones no existe o no corresponde a su tabla.");
            }
            boolean unchanged = existing != null && entry.getValue().equals(existing.classification(entry.getKey()));
            if (!item.isActive() && !unchanged) {
                throw new BusinessRuleException("«" + item.getName() + "» está desactivado.");
            }
        }
        LocalDate followUpDate = request.followUpDate();
        UUID actionId = wanted.get(ClaimCatalogKind.PREVENTIVE_ACTION);
        if (followUpDate == null && actionId != null && items.get(actionId).getFollowUpDays() != null) {
            followUpDate = request.claimDate().plusDays(items.get(actionId).getFollowUpDays());
        }
        if (followUpDate != null && followUpDate.isBefore(request.claimDate())) {
            throw new BusinessRuleException("El seguimiento no puede ser anterior a la reclamación.");
        }
        claim.update(request.claimDate(), request.customerId(), normalize(request.customerCode()),
                request.customerName().trim(), request.sourceDocumentId(), normalize(request.sourceDocumentNumber()),
                request.sourceDocumentDate(), request.description().trim(), wanted, followUpDate);
    }

    private static void put(Map<ClaimCatalogKind, UUID> map, ClaimCatalogKind kind, UUID id) {
        if (id != null) {
            map.put(kind, id);
        }
    }

    private List<ClaimLine> saveLines(Claim claim, List<ClaimLineRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<ClaimLine> lines = new ArrayList<>();
        int sequence = 1;
        for (ClaimLineRequest request : requests) {
            if (request.productId() != null && normalize(request.productCode()) == null) {
                throw new BusinessRuleException("Las líneas con producto deben incluir su código.");
            }
            lines.add(new ClaimLine(claim.getCompanyId(), claim.getId(), sequence++, request.productId(),
                    request.productId() == null ? null : request.productCode().trim(), request.description().trim(),
                    request.quantity()));
        }
        return lineRepository.saveAll(lines);
    }

    private String nextNumber(UUID companyId, int year) {
        ClaimSequence sequence = sequenceRepository.findForUpdate(companyId, year)
                .orElseGet(() -> sequenceRepository.save(new ClaimSequence(companyId, year)));
        return "RCL-%d-%06d".formatted(year, sequence.next());
    }

    private Claim require(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Reclamación", id));
    }

    private List<ClaimLine> lines(Claim claim) {
        return lineRepository.findAllByCompanyIdAndClaimIdOrderByLineSequenceAsc(claim.getCompanyId(), claim.getId());
    }

    private List<ClaimComment> comments(Claim claim) {
        return commentRepository.findAllByCompanyIdAndClaimIdOrderByCreatedAtAsc(claim.getCompanyId(), claim.getId());
    }

    private ClaimResponse response(Claim claim, List<ClaimLine> lines, List<ClaimComment> claimComments) {
        LocalDate today = LocalDate.now();
        LocalDate end = claim.getClosedOn() == null ? today : claim.getClosedOn();
        boolean overdue = claim.getStatus() == ClaimStatus.OPEN && claim.getFollowUpDate() != null
                && claim.getFollowUpDate().isBefore(today);
        return new ClaimResponse(claim.getId(), claim.getNumber(), claim.getClaimDate(), claim.getStatus(),
                claim.getCustomerId(), claim.getCustomerCodeSnapshot(), claim.getCustomerNameSnapshot(),
                claim.getSourceDocumentId(), claim.getSourceDocumentNumber(), claim.getSourceDocumentDate(),
                claim.getDescription(), claim.getReportedByName(), claim.classification(ClaimCatalogKind.REASON),
                claim.classification(ClaimCatalogKind.NONCONFORMITY), claim.classification(ClaimCatalogKind.CAUSE),
                claim.classification(ClaimCatalogKind.AREA), claim.classification(ClaimCatalogKind.RESPONSIBLE),
                claim.classification(ClaimCatalogKind.RESOLUTION),
                claim.classification(ClaimCatalogKind.PREVENTIVE_ACTION), claim.getFollowUpDate(), overdue,
                claim.getClosedOn(), claim.getClosingNote(),
                Math.max(0, ChronoUnit.DAYS.between(claim.getClaimDate(), end)),
                lines.stream().map(ClaimLineResponse::from).toList(),
                claimComments.stream().map(CommentResponse::from).toList());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
