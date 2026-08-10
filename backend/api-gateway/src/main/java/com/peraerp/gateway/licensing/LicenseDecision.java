package com.peraerp.gateway.licensing;

import org.springframework.http.HttpStatus;

record LicenseDecision(boolean allowed, HttpStatus deniedStatus, String code) {
    static LicenseDecision allow(String code) {
        return new LicenseDecision(true, null, code);
    }

    static LicenseDecision deny(HttpStatus status, String code) {
        return new LicenseDecision(false, status, code);
    }
}
