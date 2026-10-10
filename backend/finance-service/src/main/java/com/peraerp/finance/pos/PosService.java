package com.peraerp.finance.pos;

import com.peraerp.finance.cash.CashDtos.CashMovementRequest;
import com.peraerp.finance.cash.CashDtos.CashMovementResponse;
import com.peraerp.finance.cash.CashDtos.CashSessionResponse;
import com.peraerp.finance.cash.CashDtos.CloseCashSessionRequest;
import com.peraerp.finance.cash.CashDtos.OpenCashSessionRequest;
import com.peraerp.finance.cash.CashMovement;
import com.peraerp.finance.cash.CashMovementType;
import com.peraerp.finance.cash.CashRegister;
import com.peraerp.finance.cash.CashRegisterRepository;
import com.peraerp.finance.cash.CashService;
import com.peraerp.finance.cash.CashSession;
import com.peraerp.finance.cash.CashSessionRepository;
import com.peraerp.finance.cash.CashSessionStatus;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.config.CurrentUserProvider;
import com.peraerp.finance.pos.PosCalculator.LineInput;
import com.peraerp.finance.pos.PosCalculator.Payment;
import com.peraerp.finance.pos.PosCalculator.Totals;
import com.peraerp.finance.pos.PosDtos.DaySales;
import com.peraerp.finance.pos.PosDtos.GroupSales;
import com.peraerp.finance.pos.PosDtos.HourSales;
import com.peraerp.finance.pos.PosDtos.OpenPosSessionRequest;
import com.peraerp.finance.pos.PosDtos.OpenSessionHeader;
import com.peraerp.finance.pos.PosDtos.PosFigures;
import com.peraerp.finance.pos.PosDtos.PosRegisterResponse;
import com.peraerp.finance.pos.PosDtos.PosSessionSummary;
import com.peraerp.finance.pos.PosDtos.PosStatsResponse;
import com.peraerp.finance.pos.PosDtos.ProductSales;
import com.peraerp.finance.pos.PosDtos.RefundLineRequest;
import com.peraerp.finance.pos.PosDtos.RefundRequest;
import com.peraerp.finance.pos.PosDtos.SaleLineRequest;
import com.peraerp.finance.pos.PosDtos.SaleRequest;
import com.peraerp.finance.pos.PosDtos.SessionLine;
import com.peraerp.finance.pos.PosDtos.TicketLineResponse;
import com.peraerp.finance.pos.PosDtos.TicketResponse;
import com.peraerp.finance.pos.PosDtos.VoidTicketRequest;
import com.peraerp.finance.pos.PosProductClient.ProductSnapshot;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Terminal punto de venta sobre las cajas de Finanzas.
 *
 * <p>El TPV no tiene caja propia: usa las cajas y sesiones de cartera. Abrir el TPV es abrir una
 * sesión; cobrar en efectivo anota el apunte en su diario; cerrar es arquear. Así la pantalla de
 * Cartera y el TPV ven siempre el mismo dinero.</p>
 */
@Service
public class PosService {

    static final int STATS_MAX_DAYS = 366;
    private static final int TOP_PRODUCTS = 10;
    private static final int LINE_BATCH = 500;

    /** Ticket tal como lo lee operations-service para mover existencias. */
    public record PosStockSnapshot(UUID id, String number, String type, String status, LocalDate issueDate,
                                   String customerCode, String customerName, Instant updatedAt, List<Line> lines) {
        public record Line(int order, UUID productId, String productCode, String description, BigDecimal quantity) {
        }
    }

    private final CashService cashService;
    private final CashRegisterRepository registers;
    private final CashSessionRepository sessions;
    private final PosTicketRepository tickets;
    private final PosTicketLineRepository lines;
    private final PosTicketSequenceRepository sequences;
    private final PosProductClient productClient;
    private final PosStockNotifier stockNotifier;
    private final CurrentCompanyProvider companyProvider;
    private final CurrentUserProvider userProvider;
    private final ZoneId defaultZone;

    public PosService(CashService cashService, CashRegisterRepository registers, CashSessionRepository sessions,
                      PosTicketRepository tickets, PosTicketLineRepository lines,
                      PosTicketSequenceRepository sequences, PosProductClient productClient,
                      PosStockNotifier stockNotifier, CurrentCompanyProvider companyProvider,
                      CurrentUserProvider userProvider, @Value("${pera.pos.time-zone:Europe/Madrid}") String zone) {
        this.cashService = cashService;
        this.registers = registers;
        this.sessions = sessions;
        this.tickets = tickets;
        this.lines = lines;
        this.sequences = sequences;
        this.productClient = productClient;
        this.stockNotifier = stockNotifier;
        this.companyProvider = companyProvider;
        this.userProvider = userProvider;
        this.defaultZone = ZoneId.of(zone);
    }

    // ---------------------------------------------------------------- cajas y sesiones

    @Transactional(readOnly = true)
    public List<PosRegisterResponse> registers() {
        UUID companyId = companyProvider.requireCompanyId();
        return registers.findAllByCompanyIdOrderByCodeAsc(companyId).stream().filter(CashRegister::isActive)
                .map(register -> {
                    OpenSessionHeader open = sessions.findFirstByCompanyIdAndCashRegisterIdAndStatus(companyId,
                            register.getId(), CashSessionStatus.OPEN).map(this::header).orElse(null);
                    Instant lastClosedAt = sessions.findFirstByCompanyIdAndCashRegisterIdAndStatusOrderByClosedAtDesc(
                            companyId, register.getId(), CashSessionStatus.CLOSED).map(CashSession::getClosedAt)
                            .orElse(null);
                    return new PosRegisterResponse(register.getId(), register.getCode(), register.getName(),
                            register.getOwnerName(), register.isActive(), open, lastClosedAt);
                }).toList();
    }

    @Transactional
    public PosSessionSummary open(UUID registerId, OpenPosSessionRequest request) {
        CashSessionResponse opened = cashService.openSession(new OpenCashSessionRequest(registerId,
                request.openingAmount()));
        return summary(opened.id());
    }

    @Transactional
    public PosSessionSummary addMovement(UUID sessionId, CashMovementRequest request) {
        cashService.addMovement(sessionId, request);
        return summary(sessionId);
    }

    @Transactional
    public PosSessionSummary close(UUID sessionId, CloseCashSessionRequest request) {
        cashService.closeSession(sessionId, request);
        return summary(sessionId);
    }

    @Transactional(readOnly = true)
    public PosSessionSummary summary(UUID sessionId) {
        UUID companyId = companyProvider.requireCompanyId();
        CashSession session = sessions.findByIdAndCompanyId(sessionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja", sessionId));
        CashRegister register = requireRegister(companyId, session.getCashRegisterId());
        List<PosTicket> sessionTickets = tickets.findAllByCompanyIdAndCashSessionIdOrderByIssuedAtAsc(companyId,
                sessionId);
        PosFigures figures = figures(sessionTickets, linesByTicket(companyId, sessionTickets), defaultZone);
        List<CashMovement> movements = cashService.sessionMovements(session);
        BigDecimal income = sum(movements, CashMovementType.INCOME);
        BigDecimal outflow = sum(movements, CashMovementType.EXPENSE).add(sum(movements, CashMovementType.WITHDRAWAL));
        BigDecimal collections = sum(movements, CashMovementType.SALE_COLLECTION);
        boolean closed = session.getStatus() == CashSessionStatus.CLOSED;
        BigDecimal expected = closed ? session.getExpectedClosingAmount() : cashService.balanceOf(session);
        BigDecimal counted = session.getActualClosingAmount();
        return new PosSessionSummary(session.getId(), register.getId(), register.getCode(), register.getName(),
                session.getStatus().name(), session.getOpenedAt(), session.getClosedAt(), session.getOpeningAmount(),
                income, outflow, collections, expected, counted, counted == null ? null : counted.subtract(expected),
                session.getClosingNote(), figures,
                movements.stream().map(CashMovementResponse::from).toList());
    }

    // ---------------------------------------------------------------- ventas

    @Transactional
    public TicketResponse sell(SaleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (request.clientReference() != null) {
            var existing = tickets.findByCompanyIdAndClientReference(companyId, request.clientReference());
            if (existing.isPresent()) {
                return response(companyId, existing.get());
            }
        }
        // Los artículos se consultan antes de bloquear la caja: la consulta remota no debe retener la sesión.
        List<LineInput> inputs = resolveLines(request.lines());
        BigDecimal ticketDiscount = percent(request.discountPercent());
        Totals totals = PosCalculator.saleTotals(inputs, ticketDiscount);
        Payment payment = PosCalculator.payment(request.paymentMethod(), totals.totalAmount(), request.cashTendered(),
                request.cardAmount());

        CashSession session = lockOpenSession(companyId, request.sessionId());
        CashRegister register = requireRegister(companyId, session.getCashRegisterId());
        Instant now = Instant.now();
        String number = nextNumber(companyId, register, PosTicketKind.SALE, now);
        PosTicket ticket = tickets.save(new PosTicket(companyId, register.getId(), session.getId(), number,
                PosTicketKind.SALE, now, userProvider.requireUserId(), userProvider.displayName(),
                text(request.customerName()), taxId(request.customerTaxId()), null, ticketDiscount, totals, payment,
                text(request.note()), request.clientReference()));
        List<PosTicketLine> saved = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            saved.add(new PosTicketLine(companyId, ticket.getId(), index + 1, inputs.get(index),
                    totals.lineTotals().get(index), null));
        }
        lines.saveAll(saved);
        cashService.recordPosMovement(session, CashMovementType.POS_SALE, payment.cashAmount(), ticket.getId(),
                "Ticket " + number);
        stockNotifier.ticketsChanged(companyId);
        return response(companyId, ticket, saved, register, session);
    }

    @Transactional
    public TicketResponse voidTicket(UUID ticketId, VoidTicketRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        PosTicket ticket = tickets.findForUpdate(ticketId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        if (!ticket.isSale()) {
            throw new BusinessRuleException("Una devolución no se anula.");
        }
        if (ticket.getStatus() == PosTicketStatus.VOIDED) {
            throw new BusinessRuleException("El ticket " + ticket.getTicketNumber() + " ya está anulado.");
        }
        if (tickets.existsByCompanyIdAndOriginalTicketId(companyId, ticketId)) {
            throw new BusinessRuleException("El ticket " + ticket.getTicketNumber()
                    + " tiene devoluciones: ya no se puede anular.");
        }
        CashSession current = sessions.findByIdAndCompanyId(ticket.getCashSessionId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja", ticket.getCashSessionId()));
        if (current.getStatus() != CashSessionStatus.OPEN) {
            throw new BusinessRuleException("La caja de este ticket ya se cerró. Para deshacer la venta haz una "
                    + "devolución.");
        }
        CashSession session = lockOpenSession(companyId, ticket.getCashSessionId());
        cashService.recordPosMovement(session, CashMovementType.POS_REFUND, ticket.getCashAmount(), ticket.getId(),
                "Anulación del ticket " + ticket.getTicketNumber());
        ticket.voidTicket(userProvider.requireUserId(), Instant.now(), request.reason().trim());
        stockNotifier.ticketsChanged(companyId);
        return response(companyId, ticket);
    }

    @Transactional
    public TicketResponse refund(UUID originalId, RefundRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        if (request.clientReference() != null) {
            var existing = tickets.findByCompanyIdAndClientReference(companyId, request.clientReference());
            if (existing.isPresent()) {
                return response(companyId, existing.get());
            }
        }
        if (request.paymentMethod() == PosPaymentMethod.MIXED) {
            throw new BusinessRuleException("Una devolución se paga en efectivo o a la tarjeta, no de forma mixta.");
        }
        PosTicket original = tickets.findForUpdate(originalId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", originalId));
        if (!original.isSale() || original.getStatus() != PosTicketStatus.COMPLETED) {
            throw new BusinessRuleException("Solo se devuelven artículos de una venta vigente.");
        }
        Map<UUID, PosTicketLine> originalLines = lines.findAllByCompanyIdAndTicketIdOrderByLineNumberAsc(companyId,
                originalId).stream().collect(Collectors.toMap(PosTicketLine::getId, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        List<PosTicketLine> sources = new ArrayList<>();
        List<BigDecimal> quantities = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        List<BigDecimal> rates = new ArrayList<>();
        for (RefundLineRequest requested : request.lines()) {
            PosTicketLine source = originalLines.get(requested.lineId());
            if (source == null) {
                throw new BusinessRuleException("La línea indicada no pertenece al ticket " + original.getTicketNumber() + ".");
            }
            if (!seen.add(source.getId())) {
                throw new BusinessRuleException("La línea de " + source.getProductCode() + " aparece dos veces.");
            }
            BigDecimal quantity = requested.quantity();
            if (quantity.compareTo(source.refundableQuantity()) > 0) {
                throw new BusinessRuleException("De " + source.getProductCode() + " solo quedan "
                        + source.refundableQuantity().stripTrailingZeros().toPlainString() + " por devolver.");
            }
            sources.add(source);
            quantities.add(quantity);
            amounts.add(source.refundAmountFor(quantity));
            rates.add(source.getTaxRate());
        }
        Totals totals = PosCalculator.refundTotals(amounts, rates);
        BigDecimal total = totals.totalAmount();
        Payment payment = request.paymentMethod() == PosPaymentMethod.CASH
                ? new Payment(PosPaymentMethod.CASH, total, BigDecimal.ZERO, null, BigDecimal.ZERO)
                : new Payment(PosPaymentMethod.CARD, BigDecimal.ZERO, total, null, BigDecimal.ZERO);

        CashSession session = lockOpenSession(companyId, request.sessionId());
        CashRegister register = requireRegister(companyId, session.getCashRegisterId());
        Instant now = Instant.now();
        String number = nextNumber(companyId, register, PosTicketKind.REFUND, now);
        PosTicket ticket = tickets.save(new PosTicket(companyId, register.getId(), session.getId(), number,
                PosTicketKind.REFUND, now, userProvider.requireUserId(), userProvider.displayName(),
                original.getCustomerName(), original.getCustomerTaxId(), original, BigDecimal.ZERO, totals, payment,
                request.reason().trim(), request.clientReference()));
        List<PosTicketLine> saved = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            PosTicketLine source = sources.get(index);
            LineInput input = new LineInput(source.getProductId(), source.getProductCode(), source.getProductName(),
                    source.getBarcode(), source.getUnitOfMeasure(), quantities.get(index), source.getUnitPrice(),
                    source.isPriceOverridden(), source.getDiscountPercent(), source.getTaxRate());
            saved.add(new PosTicketLine(companyId, ticket.getId(), index + 1, input, amounts.get(index),
                    source.getId()));
            source.registerRefund(quantities.get(index), amounts.get(index));
        }
        lines.saveAll(saved);
        cashService.recordPosMovement(session, CashMovementType.POS_REFUND, payment.cashAmount(), ticket.getId(),
                "Devolución " + number + " del ticket " + original.getTicketNumber());
        stockNotifier.ticketsChanged(companyId);
        return response(companyId, ticket, saved, register, session);
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public TicketResponse find(UUID ticketId) {
        UUID companyId = companyProvider.requireCompanyId();
        return response(companyId, tickets.findByIdAndCompanyId(ticketId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId)));
    }

    /** Busca por número; es lo que lee el escáner del código de barras impreso en el ticket. */
    @Transactional(readOnly = true)
    public TicketResponse findByNumber(String number) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalized = number == null ? "" : number.trim();
        return response(companyId, tickets.findByCompanyIdAndTicketNumberIgnoreCase(companyId, normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", normalized)));
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> search(UUID sessionId, UUID registerId, PosTicketKind kind, LocalDate from,
                                       LocalDate to, String query, String zone, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        ZoneId zoneId = zone(zone);
        Instant fromInstant = from == null ? null : from.atStartOfDay(zoneId).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(zoneId).toInstant();
        String normalized = text(query);
        Page<PosTicket> page = tickets.search(companyId, sessionId != null, sessionId, registerId != null, registerId,
                kind != null, kind, fromInstant != null, fromInstant, toInstant != null, toInstant,
                normalized != null, normalized == null ? "" : normalized, pageable);
        Map<UUID, List<PosTicketLine>> byTicket = linesByTicket(companyId, page.getContent());
        Map<UUID, CashRegister> registerCache = new HashMap<>();
        Map<UUID, CashSession> sessionCache = new HashMap<>();
        Set<UUID> refunded = new HashSet<>();
        List<TicketResponse> content = page.getContent().stream().map(ticket -> response(companyId, ticket,
                byTicket.getOrDefault(ticket.getId(), List.of()),
                registerCache.computeIfAbsent(ticket.getCashRegisterId(), id -> requireRegister(companyId, id)),
                sessionCache.computeIfAbsent(ticket.getCashSessionId(), id -> sessions.findByIdAndCompanyId(id,
                        companyId).orElse(null)))).toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PosStatsResponse stats(LocalDate from, LocalDate to, String zone) {
        if (from == null || to == null) {
            throw new BusinessRuleException("Indica el periodo que quieres consultar.");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("El final del periodo no puede ser anterior al inicio.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= STATS_MAX_DAYS) {
            throw new BusinessRuleException("Consulta como mucho un año de ventas cada vez.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        ZoneId zoneId = zone(zone);
        Instant start = from.atStartOfDay(zoneId).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<PosTicket> periodTickets = tickets
                .findAllByCompanyIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThanOrderByIssuedAtAsc(companyId, start, end);
        Map<UUID, List<PosTicketLine>> byTicket = linesByTicket(companyId, periodTickets);
        PosFigures figures = figures(periodTickets, byTicket, zoneId);
        Map<UUID, CashRegister> registerCache = new HashMap<>();

        Map<LocalDate, BigDecimal[]> days = new TreeMap<>();
        Map<UUID, BigDecimal[]> byRegister = new LinkedHashMap<>();
        Map<UUID, BigDecimal[]> byCashier = new LinkedHashMap<>();
        Map<UUID, String> cashierNames = new HashMap<>();
        Map<UUID, BigDecimal[]> bySession = new HashMap<>();
        for (PosTicket ticket : periodTickets) {
            if (ticket.getStatus() == PosTicketStatus.VOIDED) {
                continue;
            }
            LocalDate day = LocalDate.ofInstant(ticket.getIssuedAt(), zoneId);
            accumulate(days.computeIfAbsent(day, key -> zeros()), ticket);
            accumulate(byRegister.computeIfAbsent(ticket.getCashRegisterId(), key -> zeros()), ticket);
            accumulate(byCashier.computeIfAbsent(ticket.getCashierId(), key -> zeros()), ticket);
            accumulate(bySession.computeIfAbsent(ticket.getCashSessionId(), key -> zeros()), ticket);
            cashierNames.put(ticket.getCashierId(), ticket.getCashierName());
        }
        List<DaySales> dayList = days.entrySet().stream().map(entry -> new DaySales(entry.getKey(),
                entry.getValue()[0], entry.getValue()[1], entry.getValue()[2].longValue())).toList();
        List<GroupSales> registerList = byRegister.entrySet().stream().map(entry -> {
            CashRegister register = registerCache.computeIfAbsent(entry.getKey(), id -> requireRegister(companyId, id));
            return group(register.getCode(), register.getName(), entry.getValue());
        }).sorted(Comparator.comparing(GroupSales::total).reversed()).toList();
        List<GroupSales> cashierList = byCashier.entrySet().stream()
                .map(entry -> group(entry.getKey().toString(), cashierNames.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(GroupSales::total).reversed()).toList();
        List<SessionLine> sessionLines = sessions.findForPeriod(companyId, start, end).stream().map(session -> {
            CashRegister register = registerCache.computeIfAbsent(session.getCashRegisterId(),
                    id -> requireRegister(companyId, id));
            BigDecimal[] values = bySession.getOrDefault(session.getId(), zeros());
            BigDecimal difference = session.getActualClosingAmount() == null ? null
                    : session.getActualClosingAmount().subtract(session.getExpectedClosingAmount());
            return new SessionLine(session.getId(), register.getCode(), register.getName(),
                    session.getStatus().name(), session.getOpenedAt(), session.getClosedAt(),
                    values[0].subtract(values[1]), values[2].longValue(), difference);
        }).toList();
        return new PosStatsResponse(from, to, figures, dayList, registerList, cashierList, sessionLines);
    }

    /** Tickets cambiados desde un momento, para el inventario. Lo llama operations-service. */
    @Transactional(readOnly = true)
    public List<PosStockSnapshot> stockSnapshots(UUID companyId, Instant updatedSince, int limit) {
        List<PosTicket> changed = tickets.findUpdatedSince(companyId, updatedSince, PageRequest.of(0, limit));
        Map<UUID, List<PosTicketLine>> byTicket = linesByTicket(companyId, changed);
        Map<UUID, CashRegister> registerCache = new HashMap<>();
        return changed.stream().map(ticket -> {
            CashRegister register = registerCache.computeIfAbsent(ticket.getCashRegisterId(),
                    id -> requireRegister(companyId, id));
            String customer = "TPV " + register.getCode()
                    + (ticket.getCustomerName() == null ? "" : " · " + ticket.getCustomerName());
            List<PosStockSnapshot.Line> stockLines = byTicket.getOrDefault(ticket.getId(), List.of()).stream()
                    .map(line -> new PosStockSnapshot.Line(line.getLineNumber(), line.getProductId(),
                            line.getProductCode(), line.getProductName(), line.getQuantity())).toList();
            return new PosStockSnapshot(ticket.getId(), ticket.getTicketNumber(),
                    ticket.isSale() ? "POS_TICKET" : "POS_RETURN",
                    ticket.getStatus() == PosTicketStatus.VOIDED ? "CANCELLED" : "COMPLETED",
                    LocalDate.ofInstant(ticket.getIssuedAt(), defaultZone), null,
                    customer.length() <= 180 ? customer : customer.substring(0, 180), ticket.getUpdatedAt(), stockLines);
        }).toList();
    }

    // ---------------------------------------------------------------- cálculo de cifras

    static PosFigures figures(List<PosTicket> ticketList, Map<UUID, List<PosTicketLine>> byTicket, ZoneId zone) {
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal cardSales = BigDecimal.ZERO;
        BigDecimal cashRefunds = BigDecimal.ZERO;
        BigDecimal cardRefunds = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        BigDecimal items = BigDecimal.ZERO;
        long saleCount = 0;
        long refundCount = 0;
        long voided = 0;
        List<BigDecimal> taxTotals = new ArrayList<>();
        List<BigDecimal> taxRates = new ArrayList<>();
        Map<UUID, Object[]> products = new LinkedHashMap<>();
        Map<Integer, BigDecimal[]> hours = new TreeMap<>();
        for (PosTicket ticket : ticketList) {
            if (ticket.getStatus() == PosTicketStatus.VOIDED) {
                voided++;
                continue;
            }
            boolean sale = ticket.isSale();
            List<PosTicketLine> ticketLines = byTicket.getOrDefault(ticket.getId(), List.of());
            if (sale) {
                sales = sales.add(ticket.getTotalAmount());
                cashSales = cashSales.add(ticket.getCashAmount());
                cardSales = cardSales.add(ticket.getCardAmount());
                discounts = discounts.add(ticket.getDiscountAmount());
                saleCount++;
                int hour = ticket.getIssuedAt().atZone(zone).getHour();
                BigDecimal[] slot = hours.computeIfAbsent(hour, key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                slot[0] = slot[0].add(ticket.getTotalAmount());
                slot[1] = slot[1].add(BigDecimal.ONE);
            } else {
                refunds = refunds.add(ticket.getTotalAmount());
                cashRefunds = cashRefunds.add(ticket.getCashAmount());
                cardRefunds = cardRefunds.add(ticket.getCardAmount());
                refundCount++;
            }
            for (PosTicketLine line : ticketLines) {
                BigDecimal signedTotal = sale ? line.getLineTotal() : line.getLineTotal().negate();
                BigDecimal signedQuantity = sale ? line.getQuantity() : line.getQuantity().negate();
                if (sale) {
                    items = items.add(line.getQuantity());
                }
                taxTotals.add(signedTotal);
                taxRates.add(line.getTaxRate());
                Object[] product = products.computeIfAbsent(line.getProductId(), key -> new Object[]{
                        line.getProductCode(), line.getProductName(), BigDecimal.ZERO, BigDecimal.ZERO});
                product[2] = ((BigDecimal) product[2]).add(signedQuantity);
                product[3] = ((BigDecimal) product[3]).add(signedTotal);
            }
        }
        List<ProductSales> top = products.entrySet().stream()
                .map(entry -> new ProductSales(entry.getKey(), (String) entry.getValue()[0],
                        (String) entry.getValue()[1], (BigDecimal) entry.getValue()[2],
                        (BigDecimal) entry.getValue()[3]))
                .filter(product -> product.total().signum() != 0 || product.quantity().signum() != 0)
                .sorted(Comparator.comparing(ProductSales::total).reversed()).limit(TOP_PRODUCTS).toList();
        List<HourSales> hourly = hours.entrySet().stream()
                .map(entry -> new HourSales(entry.getKey(), entry.getValue()[0], entry.getValue()[1].longValue()))
                .toList();
        BigDecimal average = saleCount == 0 ? BigDecimal.ZERO
                : sales.divide(BigDecimal.valueOf(saleCount), 2, RoundingMode.HALF_UP);
        return new PosFigures(sales, refunds, sales.subtract(refunds), cashSales, cardSales, cashRefunds, cardRefunds,
                discounts, saleCount, refundCount, voided, average, items,
                PosCalculator.taxBreakdown(taxTotals, taxRates), top, hourly);
    }

    // ---------------------------------------------------------------- utilidades

    private List<LineInput> resolveLines(List<SaleLineRequest> requested) {
        Map<UUID, ProductSnapshot> products = new HashMap<>();
        List<LineInput> inputs = new ArrayList<>();
        for (SaleLineRequest line : requested) {
            ProductSnapshot product = products.computeIfAbsent(line.productId(), productClient::findProduct);
            if (!product.active()) {
                throw new BusinessRuleException("El artículo " + product.code() + " está dado de baja y no se puede vender.");
            }
            BigDecimal taxRate = product.taxRate() == null ? BigDecimal.ZERO : product.taxRate();
            BigDecimal catalogPrice = PosCalculator.retailPrice(product.basePrice(), taxRate);
            BigDecimal price = line.unitPrice() == null ? catalogPrice : PosCalculator.money(line.unitPrice());
            inputs.add(new LineInput(product.id(), product.code(), product.name(), product.barcode(),
                    product.unitOfMeasure() == null ? "UNIT" : product.unitOfMeasure(), line.quantity(), price,
                    price.compareTo(catalogPrice) != 0, percent(line.discountPercent()), taxRate));
        }
        return inputs;
    }

    private CashSession lockOpenSession(UUID companyId, UUID sessionId) {
        CashSession session = cashService.lockOpenSession(companyId, sessionId);
        CashRegister register = requireRegister(companyId, session.getCashRegisterId());
        if (!register.isActive()) {
            throw new BusinessRuleException("La caja " + register.getCode() + " está inactiva.");
        }
        return session;
    }

    private String nextNumber(UUID companyId, CashRegister register, PosTicketKind kind, Instant at) {
        int year = at.atZone(defaultZone).getYear();
        PosTicketSequence sequence = sequences.findForUpdate(companyId, register.getId(), kind, year)
                .orElseGet(() -> sequences.saveAndFlush(new PosTicketSequence(companyId, register.getId(), kind, year)));
        long value = sequence.next();
        return (kind == PosTicketKind.SALE ? "T" : "D") + year + "-" + register.getCode() + "-"
                + String.format(Locale.ROOT, "%06d", value);
    }

    private OpenSessionHeader header(CashSession session) {
        List<PosTicket> sessionTickets = tickets.findAllByCompanyIdAndCashSessionIdOrderByIssuedAtAsc(
                session.getCompanyId(), session.getId());
        BigDecimal net = BigDecimal.ZERO;
        long count = 0;
        for (PosTicket ticket : sessionTickets) {
            if (ticket.getStatus() == PosTicketStatus.VOIDED) {
                continue;
            }
            net = ticket.isSale() ? net.add(ticket.getTotalAmount()) : net.subtract(ticket.getTotalAmount());
            if (ticket.isSale()) {
                count++;
            }
        }
        return new OpenSessionHeader(session.getId(), session.getOpenedAt(), session.getOpeningAmount(),
                cashService.balanceOf(session), net, count);
    }

    private TicketResponse response(UUID companyId, PosTicket ticket) {
        return response(companyId, ticket,
                lines.findAllByCompanyIdAndTicketIdOrderByLineNumberAsc(companyId, ticket.getId()),
                requireRegister(companyId, ticket.getCashRegisterId()),
                sessions.findByIdAndCompanyId(ticket.getCashSessionId(), companyId).orElse(null));
    }

    private TicketResponse response(UUID companyId, PosTicket ticket, List<PosTicketLine> ticketLines,
                                    CashRegister register, CashSession session) {
        boolean completedSale = ticket.isSale() && ticket.getStatus() == PosTicketStatus.COMPLETED;
        boolean untouched = ticketLines.stream().allMatch(line -> line.getRefundedQuantity().signum() == 0);
        boolean voidable = completedSale && untouched && session != null
                && session.getStatus() == CashSessionStatus.OPEN;
        boolean refundable = completedSale && ticketLines.stream()
                .anyMatch(line -> line.refundableQuantity().signum() > 0);
        List<BigDecimal> totals = ticketLines.stream().map(PosTicketLine::getLineTotal).toList();
        List<BigDecimal> rates = ticketLines.stream().map(PosTicketLine::getTaxRate).toList();
        return new TicketResponse(ticket.getId(), ticket.getTicketNumber(), ticket.getKind(), ticket.getStatus(),
                ticket.getIssuedAt(), register.getId(), register.getCode(), register.getName(),
                ticket.getCashSessionId(), ticket.getCashierName(), ticket.getCustomerName(),
                ticket.getCustomerTaxId(), ticket.getOriginalTicketId(), ticket.getOriginalTicketNumber(),
                ticket.getCurrencyCode(), ticket.getDiscountPercent(), ticket.getGrossAmount(),
                ticket.getDiscountAmount(), ticket.getTotalAmount(), ticket.getTaxBaseAmount(), ticket.getTaxAmount(),
                PosCalculator.taxBreakdown(totals, rates), ticket.getPaymentMethod(), ticket.getCashAmount(),
                ticket.getCardAmount(), ticket.getCashTendered(), ticket.getChangeAmount(), ticket.getNote(),
                ticket.getVoidedAt(), ticket.getVoidReason(), voidable, refundable,
                ticketLines.stream().map(TicketLineResponse::from).toList());
    }

    private Map<UUID, List<PosTicketLine>> linesByTicket(UUID companyId, Collection<PosTicket> ticketList) {
        List<UUID> ids = ticketList.stream().map(PosTicket::getId).toList();
        Map<UUID, List<PosTicketLine>> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += LINE_BATCH) {
            List<UUID> batch = ids.subList(start, Math.min(ids.size(), start + LINE_BATCH));
            for (PosTicketLine line : lines.findAllByCompanyIdAndTicketIdInOrderByLineNumberAsc(companyId, batch)) {
                result.computeIfAbsent(line.getTicketId(), key -> new ArrayList<>()).add(line);
            }
        }
        return result;
    }

    private CashRegister requireRegister(UUID companyId, UUID registerId) {
        return registers.findByIdAndCompanyId(registerId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Caja", registerId));
    }

    private ZoneId zone(String zone) {
        if (zone == null || zone.isBlank()) {
            return defaultZone;
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (DateTimeException exception) {
            throw new BusinessRuleException("La zona horaria indicada no es válida.");
        }
    }

    private static BigDecimal sum(List<CashMovement> movements, CashMovementType type) {
        return movements.stream().filter(movement -> movement.getType() == type).map(CashMovement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal[] zeros() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    /** [0] ventas, [1] devoluciones, [2] número de ventas. */
    private static void accumulate(BigDecimal[] values, PosTicket ticket) {
        if (ticket.isSale()) {
            values[0] = values[0].add(ticket.getTotalAmount());
            values[2] = values[2].add(BigDecimal.ONE);
        } else {
            values[1] = values[1].add(ticket.getTotalAmount());
        }
    }

    private static GroupSales group(String key, String label, BigDecimal[] values) {
        return new GroupSales(key, label, values[0].subtract(values[1]), values[1], values[2].longValue());
    }

    private static BigDecimal percent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String taxId(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT).replace(" ", "");
    }
}
