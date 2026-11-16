package dev.sg.portfolio.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

class AuthControllerTest {

    private static final String USERNAME = "sgdev-admin";
    private static final String TEST_PASSWORD = "prueba-local";
    private static final String ANONYMOUS = """
            {"authenticated":false,"username":null}
            """;

    @Test
    void logsInAcrossRequestsAndInvalidatesTheSessionOnLogout() {
        WebTestClient client = client(TEST_PASSWORD);

        client.get().uri("/api/auth/session").exchange()
                .expectStatus().isOk().expectBody().json(ANONYMOUS);

        ResponseCookie cookie = client.post().uri("https://localhost/api/auth/login")
                .bodyValue(Map.of("username", USERNAME, "password", TEST_PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("""
                        {"authenticated":true,"username":"sgdev-admin"}
                        """)
                .returnResult().getResponseCookies().getFirst("SESSION");

        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");

        client.get().uri("/api/auth/session").cookie("SESSION", cookie.getValue()).exchange()
                .expectStatus().isOk().expectBody().json("""
                        {"authenticated":true,"username":"sgdev-admin"}
                        """);
        client.post().uri("/api/auth/logout").cookie("SESSION", cookie.getValue()).exchange()
                .expectStatus().isOk().expectBody().json(ANONYMOUS);
        client.get().uri("/api/auth/session").cookie("SESSION", cookie.getValue()).exchange()
                .expectStatus().isOk().expectBody().json(ANONYMOUS);
    }

    @Test
    void returnsTheSameUnauthorizedResponseForEachCredentialFailure() {
        WebTestClient configured = client(TEST_PASSWORD);

        assertRejected(configured, Map.of("username", "otro-usuario", "password", TEST_PASSWORD));
        assertRejected(configured, Map.of("username", USERNAME, "password", "incorrecta"));
        assertRejected(configured, Map.of("username", USERNAME));
        assertRejected(client(""), Map.of("username", USERNAME, "password", TEST_PASSWORD));
    }

    private WebTestClient client(String password) {
        AdminCredentials credentials = new AdminCredentials(USERNAME, password, "", new BCryptPasswordEncoder(4));
        return WebTestClient.bindToController(new AuthController(credentials)).build();
    }

    private void assertRejected(WebTestClient client, Map<String, String> body) {
        client.post().uri("/api/auth/login").bodyValue(body).exchange()
                .expectStatus().isUnauthorized()
                .expectCookie().doesNotExist("SESSION")
                .expectBody().json("""
                        {"authenticated":false,"username":null,"message":"Credenciales inválidas."}
                        """);
    }
}
