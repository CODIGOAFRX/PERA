package com.peraerp.identity.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleCatalogController {

    private final RoleCatalogService service;

    public RoleCatalogController(RoleCatalogService service) {
        this.service = service;
    }

    @GetMapping
    List<RoleResponse> list() {
        return service.list();
    }
}
