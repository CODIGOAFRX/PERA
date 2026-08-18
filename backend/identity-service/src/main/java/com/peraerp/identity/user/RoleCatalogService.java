package com.peraerp.identity.user;

import com.peraerp.identity.access.RoleRepository;
import com.peraerp.identity.config.CurrentCompanyProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleCatalogService {

    private static final List<String> PROFILE_ORDER = List.of("OWNER", "ADMIN", "ECONOMY", "LOGISTICS", "CATALOG");

    private final RoleRepository roleRepository;
    private final CurrentCompanyProvider companyProvider;

    public RoleCatalogService(RoleRepository roleRepository, CurrentCompanyProvider companyProvider) {
        this.roleRepository = roleRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        boolean owner = companyProvider.hasRole("OWNER");
        return roleRepository.findAllByCompanyIdOrderByName(companyProvider.requireCompanyId()).stream()
                .filter(role -> owner || !role.getCode().equalsIgnoreCase("OWNER"))
                .sorted(java.util.Comparator.comparingInt(role -> {
                    int index = PROFILE_ORDER.indexOf(role.getCode().toUpperCase(java.util.Locale.ROOT));
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .map(RoleResponse::from)
                .toList();
    }
}
