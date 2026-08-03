package com.peraerp.identity.auth;

import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.access.UserCompanyRepository;
import com.peraerp.identity.company.Company;
import com.peraerp.identity.company.CompanyRepository;
import com.peraerp.identity.config.JwtProperties;
import com.peraerp.identity.user.AppUser;
import com.peraerp.identity.user.AppUserRepository;
import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final UserCompanyRepository membershipRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(AppUserRepository userRepository, UserCompanyRepository membershipRepository,
                       CompanyRepository companyRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsernameIgnoreCase(request.username())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new AuthenticationFailedException("Usuario o contraseña incorrectos."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Usuario o contraseña incorrectos.");
        }

        List<UserCompany> memberships = membershipRepository.findAllByUserIdAndActiveTrue(user.getId());
        if (memberships.isEmpty()) {
            throw new AuthenticationFailedException("El usuario no tiene empresas activas asignadas.");
        }

        if (request.companyId() == null && memberships.size() > 1) {
            return LoginResponse.selectionRequired(companyOptions(memberships));
        }

        UserCompany selected = request.companyId() == null
                ? memberships.getFirst()
                : memberships.stream()
                .filter(membership -> membership.getCompanyId().equals(request.companyId()))
                .findFirst()
                .orElseThrow(() -> new AuthenticationFailedException("El usuario no puede acceder a la empresa indicada."));

        Company company = companyRepository.findById(selected.getCompanyId())
                .filter(Company::isActive)
                .orElseThrow(() -> new AuthenticationFailedException("La empresa seleccionada no está activa."));

        String token = jwtService.issue(user, selected);
        return new LoginResponse(token, "Bearer", jwtProperties.ttl().toSeconds(), false,
                List.of(new CompanyOption(company.getId(), company.getCode(), company.getName())));
    }

    private List<CompanyOption> companyOptions(List<UserCompany> memberships) {
        return companyRepository.findAllById(memberships.stream().map(UserCompany::getCompanyId).toList())
                .stream()
                .filter(Company::isActive)
                .map(company -> new CompanyOption(company.getId(), company.getCode(), company.getName()))
                .toList();
    }
}
