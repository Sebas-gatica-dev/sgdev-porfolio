package dev.sg.portfolio.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminCredentialsTest {

    private static final String USER = "sgdev-admin";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void acceptsTheConfiguredPlaintextPassword() {
        AdminCredentials credentials = new AdminCredentials(USER, "prueba-local", "", passwordEncoder);

        assertThat(credentials.isConfigured()).isTrue();
        assertThat(credentials.matches(USER, "prueba-local")).isTrue();
    }

    @Test
    void rejectsWrongPasswordAndUnknownUserWithTheSameResult() {
        AdminCredentials credentials = new AdminCredentials(USER, "prueba-local", "", passwordEncoder);

        boolean unknownUser = credentials.matches("otro-usuario", "prueba-local");
        boolean wrongPassword = credentials.matches(USER, "otra-clave");

        assertThat(unknownUser).isFalse();
        assertThat(wrongPassword).isFalse();
        assertThat(unknownUser).isEqualTo(wrongPassword);
    }

    @Test
    void neverAuthenticatesWithoutPasswordOrHash() {
        AdminCredentials credentials = new AdminCredentials(USER, "", "", passwordEncoder);

        assertThat(credentials.isConfigured()).isFalse();
        assertThat(credentials.matches(USER, "")).isFalse();
        assertThat(credentials.matches(USER, "cualquier-clave")).isFalse();
    }

    @Test
    void bcryptHashTakesPrecedenceOverPlaintextPassword() {
        String hash = passwordEncoder.encode("hash-local");
        AdminCredentials credentials = new AdminCredentials(USER, "claro-local", hash, passwordEncoder);

        assertThat(credentials.matches(USER, "hash-local")).isTrue();
        assertThat(credentials.matches(USER, "claro-local")).isFalse();
    }

    @Test
    void acceptsTheDefaultUsernameWhenOnlyPasswordIsConfigured() {
        AdminCredentials credentials = new AdminCredentials(USER, "prueba-local", "", passwordEncoder);

        assertThat(credentials.username()).isEqualTo(USER);
        assertThat(credentials.matches("SGDEV-ADMIN", "prueba-local")).isFalse();
    }
}
