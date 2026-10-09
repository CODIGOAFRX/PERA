package com.peraerp.sales.commission;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.commission.CommissionDtos.CalculateRequest;
import com.peraerp.sales.commission.CommissionDtos.CalculateResponse;
import com.peraerp.sales.commission.CommissionDtos.CommissionResponse;
import com.peraerp.sales.commission.CommissionDtos.RuleRequest;
import com.peraerp.sales.commission.CommissionDtos.RuleResponse;
import com.peraerp.sales.commission.CommissionDtos.SettleRequest;
import com.peraerp.sales.commission.CommissionDtos.Totals;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.CommercialDocumentRepository;
import com.peraerp.sales.document.DocumentLine;
import com.peraerp.sales.document.DocumentType;
import com.peraerp.sales.document.MonetaryRounding;
import com.peraerp.sales.document.PaymentStatus;
import com.peraerp.sales.masterdata.SalesMasterDataService;
import com.peraerp.sales.masterdata.SalespersonSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comisiones de los comerciales, como «Comisiones vendedores» de DimproCristalWin: reglas por comercial,
 * recálculo por periodo sobre sus facturas y liquidación. Lo liquidado no se vuelve a calcular.
 */
@Service
public class CommissionService {
    /** Un periodo de cálculo de más de un año es casi seguro un error de fechas. */
    private static final int MAX_PERIOD_DAYS = 366;

    private final CommissionRuleRepository rules;
    private final SalesCommissionRepository commissions;
    private final CommercialDocumentRepository documents;
    private final SalesMasterDataService masterData;
    private final CurrentCompanyProvider companyProvider;
    private final Clock clock;

    public CommissionService(CommissionRuleRepository rules, SalesCommissionRepository commissions,
                             CommercialDocumentRepository documents, SalesMasterDataService masterData,
                             CurrentCompanyProvider companyProvider) {
        this.rules = rules;
        this.commissions = commissions;
        this.documents = documents;
        this.masterData = masterData;
        this.companyProvider = companyProvider;
        this.clock = Clock.systemUTC();
    }

    // --- reglas

    @Transactional(readOnly = true)
    public List<RuleResponse> rules(UUID salespersonId) {
        return rules.findAllByCompanyIdAndSalespersonIdOrderByCreatedAtAsc(companyProvider.requireCompanyId(), salespersonId)
                .stream().map(RuleResponse::from).toList();
    }

    @Transactional
    public RuleResponse createRule(RuleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        masterData.findSalesperson(request.salespersonId());
        CommissionRule rule = new CommissionRule(companyId, request.salespersonId());
        apply(rule, request);
        return RuleResponse.from(rules.save(rule));
    }

    @Transactional
    public RuleResponse updateRule(UUID id, RuleRequest request) {
        CommissionRule rule = rules.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Regla de comisión", id));
        if (!rule.getSalespersonId().equals(request.salespersonId())) {
            throw new BusinessRuleException("Una regla no se puede pasar a otro comercial.");
        }
        apply(rule, request);
        return RuleResponse.from(rule);
    }

    private void apply(CommissionRule rule, RuleRequest request) {
        if (request.productId() != null && request.productGroupId() != null) {
            throw new BusinessRuleException("Una regla es de un artículo o de un grupo, no de los dos.");
        }
        if (request.amountFrom() != null && request.amountTo() != null
                && request.amountFrom().compareTo(request.amountTo()) > 0) {
            throw new BusinessRuleException("El importe «desde» no puede ser mayor que el «hasta».");
        }
        String productLabel = null;
        if (request.productId() != null) {
            var product = masterData.findProduct(request.productId());
            productLabel = product.code() + " · " + product.name();
        }
        String groupLabel = null;
        if (request.productGroupId() != null) {
            var group = masterData.findProductGroup(request.productGroupId());
            groupLabel = group.code() + " · " + group.name();
        }
        rule.update(request.productId(), productLabel, request.productGroupId(), groupLabel, request.amountFrom(),
                request.amountTo(), request.percentage(), request.active() == null || request.active());
    }

    // --- cálculo

    /**
     * Recalcula las comisiones de las facturas del periodo, de un comercial o de todos. Las rectificativas
     * restan. Las ya liquidadas no se tocan.
     */
    @Transactional
    public CalculateResponse calculate(CalculateRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (request.fromDate().isAfter(request.toDate())) {
            throw new BusinessRuleException("La fecha inicial es posterior a la final.");
        }
        if (request.fromDate().plusDays(MAX_PERIOD_DAYS).isBefore(request.toDate())) {
            throw new BusinessRuleException("Calcula como mucho un año de una vez.");
        }
        List<CommercialDocument> invoices = documents.findForCommissions(companyId, request.salespersonId(),
                request.fromDate(), request.toDate());
        Map<UUID, List<CommissionRule>> rulesBySalesperson = new HashMap<>();
        Map<UUID, BigDecimal> defaults = new HashMap<>();
        Map<UUID, UUID> groups = new HashMap<>();
        Instant now = Instant.now(clock);
        int calculated = 0;
        int settled = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (CommercialDocument invoice : invoices) {
            SalesCommission commission = commissions.findByCompanyIdAndDocumentId(companyId, invoice.getId())
                    .orElseGet(() -> new SalesCommission(companyId, invoice.getId()));
            if (commission.isSettled()) {
                settled++;
                continue;
            }
            UUID salespersonId = invoice.getSalespersonId();
            List<CommissionRule> applicable = rulesBySalesperson.computeIfAbsent(salespersonId,
                    id -> rules.findAllByCompanyIdAndSalespersonIdAndActiveTrue(companyId, id));
            BigDecimal defaultPercentage = defaults.computeIfAbsent(salespersonId, this::defaultPercentage);
            List<SalesCommissionLine> lines = new ArrayList<>();
            for (DocumentLine line : invoice.getLines()) {
                UUID group = line.getProductId() == null ? null
                        : groups.computeIfAbsent(line.getProductId(), masterData::productGroupOf);
                lines.add(CommissionCalculator.calculate(new CommissionCalculator.Line(line.getLineOrder(),
                        line.getDescription(), baseAmount(invoice, line), line.getProductId(), group),
                        applicable, defaultPercentage));
            }
            commission.recalculate(salespersonId, invoice.getSalespersonName(), lines, now);
            commissions.save(commission);
            calculated++;
            total = total.add(commission.getCommissionAmount());
        }
        return new CalculateResponse(invoices.size(), calculated, settled, total);
    }

    /** Comisión por defecto del comercial. Sin ella, las líneas sin regla no comisionan. */
    private BigDecimal defaultPercentage(UUID salespersonId) {
        SalespersonSnapshot salesperson = masterData.findSalesperson(salespersonId);
        return salesperson.commissionPercentage();
    }

    /** Neto de la línea en moneda base; las rectificativas, en negativo porque corrigen una venta. */
    private static BigDecimal baseAmount(CommercialDocument invoice, DocumentLine line) {
        BigDecimal rate = invoice.getExchangeRate() == null ? BigDecimal.ONE : invoice.getExchangeRate();
        BigDecimal amount = MonetaryRounding.round(line.getNetAmount().multiply(rate));
        return invoice.getType() == DocumentType.RECTIFYING_INVOICE ? amount.negate() : amount;
    }

    // --- consulta y liquidación

    @Transactional(readOnly = true)
    public Page<CommissionResponse> search(UUID salespersonId, SalesCommission.Status status, LocalDate fromDate,
                                           LocalDate toDate, Boolean collected, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100));
        Page<SalesCommission> result = commissions.search(companyId, salespersonId, status, fromDate, toDate,
                collected, PaymentStatus.PAID, page);
        Map<UUID, CommercialDocument> byId = documents.findAllById(
                        result.getContent().stream().map(SalesCommission::getDocumentId).toList())
                .stream().collect(Collectors.toMap(CommercialDocument::getId, Function.identity()));
        return result.map(commission -> CommissionResponse.from(commission, byId.get(commission.getDocumentId()), false));
    }

    @Transactional(readOnly = true)
    public Totals totals(UUID salespersonId, SalesCommission.Status status, LocalDate fromDate, LocalDate toDate,
                         Boolean collected) {
        Object[] row = commissions.totals(companyProvider.requireCompanyId(), salespersonId, status, fromDate, toDate,
                collected, PaymentStatus.PAID).getFirst();
        return new Totals((Long) row[2], (BigDecimal) row[0], (BigDecimal) row[1]);
    }

    @Transactional(readOnly = true)
    public CommissionResponse findById(UUID id) {
        SalesCommission commission = require(id);
        return CommissionResponse.from(commission, documents.findById(commission.getDocumentId()).orElse(null), true);
    }

    @Transactional
    public List<CommissionResponse> settle(SettleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        List<SalesCommission> selected = commissions.findAllByCompanyIdAndIdIn(companyId, request.ids());
        if (selected.size() != request.ids().stream().distinct().count()) {
            throw new BusinessRuleException("Alguna de las comisiones no existe.");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        selected.forEach(commission -> commission.settle(request.settledOn(), note));
        return selected.stream().map(commission -> CommissionResponse.from(commission, null, false)).toList();
    }

    /** Deshace una liquidación hecha por error: la comisión vuelve a pendiente y se puede recalcular. */
    @Transactional
    public CommissionResponse reopen(UUID id) {
        SalesCommission commission = require(id);
        commission.reopen();
        return CommissionResponse.from(commission, documents.findById(commission.getDocumentId()).orElse(null), true);
    }

    /** El comercial de una factura con la comisión liquidada no se cambia sin deshacer antes la liquidación. */
    @Transactional(readOnly = true)
    public void requireNotSettled(UUID documentId) {
        commissions.findByCompanyIdAndDocumentId(companyProvider.requireCompanyId(), documentId)
                .filter(SalesCommission::isSettled)
                .ifPresent(settled -> {
                    throw new BusinessRuleException("La comisión de este documento ya está liquidada. "
                            + "Deshaz la liquidación antes de cambiar el comercial.");
                });
    }

    private SalesCommission require(UUID id) {
        return commissions.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Comisión", id));
    }
}
