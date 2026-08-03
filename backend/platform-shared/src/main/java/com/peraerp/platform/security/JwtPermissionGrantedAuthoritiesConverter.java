package com.peraerp.platform.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Maps PERA's permissions and roles claims to Spring Security authorities. */
public final class JwtPermissionGrantedAuthoritiesConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    addAuthorities(authorities, jwt.getClaim("permissions"), "");
    addAuthorities(authorities, jwt.getClaim("roles"), "ROLE_");
    return authorities;
  }

  private void addAuthorities(Set<GrantedAuthority> target, Object claim, String prefix) {
    if (!(claim instanceof Collection<?> values)) {
      return;
    }
    values.stream()
        .map(String::valueOf)
        .filter(value -> !value.isBlank())
        .map(value -> new SimpleGrantedAuthority(prefix + value))
        .forEach(target::add);
  }
}
