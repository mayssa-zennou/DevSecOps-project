package com.mayssa.ordersaga.inventory.config;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
class SecurityConfig {

  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
          .requestMatchers("/actuator/health/**").permitAll()
          .requestMatchers(HttpMethod.GET, "/stock/**").hasAuthority("SCOPE_stock:read")
          .requestMatchers(HttpMethod.POST, "/stock/*/reservations")
              .access(AuthorizationManagers.allOf(
                  AuthorityAuthorizationManager.hasRole("admin"),
                  AuthorityAuthorizationManager.hasAuthority("SCOPE_stock:reserve")))
          .anyRequest().denyAll())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter())));
    return http.build();
  }

  private JwtAuthenticationConverter converter() {
    var scopes = new JwtGrantedAuthoritiesConverter();   // claim "scope" -> SCOPE_xxx
    var conv = new JwtAuthenticationConverter();
    conv.setJwtGrantedAuthoritiesConverter(jwt -> {
      var out = new ArrayList<GrantedAuthority>(scopes.convert(jwt));
      var realm = jwt.getClaimAsMap("realm_access");
      if (realm != null && realm.get("roles") instanceof Collection<?> roles)
        roles.forEach(r -> out.add(new SimpleGrantedAuthority("ROLE_" + r)));
      return out;
    });
    return conv;
  }
}