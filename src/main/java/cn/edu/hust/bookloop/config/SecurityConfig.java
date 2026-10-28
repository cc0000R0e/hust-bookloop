package cn.edu.hust.bookloop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // The public prototype has no server-side write endpoints.
        // Leave CSRF enabled and deny every endpoint not explicitly allowed.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/prototype/**", "/error").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/", "/prototype/**").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // Real database-backed registration/login is the next implementation step.
        // Do not expose Spring Boot's generated demo account.
        return username -> { throw new UsernameNotFoundException("Account service is not implemented"); };
    }
}
