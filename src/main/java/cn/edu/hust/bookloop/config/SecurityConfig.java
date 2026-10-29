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
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/", "/login", "/register", "/prototype/**", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/register").permitAll()
                .requestMatchers("/account", "/account/contact", "/account/password").authenticated()
                .requestMatchers(HttpMethod.HEAD, "/", "/prototype/**").permitAll()
                .anyRequest().denyAll())
            .formLogin(login -> login.loginPage("/login")
                .defaultSuccessUrl("/account", true).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    if (request.getServletPath().equals("/account") || request.getServletPath().startsWith("/account/")) {
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
