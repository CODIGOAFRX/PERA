package com.peraerp.identity.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdministrationController {

    private final UserAdministrationService service;

    public UserAdministrationController(UserAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    List<UserResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    @PutMapping("/{userId}")
    UserResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(userId, request);
    }
}
