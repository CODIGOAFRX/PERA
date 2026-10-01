package com.peraerp.finance.cash;

import com.peraerp.finance.cash.CashDtos.CashMovementRequest;
import com.peraerp.finance.cash.CashDtos.CashMovementResponse;
import com.peraerp.finance.cash.CashDtos.CashRegisterRequest;
import com.peraerp.finance.cash.CashDtos.CashRegisterResponse;
import com.peraerp.finance.cash.CashDtos.CashSessionResponse;
import com.peraerp.finance.cash.CashDtos.CloseCashSessionRequest;
import com.peraerp.finance.cash.CashDtos.OpenCashSessionRequest;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.config.CurrentUserProvider;
import com.peraerp.finance.receivable.Receipt;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CashService {

    private final CashRegisterRepository registers;
    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final CurrentCompanyProvider companyProvider;
    private final CurrentUserProvider userProvider;

    public CashService(CashRegisterRepository registers, CashSessionRepository sessions,
                       CashMovementRepository movements, CurrentCompanyProvider companyProvider,
                       CurrentUserProvider userProvider) {
        this.registers = registers;
        this.sessions = sessions;
        this.movements = movements;
        this.companyProvider = companyProvider;
        this.userProvider = userProvider;
    }

    @Transactional(readOnly = true)
    public List<CashRegisterResponse> findRegisters() {
        UUID companyId = companyProvider.requireCompanyId();
        return registers.findAllByCompanyIdOrderByCodeAsc(companyId).stream().map(this::response).toList();
    }

    @Transactional
    public CashRegisterResponse createRegister(CashRegisterRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (registers.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe una caja con el código " + code + ".");
        }
        CashRegister register = new CashRegister(companyId, code, request.name().trim());
        register.update(request.name().trim(), normalize(request.ownerName()),
                request.active() == null || request.active());
        return response(registers.save(register));
    }

    @Transactional
    public CashRegisterResponse updateRegister(UUID id, CashRegisterRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        CashRegister register = requireRegister(id, companyId);
        if (!register.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código de una caja no se puede modificar.");
        }
        boolean active = request.active() == null || request.active();
        if (!active && sessions.existsByCompanyIdAndCashRegisterIdAndStatus(companyId, id, CashSessionStatus.OPEN)) {
            throw new BusinessRuleException("Cierra la sesión abierta antes de desactivar la caja.");
        }
        register.update(request.name().trim(), normalize(request.ownerName()), active);
        return response(register);
    }

    @Transactional
    public CashSessionResponse openSession(OpenCashSessionRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        CashRegister register = requireRegister(request.cashRegisterId(), companyId);
        if (!register.isActive()) {
            throw new BusinessRuleException("La caja " + register.getCode() + " está inactiva.");
        }
        if (sessions.existsByCompanyIdAndCashRegisterIdAndStatus(companyId, register.getId(),
                CashSessionStatus.OPEN)) {
            throw new BusinessRuleException("La caja " + register.getCode() + " ya tiene una sesión abierta.");
        }
        CashSession session = sessions.save(new CashSession(companyId, register.getId(),
                userProvider.requireUserId(), Instant.now(), request.openingAmount()));
        return response(session, List.of());
    }

    @Transactional(readOnly = true)
    public Page<CashSessionResponse> searchSessions(UUID cashRegisterId, CashSessionStatus status,
                                                    Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        return sessions.search(companyId, cashRegisterId != null, cashRegisterId, status != null, status, pageable)
                .map(session -> response(session, movementsOf(session)));
    }

    @Transactional(readOnly = true)
    public CashSessionResponse findSession(UUID id) {
        CashSession session = sessions.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja", id));
        return response(session, movementsOf(session));
    }

    @Transactional
    public CashSessionResponse addMovement(UUID sessionId, CashMovementRequest request) {
        if (!request.type().isManual()) {
            throw new BusinessRuleException("Solo se pueden registrar a mano ingresos, gastos y retiradas.");
        }
        CashSession session = requireOpenSession(sessionId, companyProvider.requireCompanyId());
        List<CashMovement> existing = movementsOf(session);
        if (!request.type().isInflow() && balance(session, existing).compareTo(request.amount()) < 0) {
            throw new BusinessRuleException("No hay efectivo suficiente en la caja para esa salida.");
        }
        movements.save(new CashMovement(session.getCompanyId(), session.getId(), Instant.now(), request.type(),
                request.amount(), null, null, request.concept().trim()));
        return response(session, movementsOf(session));
    }

    /** Anota en el diario de caja el cobro en efectivo de un recibo, dentro de la transacción del cobro. */
    @Transactional
    public void recordReceiptCollection(UUID companyId, UUID sessionId, Receipt receipt) {
        CashSession session = requireOpenSession(sessionId, companyId);
        movements.save(new CashMovement(companyId, session.getId(), Instant.now(), CashMovementType.SALE_COLLECTION,
                receipt.getAmount(), receipt.getDocumentId(), receipt.getId(),
                "Cobro del recibo " + receipt.getReceiptNumber() + " · " + receipt.getCustomerNameSnapshot()));
    }

    @Transactional
    public CashSessionResponse closeSession(UUID sessionId, CloseCashSessionRequest request) {
        CashSession session = requireOpenSession(sessionId, companyProvider.requireCompanyId());
        List<CashMovement> existing = movementsOf(session);
        session.close(userProvider.requireUserId(), Instant.now(), balance(session, existing),
                request.countedAmount(), normalize(request.note()));
        return response(session, existing);
    }

    private CashSession requireOpenSession(UUID sessionId, UUID companyId) {
        CashSession session = sessions.findForUpdate(sessionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión de caja", sessionId));
        try {
            session.requireOpen();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return session;
    }

    private CashRegister requireRegister(UUID id, UUID companyId) {
        return registers.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Caja", id));
    }

    private List<CashMovement> movementsOf(CashSession session) {
        return movements.findAllByCompanyIdAndCashSessionIdOrderByOccurredAtAscCreatedAtAsc(
                session.getCompanyId(), session.getId());
    }

    private static BigDecimal balance(CashSession session, List<CashMovement> sessionMovements) {
        return sessionMovements.stream().map(CashMovement::signedAmount)
                .reduce(session.getOpeningAmount(), BigDecimal::add);
    }

    private CashRegisterResponse response(CashRegister register) {
        UUID openSessionId = sessions.findFirstByCompanyIdAndCashRegisterIdAndStatus(register.getCompanyId(),
                register.getId(), CashSessionStatus.OPEN).map(CashSession::getId).orElse(null);
        return new CashRegisterResponse(register.getId(), register.getCode(), register.getName(),
                register.getOwnerName(), register.isActive(), openSessionId);
    }

    private CashSessionResponse response(CashSession session, List<CashMovement> sessionMovements) {
        BigDecimal difference = session.getActualClosingAmount() == null ? null
                : session.getActualClosingAmount().subtract(session.getExpectedClosingAmount());
        return new CashSessionResponse(session.getId(), session.getCashRegisterId(), session.getStatus(),
                session.getOpenedAt(), session.getClosedAt(), session.getOpeningAmount(),
                balance(session, sessionMovements), session.getExpectedClosingAmount(),
                session.getActualClosingAmount(), difference, session.getClosingNote(),
                sessionMovements.stream().map(CashMovementResponse::from).toList());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
