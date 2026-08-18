package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TariffService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final PriceListRepository tariffRepository;
    private final PriceListItemRepository itemRepository;
    private final PricingRuleRepository ruleRepository;
    private final PricingReferenceService references;
    private final CurrentCompanyProvider companyProvider;

    public TariffService(PriceListRepository tariffRepository, PriceListItemRepository itemRepository,
                         PricingRuleRepository ruleRepository, PricingReferenceService references,
                         CurrentCompanyProvider companyProvider) {
        this.tariffRepository = tariffRepository;
        this.itemRepository = itemRepository;
        this.ruleRepository = ruleRepository;
        this.references = references;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public TariffResponse createTariff(TariffRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        validateTariffRequest(request);
        references.validateTariffTarget(companyId, request);
        String code = normalizeCode(request.code());
        String currency = normalizeCurrency(request.currency());
        if (tariffRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe una tarifa con el código " + code + ".");
        }
        validateParent(companyId, null, request.parentTariffId(), currency);
        validateTariffAmbiguity(companyId, null, request);
        PriceList tariff = new PriceList(companyId, code, request.name().trim(), currency, request.validFrom(),
                request.validUntil(), request.active(), request.priority(), request.scope(), request.customerId(),
                request.productNatureId(), request.productSupertypeId(), request.productTypeId(),
                request.productGroupId(), request.productId(), request.parentTariffId(),
                request.generalSurchargePercentage(), request.energySurchargePercentage(),
                request.minimumBillingAmount(), request.unitMultiple(), request.minimumPerPiece());
        return TariffResponse.from(tariffRepository.save(tariff));
    }

    @Transactional(readOnly = true)
    public TariffResponse findTariff(UUID id) {
        return TariffResponse.from(requireTariff(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<TariffResponse> searchTariffs(String query, UUID customerId, UUID natureId, UUID supertypeId,
                                              UUID typeId, PricingScope scope, Boolean active, LocalDate validOn,
                                              Pageable pageable) {
        return tariffRepository.search(companyProvider.requireCompanyId(), normalizeQuery(query), customerId,
                natureId, supertypeId, typeId, scope, active, validOn, pageable).map(TariffResponse::from);
    }

    @Transactional
    public TariffResponse updateTariff(UUID id, TariffRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        PriceList tariff = requireTariff(id, companyId);
        validateTariffRequest(request);
        String requestedCode = normalizeCode(request.code());
        if (!tariff.getCode().equalsIgnoreCase(requestedCode)) {
            throw new BusinessRuleException("El código de la tarifa no se puede modificar.");
        }
        String currency = normalizeCurrency(request.currency());
        if (!tariff.getCurrency().equals(currency)
                && !tariffRepository.findAllByCompanyIdAndParentPriceListId(companyId, id).isEmpty()) {
            throw new BusinessRuleException("No se puede cambiar la moneda de una tarifa que tiene herederas.");
        }
        references.validateTariffTarget(companyId, request);
        validateParent(companyId, id, request.parentTariffId(), currency);
        validateTariffAmbiguity(companyId, id, request);
        tariff.update(request.name().trim(), currency, request.validFrom(), request.validUntil(), request.active(),
                request.priority(), request.scope(), request.customerId(), request.productNatureId(),
                request.productSupertypeId(), request.productTypeId(), request.productGroupId(), request.productId(),
                request.parentTariffId(), request.generalSurchargePercentage(),
                request.energySurchargePercentage(), request.minimumBillingAmount(), request.unitMultiple(),
                request.minimumPerPiece());
        return TariffResponse.from(tariff);
    }

    @Transactional(readOnly = true)
    public List<TariffItemResponse> listItems(UUID tariffId) {
        UUID companyId = companyProvider.requireCompanyId();
        requireTariff(tariffId, companyId);
        return itemRepository.findAllByCompanyIdAndPriceListId(companyId, tariffId).stream()
                .map(TariffItemResponse::from).toList();
    }

    @Transactional
    public TariffItemResponse createItem(UUID tariffId, TariffItemRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        requireTariff(tariffId, companyId);
        validateItemRequest(companyId, request);
        validateItemAmbiguity(companyId, tariffId, null, request);
        PriceListItem item = new PriceListItem(companyId, tariffId, request.productId(), request.customerId(),
                request.price(), request.discountPercentage(), request.surchargePercentage(), request.priority(),
                request.validFrom(), request.validUntil(), request.active());
        return TariffItemResponse.from(itemRepository.save(item));
    }

    @Transactional
    public TariffItemResponse updateItem(UUID tariffId, UUID itemId, TariffItemRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        requireTariff(tariffId, companyId);
        PriceListItem item = itemRepository.findByIdAndCompanyIdAndPriceListId(itemId, companyId, tariffId)
                .orElseThrow(() -> new ResourceNotFoundException("Línea de tarifa", itemId));
        if (!item.getProductId().equals(request.productId())
                || !Objects.equals(item.getCustomerId(), request.customerId())) {
            throw new BusinessRuleException("El artículo y el cliente de una línea de tarifa no se pueden modificar.");
        }
        validateItemRequest(companyId, request);
        validateItemAmbiguity(companyId, tariffId, itemId, request);
        item.update(request.price(), request.discountPercentage(), request.surchargePercentage(), request.priority(),
                request.validFrom(), request.validUntil(), request.active());
        return TariffItemResponse.from(item);
    }

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> listRules(UUID tariffId) {
        UUID companyId = companyProvider.requireCompanyId();
        requireTariff(tariffId, companyId);
        return ruleRepository.findAllByCompanyIdAndPriceListId(companyId, tariffId).stream()
                .map(PricingRuleResponse::from).toList();
    }

    @Transactional
    public PricingRuleResponse createRule(UUID tariffId, PricingRuleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        requireTariff(tariffId, companyId);
        validateRuleRequest(companyId, request);
        validateRuleAmbiguity(companyId, tariffId, null, request);
        PricingRule rule = new PricingRule(companyId, tariffId, request.targetType(), request.productNatureId(),
                request.productSupertypeId(), request.productTypeId(), request.productGroupId(), request.productId(),
                request.customerId(), request.fixedPrice(), request.discountPercentage(),
                request.surchargePercentage(), request.priority(), request.validFrom(), request.validUntil(),
                request.active());
        return PricingRuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public PricingRuleResponse updateRule(UUID tariffId, UUID ruleId, PricingRuleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        requireTariff(tariffId, companyId);
        PricingRule rule = ruleRepository.findByIdAndCompanyIdAndPriceListId(ruleId, companyId, tariffId)
                .orElseThrow(() -> new ResourceNotFoundException("Regla de precio", ruleId));
        if (!sameRuleTarget(rule, request)) {
            throw new BusinessRuleException("El objetivo y el cliente de una regla de precio no se pueden modificar.");
        }
        validateRuleRequest(companyId, request);
        validateRuleAmbiguity(companyId, tariffId, ruleId, request);
        rule.update(request.fixedPrice(), request.discountPercentage(), request.surchargePercentage(),
                request.priority(), request.validFrom(), request.validUntil(), request.active());
        return PricingRuleResponse.from(rule);
    }

    PriceList requireTariff(UUID id, UUID companyId) {
        return tariffRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarifa", id));
    }

    private void validateTariffRequest(TariffRequest request) {
        if (request.scope() == null) throw new BusinessRuleException("El ámbito de la tarifa es obligatorio.");
        validateDates(request.validFrom(), request.validUntil());
        if (request.priority() < 0) throw new BusinessRuleException("La prioridad no puede ser negativa.");
        validatePercentage(request.generalSurchargePercentage(), "El recargo general");
        validatePercentage(request.energySurchargePercentage(), "El recargo energético");
        validateNonNegative(request.minimumBillingAmount(), "La facturación mínima");
        validateNonNegative(request.minimumPerPiece(), "El mínimo por pieza");
        if (request.unitMultiple() != null && request.unitMultiple().signum() <= 0) {
            throw new BusinessRuleException("El múltiplo de unidades debe ser mayor que cero.");
        }
    }

    private void validateItemRequest(UUID companyId, TariffItemRequest request) {
        references.requireProduct(request.productId(), companyId);
        if (request.customerId() != null) references.requireCustomer(request.customerId(), companyId);
        validateDates(request.validFrom(), request.validUntil());
        requireNonNegative(request.price(), "El precio");
        requirePercentage(request.discountPercentage(), "El descuento");
        requirePercentage(request.surchargePercentage(), "El recargo");
        if (request.priority() < 0) throw new BusinessRuleException("La prioridad no puede ser negativa.");
    }

    private void validateRuleRequest(UUID companyId, PricingRuleRequest request) {
        if (request.targetType() == null) throw new BusinessRuleException("El objetivo de la regla es obligatorio.");
        references.validateRuleTarget(companyId, request);
        validateDates(request.validFrom(), request.validUntil());
        validateNonNegative(request.fixedPrice(), "El precio fijo");
        requirePercentage(request.discountPercentage(), "El descuento");
        requirePercentage(request.surchargePercentage(), "El recargo");
        if (request.priority() < 0) throw new BusinessRuleException("La prioridad no puede ser negativa.");
        if (request.fixedPrice() == null && isZero(request.discountPercentage())
                && isZero(request.surchargePercentage())) {
            throw new BusinessRuleException("Una regla debe definir precio, descuento o recargo.");
        }
    }

    private void validateParent(UUID companyId, UUID tariffId, UUID parentId, String currency) {
        if (parentId == null) return;
        Set<UUID> visited = new HashSet<>();
        UUID cursor = parentId;
        while (cursor != null) {
            if (Objects.equals(cursor, tariffId) || !visited.add(cursor)) {
                throw new BusinessRuleException("La herencia de tarifas no puede contener ciclos.");
            }
            PriceList parent = requireTariff(cursor, companyId);
            if (!parent.getCurrency().equals(currency)) {
                throw new BusinessRuleException("Una tarifa y su tarifa padre deben usar la misma moneda.");
            }
            cursor = parent.getParentPriceListId();
        }
    }

    private void validateTariffAmbiguity(UUID companyId, UUID tariffId, TariffRequest request) {
        if (!request.active()) return;
        boolean ambiguous = tariffRepository.findAllByCompanyIdAndScope(companyId, request.scope()).stream()
                .filter(existing -> !Objects.equals(existing.getId(), tariffId) && existing.isActive())
                .anyMatch(existing -> existing.getPriority() == request.priority()
                        && sameTariffTarget(existing, request)
                        && datesOverlap(existing.getValidFrom(), existing.getValidUntil(),
                        request.validFrom(), request.validUntil()));
        if (ambiguous) {
            throw new BusinessRuleException("Existe otra tarifa activa con el mismo ámbito, objetivo, prioridad y vigencia.");
        }
    }

    private void validateItemAmbiguity(UUID companyId, UUID tariffId, UUID itemId, TariffItemRequest request) {
        if (!request.active()) return;
        boolean duplicateItem = itemRepository.findAllByCompanyIdAndPriceListId(companyId, tariffId).stream()
                .filter(item -> !Objects.equals(item.getId(), itemId) && item.isActive())
                .anyMatch(item -> item.getProductId().equals(request.productId())
                        && Objects.equals(item.getCustomerId(), request.customerId())
                        && item.getPriority() == request.priority()
                        && datesOverlap(item.getValidFrom(), item.getValidUntil(),
                        request.validFrom(), request.validUntil()));
        boolean conflictingRule = ruleRepository.findAllByCompanyIdAndPriceListId(companyId, tariffId).stream()
                .filter(PricingRule::isActive)
                .anyMatch(rule -> rule.getTargetType() == PricingTargetType.PRODUCT
                        && Objects.equals(rule.getProductId(), request.productId())
                        && Objects.equals(rule.getCustomerId(), request.customerId())
                        && rule.getPriority() == request.priority()
                        && datesOverlap(rule.getValidFrom(), rule.getValidUntil(),
                        request.validFrom(), request.validUntil())
                        && effectsOverlap(request.price(), request.discountPercentage(), request.surchargePercentage(),
                        rule.getFixedPrice(), rule.getDiscountPercentage(), rule.getSurchargePercentage()));
        if (duplicateItem || conflictingRule) {
            throw new BusinessRuleException("Existe una línea o regla ambigua para el mismo artículo, cliente, prioridad y vigencia.");
        }
    }

    private void validateRuleAmbiguity(UUID companyId, UUID tariffId, UUID ruleId, PricingRuleRequest request) {
        if (!request.active()) return;
        boolean duplicateRule = ruleRepository.findAllByCompanyIdAndPriceListId(companyId, tariffId).stream()
                .filter(rule -> !Objects.equals(rule.getId(), ruleId) && rule.isActive())
                .anyMatch(rule -> sameRuleTarget(rule, request) && rule.getPriority() == request.priority()
                        && datesOverlap(rule.getValidFrom(), rule.getValidUntil(),
                        request.validFrom(), request.validUntil())
                        && effectsOverlap(request.fixedPrice(), request.discountPercentage(),
                        request.surchargePercentage(), rule.getFixedPrice(), rule.getDiscountPercentage(),
                        rule.getSurchargePercentage()));
        boolean conflictingItem = request.targetType() == PricingTargetType.PRODUCT
                && itemRepository.findAllByCompanyIdAndPriceListId(companyId, tariffId).stream()
                .filter(PriceListItem::isActive)
                .anyMatch(item -> Objects.equals(item.getProductId(), request.productId())
                        && Objects.equals(item.getCustomerId(), request.customerId())
                        && item.getPriority() == request.priority()
                        && datesOverlap(item.getValidFrom(), item.getValidUntil(),
                        request.validFrom(), request.validUntil())
                        && effectsOverlap(request.fixedPrice(), request.discountPercentage(),
                        request.surchargePercentage(), item.getPrice(), item.getDiscountPercentage(),
                        item.getSurchargePercentage()));
        if (duplicateRule || conflictingItem) {
            throw new BusinessRuleException("Existe otra regla o línea ambigua para el mismo objetivo, prioridad y vigencia.");
        }
    }

    private boolean sameTariffTarget(PriceList existing, TariffRequest request) {
        return Objects.equals(existing.getCustomerId(), request.customerId())
                && Objects.equals(existing.getProductNatureId(), request.productNatureId())
                && Objects.equals(existing.getProductSupertypeId(), request.productSupertypeId())
                && Objects.equals(existing.getProductTypeId(), request.productTypeId())
                && Objects.equals(existing.getProductGroupId(), request.productGroupId())
                && Objects.equals(existing.getProductId(), request.productId());
    }

    private boolean sameRuleTarget(PricingRule rule, PricingRuleRequest request) {
        return rule.getTargetType() == request.targetType()
                && Objects.equals(rule.getProductNatureId(), request.productNatureId())
                && Objects.equals(rule.getProductSupertypeId(), request.productSupertypeId())
                && Objects.equals(rule.getProductTypeId(), request.productTypeId())
                && Objects.equals(rule.getProductGroupId(), request.productGroupId())
                && Objects.equals(rule.getProductId(), request.productId())
                && Objects.equals(rule.getCustomerId(), request.customerId());
    }

    private boolean effectsOverlap(BigDecimal fixedA, BigDecimal discountA, BigDecimal surchargeA,
                                   BigDecimal fixedB, BigDecimal discountB, BigDecimal surchargeB) {
        return (fixedA != null && fixedB != null)
                || (!isZero(discountA) && !isZero(discountB))
                || (!isZero(surchargeA) && !isZero(surchargeB));
    }

    private boolean datesOverlap(LocalDate fromA, LocalDate untilA, LocalDate fromB, LocalDate untilB) {
        LocalDate endA = untilA == null ? LocalDate.MAX : untilA;
        LocalDate endB = untilB == null ? LocalDate.MAX : untilB;
        return !fromA.isAfter(endB) && !fromB.isAfter(endA);
    }

    private void validateDates(LocalDate validFrom, LocalDate validUntil) {
        if (validFrom == null) throw new BusinessRuleException("La fecha inicial de vigencia es obligatoria.");
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new BusinessRuleException("La fecha final de vigencia no puede ser anterior a la inicial.");
        }
    }

    private void validatePercentage(BigDecimal value, String label) {
        if (value != null && (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0)) {
            throw new BusinessRuleException(label + " debe estar entre 0 y 100.");
        }
    }

    private void validateNonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw new BusinessRuleException(label + " no puede ser negativo.");
        }
    }

    private void requireNonNegative(BigDecimal value, String label) {
        if (value == null) throw new BusinessRuleException(label + " es obligatorio.");
        validateNonNegative(value, label);
    }

    private void requirePercentage(BigDecimal value, String label) {
        if (value == null) throw new BusinessRuleException(label + " es obligatorio.");
        validatePercentage(value, label);
    }

    private boolean isZero(BigDecimal value) { return value == null || value.signum() == 0; }
    private String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }
    private String normalizeCurrency(String currency) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new BusinessRuleException("La moneda debe usar un código ISO-4217 de tres letras.");
        }
        return normalized;
    }
    private String normalizeQuery(String query) { return query == null || query.isBlank() ? "" : query.trim(); }
}
