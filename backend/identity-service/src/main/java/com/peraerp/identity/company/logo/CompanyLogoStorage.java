package com.peraerp.identity.company.logo;

import java.util.UUID;

public interface CompanyLogoStorage {

    String store(UUID companyId, CompanyLogoMediaType mediaType, byte[] content);

    byte[] read(UUID companyId, String storageKey);

    void delete(UUID companyId, String storageKey);
}
