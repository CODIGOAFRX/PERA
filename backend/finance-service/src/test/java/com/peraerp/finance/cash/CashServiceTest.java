package com.peraerp.finance.cash;

import com.peraerp.finance.cash.CashDtos.CashMovementRequest;
import com.peraerp.finance.cash.CashDtos.CashRegisterRequest;
import com.peraerp.finance.cash.CashDtos.CashRegisterResponse;
import com.peraerp.finance.cash.CashDtos.CashSessionResponse;
import com.peraerp.finance.cash.CashDtos.CloseCashSessionRequest;
import com.peraerp.finance.cash.CashDtos.OpenCashSessionRequest;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.config.CurrentUserProvider;
import com.peraerp.finance.receivable.DocumentDueDate;
import com.peraerp.finance.receivable.Receipt;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashServiceTest {

    @Mock CashRegisterRepository registers;
    @Mock CashSessionRepository sessions;
    @Mock CashMovementRepository movements;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock CurrentUserProvider userProvider;

    private final Map<UUID, CashRegister> registerById = new HashMap<>();
    private final Map<UUID, CashSession> sessionById = new HashMap<>();
    private final List<CashMovement> journal = new ArrayList<>();
    private CashService service;
    private UUID companyId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new CashService(registers, sessions, movements, companyProvider, userProvider);
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(userProvider.requireUserId()).thenReturn(userId);

        // Repositorios en memoria.
        when(registers.save(any(CashRegister.class))).thenAnswer(invocation -> {
            CashRegister register = invocation.getArgument(0);
            ReflectionTestUtils.setField(register, "id", UUID.randomUUID());
            registerById.put(register.getId(), register);
            return register;
        });
        when(registers.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(registerById.get(invocation.<UUID>getArgument(0))));
        when(registers.existsByCompanyIdAndCodeIgnoreCase(eq(companyId), any())).thenAnswer(invocation ->
                registerById.values().stream().anyMatch(register ->
                        register.getCode().equalsIgnoreCase(invocation.getArgument(1))));
        when(sessions.save(any(CashSession.class))).thenAnswer(invocation -> {
            CashSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
            sessionById.put(session.getId(), session);
            return session;
        });
        when(sessions.findForUpdate(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(sessionById.get(invocation.<UUID>getArgument(0))));
        when(sessions.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(sessionById.get(invocation.<UUID>getArgument(0))));
        when(sessions.existsByCompanyIdAndCashRegisterIdAndStatus(eq(companyId), any(), eq(CashSessionStatus.OPEN)))
                .thenAnswer(invocation -> openSession(invocation.getArgument(1)).isPresent());
        when(sessions.findFirstByCompanyIdAndCashRegisterIdAndStatus(eq(companyId), any(), eq(CashSessionStatus.OPEN)))
                .thenAnswer(invocation -> openSession(invocation.getArgument(1)));
        when(movements.save(any(CashMovement.class))).thenAnswer(invocation -> {
            journal.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(movements.findAllByCompanyIdAndCashSessionIdOrderByOccurredAtAscCreatedAtAsc(eq(companyId), any()))
                .thenAnswer(invocation -> journal.stream()
                        .filter(movement -> movement.getCashSessionId().equals(invocation.getArgument(1))).toList());
    }

    @Test
    void aRegisterHasOneOpenSessionAtATime() {
        CashRegisterResponse register = service.createRegister(new CashRegisterRequest("caja1", "Mostrador", null, null));
        assertThat(register.code()).isEqualTo("CAJA1");
        assertThatThrownBy(() -> service.createRegister(new CashRegisterRequest("CAJA1", "Otra", null, null)))
                .isInstanceOf(BusinessRuleException.class);

        CashSessionResponse session = service.openSession(new OpenCashSessionRequest(register.id(),
                new BigDecimal("100.00")));

        assertThat(session.status()).isEqualTo(CashSessionStatus.OPEN);
        assertThat(session.balance()).isEqualByComparingTo("100.00");
        assertThat(sessionById.get(session.id()).getOpenedBy()).isEqualTo(userId);
        assertThatThrownBy(() -> service.openSession(new OpenCashSessionRequest(register.id(), BigDecimal.ZERO)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ya tiene una sesión abierta");
        assertThatThrownBy(() -> service.updateRegister(register.id(),
                new CashRegisterRequest("CAJA1", "Mostrador", null, false)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theBalanceFollowsTheJournalAndCashCannotGoNegative() {
        CashRegisterResponse register = service.createRegister(new CashRegisterRequest("CAJA1", "Mostrador", null, null));
        CashSessionResponse session = service.openSession(new OpenCashSessionRequest(register.id(),
                new BigDecimal("100.00")));

        service.addMovement(session.id(), new CashMovementRequest(CashMovementType.INCOME, new BigDecimal("50.00"),
                " Venta de mostrador "));
        service.recordReceiptCollection(companyId, session.id(), receipt("30.00"));
        CashSessionResponse afterExpense = service.addMovement(session.id(),
                new CashMovementRequest(CashMovementType.EXPENSE, new BigDecimal("20.00"), "Material de oficina"));

        assertThat(afterExpense.balance()).isEqualByComparingTo("160.00");
        assertThat(afterExpense.movements()).extracting(movement -> movement.signedAmount().toPlainString())
                .containsExactly("50.00", "30.00", "-20.00");
        assertThat(afterExpense.movements().get(0).concept()).isEqualTo("Venta de mostrador");
        assertThat(afterExpense.movements().get(1).concept()).contains("REC-2026-000001").contains("Cliente Demo");
        assertThatThrownBy(() -> service.addMovement(session.id(),
                new CashMovementRequest(CashMovementType.WITHDRAWAL, new BigDecimal("160.01"), "Ingreso en banco")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("efectivo suficiente");
        assertThatThrownBy(() -> service.addMovement(session.id(),
                new CashMovementRequest(CashMovementType.SALE_COLLECTION, BigDecimal.ONE, "Cobro")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void closingRecordsTheCountAndTheDifferenceAndLocksTheSession() {
        CashRegisterResponse register = service.createRegister(new CashRegisterRequest("CAJA1", "Mostrador", null, null));
        CashSessionResponse session = service.openSession(new OpenCashSessionRequest(register.id(),
                new BigDecimal("100.00")));
        service.addMovement(session.id(), new CashMovementRequest(CashMovementType.INCOME, new BigDecimal("50.00"),
                "Venta"));

        CashSessionResponse closed = service.closeSession(session.id(),
                new CloseCashSessionRequest(new BigDecimal("148.50"), "Faltan 1,50"));

        assertThat(closed.status()).isEqualTo(CashSessionStatus.CLOSED);
        assertThat(closed.expectedClosingAmount()).isEqualByComparingTo("150.00");
        assertThat(closed.actualClosingAmount()).isEqualByComparingTo("148.50");
        assertThat(closed.difference()).isEqualByComparingTo("-1.50");
        assertThat(sessionById.get(session.id()).getClosedBy()).isEqualTo(userId);
        assertThatThrownBy(() -> service.addMovement(session.id(),
                new CashMovementRequest(CashMovementType.INCOME, BigDecimal.ONE, "Tarde")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cerrada");
        assertThatThrownBy(() -> service.recordReceiptCollection(companyId, session.id(), receipt("10.00")))
                .isInstanceOf(BusinessRuleException.class);
        // Cerrada la sesión, la caja puede abrir otra.
        assertThat(service.openSession(new OpenCashSessionRequest(register.id(), new BigDecimal("148.50"))).status())
                .isEqualTo(CashSessionStatus.OPEN);
    }

    private Optional<CashSession> openSession(UUID registerId) {
        return sessionById.values().stream().filter(session -> session.getCashRegisterId().equals(registerId)
                && session.getStatus() == CashSessionStatus.OPEN).findFirst();
    }

    private Receipt receipt(String amount) {
        DocumentDueDate dueDate = new DocumentDueDate(companyId, UUID.randomUUID(), 1, LocalDate.of(2026, 10, 31),
                new BigDecimal(amount));
        Receipt receipt = new Receipt(companyId, "REC-2026-000001", dueDate, UUID.randomUUID(), "C001",
                "Cliente Demo", "FAC-2026-000001", "EUR");
        ReflectionTestUtils.setField(receipt, "id", UUID.randomUUID());
        return receipt;
    }
}
