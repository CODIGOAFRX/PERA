package com.peraerp.gateway.licensing;

import reactor.core.publisher.Mono;

interface LicensingClient {
    Mono<RemoteLicenseResponse> validate();
}
