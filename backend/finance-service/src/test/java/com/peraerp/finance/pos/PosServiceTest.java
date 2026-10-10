package com.peraerp.finance.pos;

import com.peraerp.finance.cash.CashMovementType;
import com.peraerp.finance.cash.CashRegister;
import com.peraerp.finance.cash.CashRegisterRepository;
import com.peraerp.finance.cash.CashService;
import com.peraerp.finance.cash.CashSession;
import com.peraerp.finance.cash.CashSessionRepository;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.config.CurrentUserProvider;
import com.peraerp.finance.pos.PosDtos.PosFigures;
import com.peraerp.finance.pos.PosDtos.RefundLineRequest;
import com.peraerp.finance.pos.PosDtos.RefundRequest;
import com.peraerp.finance.pos.PosDtos.SaleLineRequest;
import com.peraerp.finance.pos.PosDtos.SaleRequest;
import com.peraerp.finance.pos.PosDtos.TicketResponse;
import com.peraerp.finance.pos.PosDtos.VoidTicketRequest;
import com.peraerp.finance.pos.PosProductClient.ProductSnapshot;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    @Mock CashService cashService;
    @Mock CashRegisterRepository registers;
    @Mock CashSessionRepository sessions;
    @Mock PosTicketRepository tickets;
    @Mock PosTicketLineRepository lines;
    @Mock PosTicketSequenceRepository sequences;
    @Mock PosProductClient productClient;
    @Mock PosStockNotifier stockNotifier;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock CurrentUserProvider userProvider;

    private final Map<UUID, PosTicket> storedTickets = new HashMap<>();
    private final List<PosTicketLine> storedLines = new ArrayList<>();
    private final Map<String, PosTicketSequence> storedSequences = new HashMap<>();
    private final Map<UUID, ProductSnapshot> products = new HashMap<>();
    private PosService service;
    private UUID companyId;
    private CashRegister register;
    private CashSession session;
    private UUID water;
    private UUID bread;

    @BeforeEach
    void setUp() {
        service = new PosService(cashService, registers, sessions, tickets, lines, sequences, productClient,
                stockNotifier, companyProvider, userProvider, "Europe/Madrid");
        companyId = UUID.randomUUID();
        register = new CashRegister(companyId, "CAJA1", "Caja 1");
        ReflectionTestUtils.setField(register, "id", UUID.randomUUID());
        session = new CashSession(companyId, register.getId(), UUID.randomUUID(), Instant.now(), new BigDecimal("100"));
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        water = product("AGUA", "0.8264", "21", true);
        bread = product("PAN", "1.00", "4", true);

        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(userProvider.requireUserId()).thenReturn(UUID.randomUUID());
        when(userProvider.displayName()).thenReturn("Lucía · Caja");
        when(productClient.findProduct(any())).thenAnswer(invocation -> products.get(invocation.<UUID>getArgument(0)));
        when(registers.findByIdAndCompanyId(register.getId(), companyId)).thenReturn(Optional.of(register));
        when(sessions.findByIdAndCompanyId(session.getId(), companyId)).thenReturn(Optional.of(session));
        when(cashService.lockOpenSession(companyId, session.getId())).thenReturn(session);
        when(cashService.balanceOf(session)).thenReturn(new BigDecimal("100"));

        // Repositorios en memoria.
        when(tickets.save(any(PosTicket.class))).thenAnswer(invocation -> {
            PosTicket ticket = invocation.getArgument(0);
            if (ticket.getId() == null) ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
            storedTickets.put(ticket.getId(), ticket);
            return ticket;
        });
        when(tickets.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(storedTickets.get(invocation.<UUID>getArgument(0))));
        when(tickets.findForUpdate(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(storedTickets.get(invocation.<UUID>getArgument(0))));
        when(tickets.findByCompanyIdAndClientReference(eq(companyId), any())).thenAnswer(invocation ->
                storedTickets.values().stream().filter(ticket -> invocation.getArgument(1).equals(ticket.getClientReference()))
                        .findFirst());
        when(tickets.existsByCompanyIdAndOriginalTicketId(eq(companyId), any())).thenAnswer(invocation ->
                storedTickets.values().stream().anyMatch(ticket -> invocation.getArgument(1).equals(ticket.getOriginalTicketId())));
        when(lines.saveAll(anyList())).thenAnswer(invocation -> {
            List<PosTicketLine> saved = invocation.getArgument(0);
            saved.forEach(line -> ReflectionTestUtils.setField(line, "id", UUID.randomUUID()));
            storedLines.addAll(saved);
            return saved;
        });
        when(lines.findAllByCompanyIdAndTicketIdOrderByLineNumberAsc(eq(companyId), any())).thenAnswer(invocation ->
                storedLines.stream().filter(line -> line.getTicketId().equals(invocation.getArgument(1))).toList());
        when(sequences.findForUpdate(eq(companyId), any(), any(), anyInt())).thenAnswer(invocation ->
                Optional.ofNullable(storedSequences.get(invocation.getArgument(2) + "/" + invocation.getArgument(3))));
        when(sequences.saveAndFlush(any(PosTicketSequence.class))).thenAnswer(invocation -> {
            PosTicketSequence sequence = invocation.getArgument(0);
            storedSequences.put(ReflectionTestUtils.getField(sequence, "kind") + "/"
                    + ReflectionTestUtils.getField(sequence, "fiscalYear"), sequence);
            return sequence;
        });
    }

    @Test
    void aSaleChargesTheShopPriceNumbersTheTicketAndPutsOnlyTheCashPartInTheDrawer() {
        TicketResponse ticket = service.sell(sale(PosPaymentMethod.MIXED, new BigDecimal("2.00"), new BigDecimal("1.00"),
                null, new SaleLineRequest(water, new BigDecimal("2"), null, null)));

        int year = Instant.now().atZone(ZONE).getYear();
        assertThat(ticket.number()).isEqualTo("T" + year + "-CAJA1-000001");
        assertThat(ticket.lines().getFirst().unitPrice()).isEqualByComparingTo("1.00");
        assertThat(ticket.lines().getFirst().priceOverridden()).isFalse();
        assertThat(ticket.totalAmount()).isEqualByComparingTo("2.00");
        assertThat(ticket.cardAmount()).isEqualByComparingTo("1.00");
        assertThat(ticket.cashAmount()).isEqualByComparingTo("1.00");
        assertThat(ticket.changeAmount()).isEqualByComparingTo("1.00");
        assertThat(ticket.cashierName()).isEqualTo("Lucía · Caja");
        assertThat(ticket.voidable()).isTrue();
        verify(cashService).recordPosMovement(eq(session), eq(CashMovementType.POS_SALE),
                eq(new BigDecimal("1.00")), eq(ticket.id()), eq("Ticket " + ticket.number()));
        verify(stockNotifier).ticketsChanged(companyId);

        TicketResponse next = service.sell(sale(PosPaymentMethod.CARD, null, null, null,
                new SaleLineRequest(bread, BigDecimal.ONE, null, null)));
        assertThat(next.number()).isEqualTo("T" + year + "-CAJA1-000002");
    }

    @Test
    void retryingTheSameChargeReturnsTheTicketAlreadyCreated() {
        UUID reference = UUID.randomUUID();
        TicketResponse first = service.sell(sale(PosPaymentMethod.CASH, null, null, reference,
                new SaleLineRequest(water, BigDecimal.ONE, null, null)));
        TicketResponse retry = service.sell(sale(PosPaymentMethod.CASH, null, null, reference,
                new SaleLineRequest(water, BigDecimal.ONE, null, null)));

        assertThat(retry.id()).isEqualTo(first.id());
        verify(tickets, times(1)).save(any(PosTicket.class));
    }

    @Test
    void aManualPriceIsKeptAndMarkedAndADeactivatedProductCannotBeSold() {
        TicketResponse ticket = service.sell(sale(PosPaymentMethod.CARD, null, null, null,
                new SaleLineRequest(water, BigDecimal.ONE, new BigDecimal("0.80"), null)));
        assertThat(ticket.lines().getFirst().priceOverridden()).isTrue();
        assertThat(ticket.totalAmount()).isEqualByComparingTo("0.80");

        UUID retired = product("OLD", "5", "21", false);
        assertThatThrownBy(() -> service.sell(sale(PosPaymentMethod.CARD, null, null, null,
                new SaleLineRequest(retired, BigDecimal.ONE, null, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("dado de baja");
    }

    @Test
    void voidingGivesTheCashBackOnlyWhileTheSessionIsOpen() {
        TicketResponse ticket = service.sell(sale(PosPaymentMethod.CASH, new BigDecimal("5"), null, null,
                new SaleLineRequest(water, new BigDecimal("3"), null, null)));

        TicketResponse voided = service.voidTicket(ticket.id(), new VoidTicketRequest("Cobrado dos veces"));

        assertThat(voided.status()).isEqualTo(PosTicketStatus.VOIDED);
        assertThat(voided.voidReason()).isEqualTo("Cobrado dos veces");
        verify(cashService).recordPosMovement(eq(session), eq(CashMovementType.POS_REFUND),
                eq(new BigDecimal("3.00")), eq(ticket.id()), any());
        assertThatThrownBy(() -> service.voidTicket(ticket.id(), new VoidTicketRequest("Otra vez")))
                .isInstanceOf(BusinessRuleException.class);

        TicketResponse other = service.sell(sale(PosPaymentMethod.CARD, null, null, null,
                new SaleLineRequest(water, BigDecimal.ONE, null, null)));
        session.close(UUID.randomUUID(), Instant.now(), new BigDecimal("100"), new BigDecimal("100"), null);
        assertThatThrownBy(() -> service.voidTicket(other.id(), new VoidTicketRequest("Tarde")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("devolución");
    }

    @Test
    void refundsReturnTheChargedAmountPerUnitAndTheLastOneTakesWhatIsLeft() {
        // 3 × 1,10 = 3,30 con un 5 % de descuento de ticket = 3,14: no se reparte exacto entre tres.
        TicketResponse ticket = service.sell(new SaleRequest(session.getId(),
                List.of(new SaleLineRequest(water, new BigDecimal("3"), new BigDecimal("1.10"), null)),
                new BigDecimal("5"), PosPaymentMethod.CARD, null, null, null, null, null, null));
        UUID lineId = ticket.lines().getFirst().id();
        assertThat(ticket.totalAmount()).isEqualByComparingTo("3.14");

        TicketResponse first = service.refund(ticket.id(), refund(lineId, "1", PosPaymentMethod.CASH));
        TicketResponse second = service.refund(ticket.id(), refund(lineId, "2", PosPaymentMethod.CARD));

        assertThat(first.kind()).isEqualTo(PosTicketKind.REFUND);
        assertThat(first.number()).startsWith("D");
        assertThat(first.originalTicketNumber()).isEqualTo(ticket.number());
        assertThat(first.totalAmount()).isEqualByComparingTo("1.05");
        assertThat(second.totalAmount()).isEqualByComparingTo("2.09");
        verify(cashService).recordPosMovement(eq(session), eq(CashMovementType.POS_REFUND),
                eq(new BigDecimal("1.05")), eq(first.id()), any());
        assertThat(service.find(ticket.id()).refundable()).isFalse();
        assertThatThrownBy(() -> service.refund(ticket.id(), refund(lineId, "1", PosPaymentMethod.CASH)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.voidTicket(ticket.id(), new VoidTicketRequest("No")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("devoluciones");
    }

    @Test
    void aRefundCannotBeMixedNorReturnMoreThanWasSold() {
        TicketResponse ticket = service.sell(sale(PosPaymentMethod.CARD, null, null, null,
                new SaleLineRequest(bread, new BigDecimal("2"), null, null)));
        UUID lineId = ticket.lines().getFirst().id();

        assertThatThrownBy(() -> service.refund(ticket.id(), refund(lineId, "3", PosPaymentMethod.CASH)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("quedan 2");
        assertThatThrownBy(() -> service.refund(ticket.id(), refund(lineId, "1", PosPaymentMethod.MIXED)))
                .isInstanceOf(BusinessRuleException.class);
        verify(cashService, never()).recordPosMovement(any(), eq(CashMovementType.POS_REFUND), any(), any(), any());
    }

    @Test
    void theFiguresSubtractRefundsLeaveVoidedTicketsOutAndRankProducts() {
        TicketResponse sale = service.sell(sale(PosPaymentMethod.CASH, null, null, null,
                new SaleLineRequest(water, new BigDecimal("4"), null, null),
                new SaleLineRequest(bread, BigDecimal.ONE, null, null)));
        TicketResponse voided = service.sell(sale(PosPaymentMethod.CARD, null, null, null,
                new SaleLineRequest(bread, new BigDecimal("10"), null, null)));
        service.voidTicket(voided.id(), new VoidTicketRequest("Error"));
        service.refund(sale.id(), refund(sale.lines().getFirst().id(), "1", PosPaymentMethod.CASH));

        List<PosTicket> all = new ArrayList<>(storedTickets.values());
        Map<UUID, List<PosTicketLine>> byTicket = storedLines.stream()
                .collect(Collectors.groupingBy(PosTicketLine::getTicketId));
        PosFigures figures = PosService.figures(all, byTicket, ZONE);

        assertThat(figures.salesTotal()).isEqualByComparingTo("5.04");
        assertThat(figures.refundsTotal()).isEqualByComparingTo("1.00");
        assertThat(figures.netTotal()).isEqualByComparingTo("4.04");
        assertThat(figures.ticketCount()).isEqualTo(1);
        assertThat(figures.refundCount()).isEqualTo(1);
        assertThat(figures.voidedCount()).isEqualTo(1);
        assertThat(figures.itemsSold()).isEqualByComparingTo("5");
        assertThat(figures.topProducts().getFirst().productCode()).isEqualTo("AGUA");
        assertThat(figures.topProducts().getFirst().quantity()).isEqualByComparingTo("3");
        assertThat(figures.taxes().stream().map(PosCalculator.TaxLine::total).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("4.04");
    }

    private SaleRequest sale(PosPaymentMethod method, BigDecimal tendered, BigDecimal card, UUID reference,
                             SaleLineRequest... saleLines) {
        return new SaleRequest(session.getId(), List.of(saleLines), null, method, tendered, card, null, null, null,
                reference);
    }

    private RefundRequest refund(UUID lineId, String quantity, PosPaymentMethod method) {
        return new RefundRequest(session.getId(), List.of(new RefundLineRequest(lineId, new BigDecimal(quantity))),
                method, "Devolución del cliente", null);
    }

    private UUID product(String code, String basePrice, String taxRate, boolean active) {
        UUID id = UUID.randomUUID();
        products.put(id, new ProductSnapshot(id, code, "Artículo " + code, null, "UNIT", new BigDecimal(basePrice),
                new BigDecimal(taxRate), active));
        return id;
    }
}
