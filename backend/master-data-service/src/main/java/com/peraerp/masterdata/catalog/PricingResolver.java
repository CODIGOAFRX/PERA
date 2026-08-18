package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class PricingResolver {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final PriceListRepository tariffRepository;
    private final PriceListItemRepository itemRepository;
    private final PricingRuleRepository ruleRepository;
    private final PricingReferenceService references;
    private final CurrentCompanyProvider companyProvider;

    public PricingResolver(PriceListRepository tariffRepository, PriceListItemRepository itemRepository,
                           PricingRuleRepository ruleRepository, PricingReferenceService references,
                           CurrentCompanyProvider companyProvider) {
        this.tariffRepository = tariffRepository;
        this.itemRepository = itemRepository;
        this.ruleRepository = ruleRepository;
        this.references = references;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public PricingResolveResponse resolve(PricingResolveRequest request) {
        validateRequest(request);
        UUID companyId = companyProvider.requireCompanyId();
        PricingContext context = references.resolveContext(companyId, request);
        PriceList selected = selectTariff(context);
        List<PriceList> chain = selected == null ? List.of() : inheritanceChain(selected, companyId);
        EffectiveSettings settings = effectiveSettings(chain);
        List<RuleCandidate> candidates = matchingRules(chain, context);
        List<PricingTraceStep> trace = new ArrayList<>();

        if (selected != null) {
            for (int index = 0; index < chain.size(); index++) {
                PriceList tariff = chain.get(index);
                String operation = index == chain.size() - 1 ? "SELECTED_TARIFF" : "INHERITED_TARIFF";
                addTrace(trace, operation, tariff.getId(), tariff.getCode(),
                        index == chain.size() - 1 ? "Tarifa seleccionada" : "Configuración heredada", null, null);
            }
        }

        BigDecimal baseUnitPrice = money(context.basePrice());
        BigDecimal unitPrice = baseUnitPrice;
        addTrace(trace, "BASE_PRICE", null, null, "Precio unitario base", baseUnitPrice, baseUnitPrice);

        RuleCandidate fixedPrice = winner(candidates, candidate -> candidate.fixedPrice() != null);
        if (fixedPrice != null) {
            BigDecimal before = unitPrice;
            unitPrice = money(fixedPrice.fixedPrice());
            addRuleTrace(trace, "FIXED_PRICE", fixedPrice, before, unitPrice);
        }

        RuleCandidate discount = winner(candidates, candidate -> positive(candidate.discountPercentage()));
        if (discount != null) {
            BigDecimal before = unitPrice;
            unitPrice = applyDecrease(unitPrice, discount.discountPercentage());
            addRuleTrace(trace, "DISCOUNT", discount, before, unitPrice);
        }

        RuleCandidate surcharge = winner(candidates, candidate -> positive(candidate.surchargePercentage()));
        if (surcharge != null) {
            BigDecimal before = unitPrice;
            unitPrice = applyIncrease(unitPrice, surcharge.surchargePercentage());
            addRuleTrace(trace, "RULE_SURCHARGE", surcharge, before, unitPrice);
        }

        if (settings.minimumPerPiece() != null && unitPrice.compareTo(settings.minimumPerPiece().value()) < 0) {
            BigDecimal before = unitPrice;
            unitPrice = money(settings.minimumPerPiece().value());
            addSettingTrace(trace, "MINIMUM_PER_PIECE", settings.minimumPerPiece(),
                    "Mínimo monetario por pieza", before, unitPrice);
        }

        BigDecimal billedQuantity = context.quantity();
        if (settings.unitMultiple() != null) {
            BigDecimal multiple = settings.unitMultiple().value();
            billedQuantity = context.quantity().divide(multiple, 0, RoundingMode.CEILING).multiply(multiple);
            if (billedQuantity.compareTo(context.quantity()) != 0) {
                addSettingTrace(trace, "UNIT_MULTIPLE", settings.unitMultiple(),
                        "Cantidad redondeada al múltiplo configurado", context.quantity(), billedQuantity);
            }
        }

        unitPrice = money(unitPrice);
        BigDecimal subtotal = money(unitPrice.multiply(billedQuantity));
        BigDecimal finalPrice = subtotal;

        if (settings.generalSurcharge() != null && positive(settings.generalSurcharge().value())) {
            BigDecimal before = finalPrice;
            finalPrice = applyIncrease(finalPrice, settings.generalSurcharge().value());
            addSettingTrace(trace, "GENERAL_SURCHARGE", settings.generalSurcharge(),
                    "Recargo general", before, finalPrice);
        }
        if (settings.energySurcharge() != null && positive(settings.energySurcharge().value())) {
            BigDecimal before = finalPrice;
            finalPrice = applyIncrease(finalPrice, settings.energySurcharge().value());
            addSettingTrace(trace, "ENERGY_SURCHARGE", settings.energySurcharge(),
                    "Recargo energético", before, finalPrice);
        }
        if (settings.minimumBilling() != null && finalPrice.compareTo(settings.minimumBilling().value()) < 0) {
            BigDecimal before = finalPrice;
            finalPrice = money(settings.minimumBilling().value());
            addSettingTrace(trace, "MINIMUM_BILLING", settings.minimumBilling(),
                    "Facturación mínima", before, finalPrice);
        }

        return new PricingResolveResponse(selected == null ? null : selected.getId(),
                selected == null ? null : selected.getCode(), context.currency(), context.quantity(),
                billedQuantity, baseUnitPrice, unitPrice, subtotal, money(finalPrice), List.copyOf(trace));
    }

    private PriceList selectTariff(PricingContext context) {
        List<PriceList> candidates = tariffRepository.findResolutionCandidates(context.companyId(),
                        context.currency(), context.date()).stream()
                .filter(tariff -> matches(tariff, context))
                .sorted((left, right) -> compareTariffs(left, right, context.assignedTariffId()))
                .toList();
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private int compareTariffs(PriceList left, PriceList right, UUID assignedTariffId) {
        int assigned = Boolean.compare(Objects.equals(right.getId(), assignedTariffId),
                Objects.equals(left.getId(), assignedTariffId));
        if (assigned != 0) return assigned;
        int priority = Integer.compare(right.getPriority(), left.getPriority());
        if (priority != 0) return priority;
        int specificity = Integer.compare(scopeSpecificity(right.getScope()), scopeSpecificity(left.getScope()));
        if (specificity != 0) return specificity;
        int customer = Boolean.compare(right.getCustomerId() != null, left.getCustomerId() != null);
        if (customer != 0) return customer;
        return left.getCode().compareTo(right.getCode());
    }

    private boolean matches(PriceList tariff, PricingContext context) {
        if (tariff.getCustomerId() != null && !Objects.equals(tariff.getCustomerId(), context.customerId())) {
            return false;
        }
        return switch (tariff.getScope()) {
            case GENERAL -> true;
            case CUSTOMER -> Objects.equals(tariff.getCustomerId(), context.customerId());
            case PRODUCT_NATURE -> Objects.equals(tariff.getProductNatureId(), context.productNatureId());
            case PRODUCT_SUPERTYPE -> Objects.equals(tariff.getProductSupertypeId(), context.productSupertypeId());
            case PRODUCT_TYPE -> Objects.equals(tariff.getProductTypeId(), context.productTypeId());
            case PRODUCT_GROUP -> Objects.equals(tariff.getProductGroupId(), context.productGroupId());
            case PRODUCT -> Objects.equals(tariff.getProductId(), context.productId());
        };
    }

    private List<PriceList> inheritanceChain(PriceList selected, UUID companyId) {
        LinkedList<PriceList> chain = new LinkedList<>();
        Set<UUID> visited = new HashSet<>();
        PriceList cursor = selected;
        while (cursor != null) {
            if (cursor.getId() != null && !visited.add(cursor.getId())) {
                throw new BusinessRuleException("Se ha detectado un ciclo en la herencia de tarifas.");
            }
            chain.addFirst(cursor);
            UUID parentId = cursor.getParentPriceListId();
            if (parentId == null) break;
            cursor = tariffRepository.findByIdAndCompanyId(parentId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tarifa padre", parentId));
            if (!cursor.getCurrency().equals(selected.getCurrency())) {
                throw new BusinessRuleException("La cadena de tarifas contiene monedas incompatibles.");
            }
        }
        return List.copyOf(chain);
    }

    private EffectiveSettings effectiveSettings(List<PriceList> chain) {
        SettingValue general = null;
        SettingValue energy = null;
        SettingValue minimumBilling = null;
        SettingValue unitMultiple = null;
        SettingValue minimumPiece = null;
        for (PriceList tariff : chain) {
            if (tariff.getGeneralSurchargePercentage() != null) {
                general = setting(tariff, tariff.getGeneralSurchargePercentage());
            }
            if (tariff.getEnergySurchargePercentage() != null) {
                energy = setting(tariff, tariff.getEnergySurchargePercentage());
            }
            if (tariff.getMinimumBillingAmount() != null) {
                minimumBilling = setting(tariff, tariff.getMinimumBillingAmount());
            }
            if (tariff.getUnitMultiple() != null) {
                unitMultiple = setting(tariff, tariff.getUnitMultiple());
            }
            if (tariff.getMinimumPerPiece() != null) {
                minimumPiece = setting(tariff, tariff.getMinimumPerPiece());
            }
        }
        return new EffectiveSettings(general, energy, minimumBilling, unitMultiple, minimumPiece);
    }

    private List<RuleCandidate> matchingRules(List<PriceList> chain, PricingContext context) {
        List<RuleCandidate> candidates = new ArrayList<>();
        for (int depth = 0; depth < chain.size(); depth++) {
            PriceList tariff = chain.get(depth);
            for (PriceListItem item : itemRepository.findAllByCompanyIdAndPriceListId(
                    context.companyId(), tariff.getId())) {
                if (item.isEffectiveOn(context.date()) && Objects.equals(item.getProductId(), context.productId())
                        && (item.getCustomerId() == null
                        || Objects.equals(item.getCustomerId(), context.customerId()))) {
                    candidates.add(new RuleCandidate(item.getId(), tariff.getCode(), "Línea de tarifa",
                            depth, item.getPriority(), 5, item.getCustomerId() != null, item.getPrice(),
                            item.getDiscountPercentage(), item.getSurchargePercentage()));
                }
            }
            for (PricingRule rule : ruleRepository.findAllByCompanyIdAndPriceListId(
                    context.companyId(), tariff.getId())) {
                if (rule.isEffectiveOn(context.date()) && matches(rule, context)) {
                    candidates.add(new RuleCandidate(rule.getId(), tariff.getCode(), "Regla " + rule.getTargetType(),
                            depth, rule.getPriority(), targetSpecificity(rule.getTargetType()),
                            rule.getCustomerId() != null, rule.getFixedPrice(), rule.getDiscountPercentage(),
                            rule.getSurchargePercentage()));
                }
            }
        }
        candidates.sort(this::compareRules);
        return candidates;
    }

    private boolean matches(PricingRule rule, PricingContext context) {
        if (rule.getCustomerId() != null && !Objects.equals(rule.getCustomerId(), context.customerId())) {
            return false;
        }
        return switch (rule.getTargetType()) {
            case PRODUCT_NATURE -> Objects.equals(rule.getProductNatureId(), context.productNatureId());
            case PRODUCT_SUPERTYPE -> Objects.equals(rule.getProductSupertypeId(), context.productSupertypeId());
            case PRODUCT_TYPE -> Objects.equals(rule.getProductTypeId(), context.productTypeId());
            case PRODUCT_GROUP -> Objects.equals(rule.getProductGroupId(), context.productGroupId());
            case PRODUCT -> Objects.equals(rule.getProductId(), context.productId());
        };
    }

    private int compareRules(RuleCandidate left, RuleCandidate right) {
        int priority = Integer.compare(right.priority(), left.priority());
        if (priority != 0) return priority;
        int customer = Boolean.compare(right.customerSpecific(), left.customerSpecific());
        if (customer != 0) return customer;
        int specificity = Integer.compare(right.specificity(), left.specificity());
        if (specificity != 0) return specificity;
        int depth = Integer.compare(right.depth(), left.depth());
        if (depth != 0) return depth;
        return stableId(left.id()).compareTo(stableId(right.id()));
    }

    private RuleCandidate winner(List<RuleCandidate> candidates, Predicate<RuleCandidate> predicate) {
        return candidates.stream().filter(predicate).findFirst().orElse(null);
    }

    private void validateRequest(PricingResolveRequest request) {
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw new BusinessRuleException("La cantidad debe ser mayor que cero.");
        }
        if (request.date() == null) throw new BusinessRuleException("La fecha de resolución es obligatoria.");
        if (request.basePrice() == null || request.basePrice().signum() < 0) {
            throw new BusinessRuleException("El precio base no puede ser negativo.");
        }
        if (request.currency() == null || !request.currency().trim().matches("(?i)[A-Z]{3}")) {
            throw new BusinessRuleException("La moneda debe usar un código ISO-4217 de tres letras.");
        }
    }

    private BigDecimal applyDecrease(BigDecimal value, BigDecimal percentage) {
        return value.multiply(ONE_HUNDRED.subtract(percentage))
                .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal applyIncrease(BigDecimal value, BigDecimal percentage) {
        return value.multiply(ONE_HUNDRED.add(percentage))
                .divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private String stableId(UUID id) { return id == null ? "" : id.toString(); }
    private SettingValue setting(PriceList tariff, BigDecimal value) {
        return new SettingValue(value, tariff.getId(), tariff.getCode());
    }

    private int scopeSpecificity(PricingScope scope) {
        return switch (scope) {
            case GENERAL -> 0;
            case CUSTOMER -> 1;
            case PRODUCT_NATURE -> 2;
            case PRODUCT_SUPERTYPE -> 3;
            case PRODUCT_TYPE -> 4;
            case PRODUCT_GROUP -> 5;
            case PRODUCT -> 6;
        };
    }

    private int targetSpecificity(PricingTargetType type) {
        return switch (type) {
            case PRODUCT_NATURE -> 1;
            case PRODUCT_SUPERTYPE -> 2;
            case PRODUCT_TYPE -> 3;
            case PRODUCT_GROUP -> 4;
            case PRODUCT -> 5;
        };
    }

    private void addRuleTrace(List<PricingTraceStep> trace, String operation, RuleCandidate candidate,
                              BigDecimal before, BigDecimal after) {
        addTrace(trace, operation, candidate.id(), candidate.tariffCode(), candidate.description(), before, after);
    }

    private void addSettingTrace(List<PricingTraceStep> trace, String operation, SettingValue setting,
                                 String description, BigDecimal before, BigDecimal after) {
        addTrace(trace, operation, setting.tariffId(), setting.tariffCode(), description, before, after);
    }

    private void addTrace(List<PricingTraceStep> trace, String operation, UUID sourceId, String sourceCode,
                          String description, BigDecimal before, BigDecimal after) {
        trace.add(new PricingTraceStep(trace.size() + 1, operation, sourceId, sourceCode, description,
                before == null ? null : before.stripTrailingZeros(), after == null ? null : after.stripTrailingZeros()));
    }

    private record RuleCandidate(UUID id, String tariffCode, String description, int depth, int priority,
                                 int specificity, boolean customerSpecific, BigDecimal fixedPrice,
                                 BigDecimal discountPercentage, BigDecimal surchargePercentage) {}

    private record SettingValue(BigDecimal value, UUID tariffId, String tariffCode) {}

    private record EffectiveSettings(SettingValue generalSurcharge, SettingValue energySurcharge,
                                     SettingValue minimumBilling, SettingValue unitMultiple,
                                     SettingValue minimumPerPiece) {}
}
