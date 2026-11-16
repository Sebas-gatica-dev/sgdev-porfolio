package dev.sg.portfolio.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.session.WebSessionIdResolver;

/** Configura la cookie de sesión de WebFlux sin reactivar la persistencia de Spring Security. */
@Configuration
public class SessionConfig {

    @Bean
    WebSessionIdResolver webSessionIdResolver() {
        return new SecureSessionCookieResolver();
    }
}
