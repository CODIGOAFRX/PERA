package com.peraerp.identity.auth;

import com.peraerp.platform.domain.AuthenticationFailedException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter limiter;
    private final DemoAccessService demoAccessService;

    public AuthController(AuthService authService, LoginAttemptLimiter limiter, DemoAccessService demoAccessService) {
        this.authService = authService;
        this.limiter = limiter;
        this.demoAccessService = demoAccessService;
    }

    /** Entrada sin contraseña a la demo pública; responde 404 si la demo no está activada. */
    @PostMapping("/demo")
    ResponseEntity<LoginResponse> demo() {
        return ResponseEntity.ok(demoAccessService.enter());
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (limiter.isBlocked(request.username())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            LoginResponse response = authService.login(request);
            limiter.recordSuccess(request.username());
            return ResponseEntity.ok(response);
        } catch (AuthenticationFailedException exception) {
            limiter.recordFailure(request.username());
            throw exception;
        }
    }
}
