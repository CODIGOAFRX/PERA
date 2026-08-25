package com.peraerp.finance.accounting;

import java.util.List;
import java.util.UUID;

public interface SalesInvoiceClient {
    List<SalesInvoiceSnapshot> findInvoices(UUID companyId);
}
