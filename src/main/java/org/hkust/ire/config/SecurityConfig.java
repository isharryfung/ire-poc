package org.hkust.ire.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Security configuration for the IRE system.
 *
 * <p>Configures role-based access control for REST API endpoints and JSP admin views.
 * All user credentials MUST be provided via environment variables in production.</p>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${ire.security.api-user.name:api-user}")
    private String apiUserName;

    @Value("${ire.security.api-user.password:}")
    private String apiUserPassword;

    @Value("${ire.security.admin.name:admin}")
    private String adminName;

    @Value("${ire.security.admin.password:}")
    private String adminPassword;

    @Value("${ire.security.reviewer.name:reviewer}")
    private String reviewerName;

    @Value("${ire.security.reviewer.password:}")
    private String reviewerPassword;

    @Value("${ire.security.disable-auth:false}")
    private boolean disableAuth;

    /**
     * Password encoder - BCrypt.
     *
     * @return BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .passwordEncoder(passwordEncoder())
                .withUser(apiUserName)
                    .password(passwordEncoder().encode(apiUserPassword))
                    .roles("API_USER")
                .and()
                .withUser(adminName)
                    .password(passwordEncoder().encode(adminPassword))
                    .roles("ADMIN", "API_USER")
                .and()
                .withUser(reviewerName)
                    .password(passwordEncoder().encode(reviewerPassword))
                    .roles("REVIEWER", "API_USER");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (disableAuth) {
            http
                .authorizeRequests()
                    .anyRequest().permitAll()
                .and()
                .csrf().disable();
            return;
        }

        http
            .authorizeRequests()
                .antMatchers("/api/v1/health").permitAll()
                .antMatchers("/actuator/health").permitAll()
                .antMatchers("/api/v1/**").hasRole("API_USER")
                .antMatchers("/admin/**").hasRole("ADMIN")
                .antMatchers("/review/**").hasAnyRole("ADMIN", "REVIEWER")
                .anyRequest().authenticated()
            .and()
            .httpBasic()
            .and()
            .formLogin()
                .loginPage("/login")
                .permitAll()
            .and()
            .logout()
                .permitAll()
            .and()
            // CSRF: disabled for REST /api/** endpoints, enabled for JSP form submissions
            .csrf()
                .ignoringAntMatchers("/api/v1/**");
    }
}
