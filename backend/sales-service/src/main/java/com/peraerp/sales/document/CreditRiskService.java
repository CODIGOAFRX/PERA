package com.peraerp.sales.document;

import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.masterdata.CustomerSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Customer credit risk: issued invoices pending collection plus the new document, against the
 * customer's credit limit and warning threshold, all in the company base currency.
 *
 * <p>A limit of zero with the WARN policy means "no credit control". With REQUIRE_CONFIRMATION or
 * BLOCK, a zero limit means no credit is granted (prepayment). Only owners and administrators may
 * continue past a BLOCK, and always with an explicit acknowledgement.</p>
 */
@Service
public class CreditRiskService {
    /** Documents that commit the company to the customer; quotes and rectifications are not checked. */
    static final Set<DocumentType> CHECKED_TYPES = Set.of(DocumentType.SALES_ORDER, DocumentType.DELIVERY_NOTE, DocumentType.INVOICE);

    public enum Level { OK, WARNING, OVER_LIMIT }
    public enum Policy { WARN, REQUIRE_CONFIRMATION, BLOCK }

    public record Assessment(Level level, Policy policy, BigDecimal creditLimit, BigDecimal warningThreshold,
                             BigDecimal outstanding, BigDecimal documentAmount, BigDecimal projected,
                             boolean canOverride) {
        /** The document can only be saved after the user explicitly acknowledges the risk. */
        public boolean requiresAcknowledgement() {
            return level == Level.OVER_LIMIT && (policy == Policy.REQUIRE_CONFIRMATION || (policy == Policy.BLOCK && canOverride));
        }
        public boolean blocked() { return level == Level.OVER_LIMIT && policy == Policy.BLOCK && !canOverride; }
    }

    private final CommercialDocumentRepository repository;
    private final CurrentCompanyProvider companyProvider;

    public CreditRiskService(CommercialDocumentRepository repository, CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.companyProvider = companyProvider;
    }

    public Assessment assess(UUID companyId, CustomerSnapshot customer, BigDecimal documentAmountBase) {
        Policy policy = policy(customer.riskPolicy());
        BigDecimal limit = positiveOrZero(customer.creditLimit());
        BigDecimal threshold = positiveOrZero(customer.riskWarningThreshold());
        BigDecimal amount = documentAmountBase == null ? BigDecimal.ZERO : documentAmountBase;
        BigDecimal outstanding = repository.sumOutstandingInvoices(companyId, customer.id());
        if (outstanding == null) outstanding = BigDecimal.ZERO;
        BigDecimal projected = outstanding.add(amount);
        boolean canOverride = companyProvider.hasAnyRole("OWNER", "ADMIN");

        boolean controlled = limit.signum() > 0 || policy != Policy.WARN;
        Level level = Level.OK;
        if (controlled && projected.compareTo(limit) > 0) level = Level.OVER_LIMIT;
        else if (threshold.signum() > 0 && projected.compareTo(threshold) > 0) level = Level.WARNING;
        return new Assessment(level, policy, limit, threshold, outstanding, amount, projected, canOverride);
    }

    /** Throws when the document must not be saved without a decision, or cannot be saved at all. */
    public void enforce(Assessment assessment, boolean acknowledged) {
        if (assessment.blocked()) {
            throw new CreditRiskException("El cliente supera su límite de crédito y tiene la venta bloqueada. "
                    + "Solo un propietario o administrador puede autorizarla.", assessment);
        }
        if (assessment.requiresAcknowledgement() && !acknowledged) {
            throw new CreditRiskException("El documento supera el límite de crédito del cliente. Confirma para continuar.", assessment);
        }
    }

    private static Policy policy(String value) {
        if (value == null) return Policy.WARN;
        try { return Policy.valueOf(value); } catch (IllegalArgumentException unknown) { return Policy.WARN; }
    }

    private static BigDecimal positiveOrZero(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
