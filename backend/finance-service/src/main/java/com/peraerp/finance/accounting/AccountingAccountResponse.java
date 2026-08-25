package com.peraerp.finance.accounting;

import java.util.UUID;

public record AccountingAccountResponse(UUID id, String code, String name, AccountKind kind, boolean systemDefined) {
    static AccountingAccountResponse from(AccountingAccount account) {
        return new AccountingAccountResponse(account.getId(), account.getCode(), account.getName(), account.getKind(),
                account.isSystemDefined());
    }
}
