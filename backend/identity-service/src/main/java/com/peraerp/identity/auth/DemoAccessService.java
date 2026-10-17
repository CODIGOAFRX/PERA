package com.peraerp.identity.auth;

import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.Company;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.config.DemoProperties;
import com.peraerp.identity.config.JwtProperties;
import com.peraerp.identity.user.AppUser;
import com.peraerp.identity.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Sesión de visitante para la demo pública, sin contraseña.
 *
 * <p>El token lleva la marca {@code demo}: con ella la pasarela impide lo que podría salir de la
 * demo (correos, certificados, envíos a la AEAT) o dejarla inservible para el siguiente visitante
 * (usuarios, datos de la empresa).</p>
 */
@Service
public class DemoAccessService {

    private final DemoProperties properties;
    private final AppUserRepository userRepository;
    private final UserCompanyRepository membershipRepository;
    private final CompanyRepository companyRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public DemoAccessService(DemoProperties properties, AppUserRepository userRepository,
                             UserCompanyRepository membershipRepository, CompanyRepository companyRepository,
                             JwtService jwtService, JwtProperties jwtProperties) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.companyRepository = companyRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(readOnly = true)
    public LoginResponse enter() {
        // Sin demo activada el endpoint no existe: un 404 no da pistas de que se pueda encender.
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Company company = companyRepository.findByCodeIgnoreCase(properties.companyCode())
                .filter(Company::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "La demo no está preparada."));
        AppUser user = userRepository.findByUsernameIgnoreCase(properties.username())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "La demo no está preparada."));
        UserCompany membership = membershipRepository.findByUserIdAndCompanyId(user.getId(), company.getId())
                .filter(UserCompany::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "La demo no está preparada."));

        String token = jwtService.issue(user, membership, true);
        return new LoginResponse(token, "Bearer", jwtProperties.ttl().toSeconds(), false,
                List.of(new CompanyOption(company.getId(), company.getCode(), company.getName())));
    }
}
