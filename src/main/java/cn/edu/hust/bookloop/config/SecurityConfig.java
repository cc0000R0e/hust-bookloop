package cn.edu.hust.bookloop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import cn.edu.hust.bookloop.account.UserRepository;
import cn.edu.hust.bookloop.account.AccountService;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var requestCache = new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        requestCache.setRequestMatcher(request -> "GET".equals(request.getMethod())
                && (request.getServletPath().equals("/account") || request.getServletPath().equals("/books/new")
                    || request.getServletPath().equals("/my/listings")
                    || request.getServletPath().startsWith("/admin/")
                    || request.getServletPath().matches("/books/[^/]+/contact")
                    || request.getServletPath().matches("/books/[^/]+/edit")
                    || request.getServletPath().matches("/books/[^/]+/report")));
        http.requestCache(cache -> cache.requestCache(requestCache))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/books/new").authenticated()
                .requestMatchers(HttpMethod.HEAD, "/books/new").authenticated()
                .requestMatchers(HttpMethod.GET, "/my/listings", "/books/*/edit", "/books/*/report").authenticated()
                .requestMatchers(HttpMethod.HEAD, "/my/listings", "/books/*/edit").authenticated()
                .requestMatchers(HttpMethod.POST, "/books", "/books/preview", "/books/*", "/books/*/status",
                        "/books/*/reports").authenticated()
                .requestMatchers(HttpMethod.GET, "/books/*/images/*").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/books/*/images/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/books/*/contact").authenticated()
                .requestMatchers(HttpMethod.HEAD, "/books/*/contact").authenticated()
                .requestMatchers(HttpMethod.GET, "/books/*").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/books/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/login", "/register", "/market", "/css/**", "/js/**", "/prototype/**", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/register").permitAll()
                .requestMatchers("/account", "/account/contact", "/account/password").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.HEAD, "/", "/market", "/css/**", "/js/**", "/prototype/**").permitAll()
                .anyRequest().denyAll())
            .formLogin(login -> login.loginPage("/login")
                .defaultSuccessUrl("/account", false).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    if (request.getServletPath().equals("/books") || request.getServletPath().equals("/books/new") || request.getServletPath().equals("/books/preview")
                            || request.getServletPath().equals("/account") || request.getServletPath().startsWith("/account/")
                            || request.getServletPath().equals("/my/listings")
                            || request.getServletPath().startsWith("/admin/")
                            || request.getServletPath().matches("/books/[^/]+/contact")
                            || request.getServletPath().matches("/books/[^/]+/edit")
                            || request.getServletPath().matches("/books/[^/]+/report")) {
                        new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, exception);
                    } else {
                        new HttpStatusEntryPoint(HttpStatus.FORBIDDEN).commence(request, response, exception);
                    }
                }));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(AccountService.normalize(username))
            .map(user -> User.withUsername(user.getUsername()).password(user.getPasswordHash())
                .roles(user.getRole()).build())
            .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    }
}
