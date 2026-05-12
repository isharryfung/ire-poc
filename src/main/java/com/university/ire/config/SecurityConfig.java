package com.university.ire.config;

import com.university.ire.service.iam.IamAuthenticationProvider;
import java.util.Collection;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**", "/api/v1/health/**", "/api/v1/ingest/status").permitAll()
                    .requestMatchers("/api/v1/ingest").hasAnyRole("ADMIN", "REVIEWER")
                    .requestMatchers("/api/v1/reviews/**").hasAnyRole("ADMIN", "REVIEWER")
                    .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(IamAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object roles = jwt.getClaims().get("roles");
            if (roles instanceof Collection<?> roleCollection) {
                return roleCollection.stream()
                        .map(Object::toString)
                        .map(r -> (GrantedAuthority) () -> "ROLE_" + r)
                        .toList();
            }
            return Collections.emptyList();
        });
        return converter;
    }
}
