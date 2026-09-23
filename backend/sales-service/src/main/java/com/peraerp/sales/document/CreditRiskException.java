package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;

/** The document exceeds the customer's credit limit and needs an explicit decision, or is blocked. */
public class CreditRiskException extends BusinessRuleException {
    private final CreditRiskService.Assessment assessment;

    public CreditRiskException(String message, CreditRiskService.Assessment assessment) {
        super(message);
        this.assessment = assessment;
    }

    public CreditRiskService.Assessment assessment() { return assessment; }
}
