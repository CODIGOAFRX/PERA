package com.peraerp.sales.verifactu.chain;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.verifactu.domain.InvoiceChainHead;
import com.peraerp.sales.verifactu.domain.InvoiceChainHeadRepository;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.domain.VerifactuState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Encadenado de registros de facturación.
 *
 * <p>La cadena es lo que hace verificable el sistema: cada huella incorpora la anterior, así que
 * alterar una factura antigua invalida todo lo que vino después. Un hueco, una bifurcación o una
 * secuencia repetida son fallos graves y silenciosos, y estas pruebas existen para que dejen de
 * ser silenciosos.</p>
 *
 * <p>El bloqueo pesimista real ({@code SELECT ... FOR UPDATE}) no se puede ejercitar sin una base
 * de datos; aquí se prueba la lógica de encadenado. La concurrencia real necesita una prueba con
 * PostgreSQL, que el proyecto todavía no tiene.</p>
 */
class VerifactuChainServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 8, 19);
    /** 09:30 UTC son las 11:30 en Madrid con horario de verano. */
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-08-19T09:30:00Z"), ZoneOffset.UTC);

    private final Map<UUID, InvoiceChainHead> heads = new HashMap<>();
    private InvoiceChainHeadRepository chainHeads;
    private VerifactuRecordRepository records;
    private VerifactuChainService service;

    @BeforeEach
    void setUp() {
        heads.clear();
        chainHeads = mock(InvoiceChainHeadRepository.class);
        records = mock(VerifactuRecordRepository.class);
        service = new VerifactuChainService(chainHeads, records, FIXED);

        when(chainHeads.ensureHead(any(), any(), any())).thenAnswer(invocation -> {
            UUID companyId = invocation.getArgument(1);
            return heads.putIfAbsent(companyId, new InvoiceChainHead(companyId)) == null ? 1 : 0;
        });
        when(chainHeads.findByCompanyId(any()))
                .thenAnswer(invocation -> Optional.ofNullable(heads.get(invocation.<UUID>getArgument(0))));
        when(chainHeads.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // Hibernate asigna el identificador al persistir; aquí hay que simularlo, porque el
        // encadenado guarda en el puntero el id del último registro.
        when(records.save(any())).thenAnswer(invocation -> assignId(invocation.getArgument(0)));
    }

    private static VerifactuRecord assignId(VerifactuRecord record) {
        try {
            Field id = Class.forName("com.peraerp.platform.domain.AuditableEntity").getDeclaredField("id");
            id.setAccessible(true);
            if (id.get(record) == null) {
                id.set(record, UUID.randomUUID());
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo simular el identificador generado.", e);
        }
        return record;
    }

    private ChainedRecordRequest alta(String number) {
        return new ChainedRecordRequest(UUID.randomUUID(), VerifactuRecordType.ALTA, "B75777847", number,
                ISSUE_DATE, InvoiceKind.F1, null, new BigDecimal("4348.68"), new BigDecimal("25052.00"),
                MADRID, null);
    }

    // --- cadena ---

    @Test
    void firstRecordOfACompanyStartsTheChain() {
        VerifactuRecord record = service.append(COMPANY, alta("F-2026-0000015"));

        assertThat(record.getSequenceNumber()).isEqualTo(1L);
        assertThat(record.getPreviousFingerprint()).isNull();
        assertThat(record.getFingerprint()).matches("[0-9A-F]{64}");
        assertThat(record.getState()).isEqualTo(VerifactuState.PENDING);
    }

    @Test
    void everyRecordEmbedsTheFingerprintOfThePreviousOne() {
        VerifactuRecord first = service.append(COMPANY, alta("F-2026-0000015"));
        VerifactuRecord second = service.append(COMPANY, alta("F-2026-0000016"));

        assertThat(second.getSequenceNumber()).isEqualTo(2L);
        assertThat(second.getPreviousFingerprint()).isEqualTo(first.getFingerprint());
        assertThat(second.getFingerprint()).isNotEqualTo(first.getFingerprint());
    }

    @Test
    void chainHeadAdvancesToTheLastRecord() {
        service.append(COMPANY, alta("F-2026-0000015"));
        VerifactuRecord last = service.append(COMPANY, alta("F-2026-0000016"));

        InvoiceChainHead head = heads.get(COMPANY);
        assertThat(head.getLastFingerprint()).isEqualTo(last.getFingerprint());
        assertThat(head.getLastRecordId()).isEqualTo(last.getId());
        assertThat(head.getNextSequence()).isEqualTo(3L);
        assertThat(head.isEmpty()).isFalse();
    }

    @Test
    void eachCompanyKeepsItsOwnChain() {
        service.append(COMPANY, alta("F-2026-0000015"));

        UUID otherCompany = UUID.randomUUID();
        VerifactuRecord other = service.append(otherCompany, alta("A-2026-0000001"));

        assertThat(other.getSequenceNumber()).isEqualTo(1L);
        assertThat(other.getPreviousFingerprint()).isNull();
    }

    // --- coherencia temporal ---

    @Test
    void recordCannotBeGeneratedBeforeTheInvoiceWasIssued() {
        ChainedRecordRequest impossible = new ChainedRecordRequest(UUID.randomUUID(),
                VerifactuRecordType.ALTA, "B75777847", "F-2026-0000015", LocalDate.of(2026, 8, 25),
                InvoiceKind.F1, null, BigDecimal.ZERO, BigDecimal.TEN, MADRID, null);

        assertThatThrownBy(() -> service.append(COMPANY, impossible))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anterior a la fecha de expedición");
    }

    @Test
    void chainRejectsTimeGoingBackwards() {
        service.append(COMPANY, alta("F-2026-0000015"));

        // Un reloj que retrocede —un ajuste de NTP mal dado, por ejemplo— no puede colar un
        // registro fechado antes que el anterior de la cadena.
        VerifactuChainService rewound = new VerifactuChainService(chainHeads, records,
                Clock.fixed(Instant.parse("2026-08-19T07:30:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> rewound.append(COMPANY, alta("F-2026-0000016")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("registro previo");
    }

    @Test
    void twoRecordsStampedInTheSameInstantStillChain() {
        VerifactuRecord first = service.append(COMPANY, alta("F-2026-0000015"));
        VerifactuRecord second = service.append(COMPANY, alta("F-2026-0000016"));

        // El reloj está fijo: ambos registros comparten instante. Es lo que ocurre cuando dos
        // facturas se expiden a la vez, y no puede rechazarse.
        assertThat(second.getGeneratedAt()).isEqualTo(first.getGeneratedAt());
        assertThat(second.getPreviousFingerprint()).isEqualTo(first.getFingerprint());
        assertThat(second.getFingerprint()).isNotEqualTo(first.getFingerprint());
    }

    @Test
    void theRecordIsStampedInTheCompanyTimeZone() {
        VerifactuRecord record = service.append(COMPANY, alta("F-2026-0000015"));

        assertThat(record.getGeneratedAt().atZone(MADRID).getHour()).isEqualTo(11);
        assertThat(record.getGeneratedAt().atZone(MADRID).getOffset().getTotalSeconds()).isEqualTo(7200);
    }

    // --- validaciones de contenido ---

    @Test
    void altaRequiresTheInvoiceKind() {
        ChainedRecordRequest withoutKind = new ChainedRecordRequest(UUID.randomUUID(),
                VerifactuRecordType.ALTA, "B75777847", "F-9", ISSUE_DATE, null, null,
                BigDecimal.ZERO, BigDecimal.TEN, MADRID, null);

        assertThatThrownBy(() -> service.append(COMPANY, withoutKind))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("F1 a R5");
    }

    @Test
    void issuerTaxIdIsMandatory() {
        ChainedRecordRequest withoutIssuer = new ChainedRecordRequest(UUID.randomUUID(),
                VerifactuRecordType.ALTA, "   ", "F-9", ISSUE_DATE, InvoiceKind.F1, null,
                BigDecimal.ZERO, BigDecimal.TEN, MADRID, null);

        assertThatThrownBy(() -> service.append(COMPANY, withoutIssuer))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("NIF");
    }

    @Test
    void invoiceNumberIsMandatory() {
        ChainedRecordRequest withoutNumber = new ChainedRecordRequest(UUID.randomUUID(),
                VerifactuRecordType.ALTA, "B75777847", "", ISSUE_DATE, InvoiceKind.F1, null,
                BigDecimal.ZERO, BigDecimal.TEN, MADRID, null);

        assertThatThrownBy(() -> service.append(COMPANY, withoutNumber))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("número");
    }

    // --- anulación ---

    @Test
    void anulacionChainsWithoutAnInvoiceKind() {
        VerifactuRecord alta = service.append(COMPANY, alta("F-2026-0000015"));

        ChainedRecordRequest cancellation = new ChainedRecordRequest(UUID.randomUUID(),
                VerifactuRecordType.ANULACION, "B75777847", "F-2026-0000015", ISSUE_DATE, null, null,
                null, null, MADRID, null);
        VerifactuRecord record = service.append(COMPANY, cancellation);

        assertThat(record.getInvoiceKind()).isNull();
        assertThat(record.getRecordType()).isEqualTo(VerifactuRecordType.ANULACION);
        assertThat(record.getPreviousFingerprint()).isEqualTo(alta.getFingerprint());
    }

    // --- ciclo de vida frente a la AEAT ---

    @Test
    void sendingARecordCountsTheAttempt() {
        VerifactuRecord record = service.append(COMPANY, alta("F-2026-0000015"));

        record.markSent(Instant.now());

        assertThat(record.getState()).isEqualTo(VerifactuState.SENT);
        assertThat(record.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void acceptedResponseStoresTheCsv() {
        VerifactuRecord record = service.append(COMPANY, alta("F-2026-0000015"));

        record.applyResponse(VerifactuState.ACCEPTED, "CSV-123", "{}", Instant.now());

        assertThat(record.getState()).isEqualTo(VerifactuState.ACCEPTED);
        assertThat(record.getAeatCsv()).isEqualTo("CSV-123");
    }

    @Test
    void aResponseCannotLeaveTheRecordPending() {
        VerifactuRecord record = service.append(COMPANY, alta("F-2026-0000015"));

        assertThatThrownBy(() -> record.applyResponse(VerifactuState.PENDING, null, null, Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyUnresolvedStatesRequireAttention() {
        assertThat(VerifactuState.ACCEPTED.requiresAttention()).isFalse();
        assertThat(VerifactuState.PENDING.requiresAttention()).isTrue();
        assertThat(VerifactuState.SENT.requiresAttention()).isTrue();
        assertThat(VerifactuState.ACCEPTED_WITH_ERRORS.requiresAttention()).isTrue();
        assertThat(VerifactuState.REJECTED.requiresAttention()).isTrue();
    }
}
