package com.peraerp.identity.auth;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        boolean companySelectionRequired,
        List<CompanyOption> companies
) {
    public static LoginResponse selectionRequired(List<CompanyOption> companies) {
        return new LoginResponse(null, null, 0, true, companies);
    }
}
