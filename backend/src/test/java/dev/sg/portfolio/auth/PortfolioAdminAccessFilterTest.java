package dev.sg.portfolio.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sg.portfolio.PortfolioAdminAccessFilter;
import dev.sg.portfolio.portfolio.PortfolioAdminAuthorizer;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.web.server.MockWebSession;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PortfolioAdminAccessFilterTest {

    private static final String PROJECTS_PATH = "/api/admin/projects";
    private static final String TOKEN_HEADER = "X-Sgdev-Portfolio-Admin-Token";

    private final AtomicBoolean chainCalled = new AtomicBoolean();
    private final WebFilterChain chain = exchange -> {
        chainCalled.set(true);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return Mono.empty();
    };

    @Test
    void rejectsRequestsWithoutSessionOrToken() {
        PortfolioAdminAccessFilter filter = filter("token-configurado");
        MockServerWebExchange exchange = exchange(PROJECTS_PATH);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("No autorizado.");
        assertThat(chainCalled).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/proj%65cts", "/api/admin/projects;v=1", "/api/admin/proj%65cts/123"})
    void protectsEquivalentEncodedAndMatrixPaths(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, URI.create(path)).build();

        assertThat(authorize(filter("token-configurado"), MockServerWebExchange.from(request)))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void returnsUnauthorizedInsteadOf503WhenTheTokenIsMissing() {
        PortfolioAdminAccessFilter filter = filter("");

        assertThat(authorize(filter, exchange(PROJECTS_PATH))).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void rejectsThePublicPlaceholderToken() {
        PortfolioAdminAccessFilter filter = filter("change-me-admin-token");
        MockServerWebExchange exchange = exchange(PROJECTS_PATH, TOKEN_HEADER, "change-me-admin-token");

        assertThat(authorize(filter, exchange)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void acceptsAConfiguredHeaderToken() {
        PortfolioAdminAccessFilter filter = filter("token-configurado");
        MockServerWebExchange exchange = exchange(PROJECTS_PATH, TOKEN_HEADER, "token-configurado");

        assertThat(authorize(filter, exchange)).isEqualTo(HttpStatus.OK);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void acceptsTheUsageTokenWhenTheProjectTokenIsBlank() {
        PortfolioAdminAccessFilter filter = new PortfolioAdminAccessFilter(
                new PortfolioAdminAuthorizer(" ", "token-de-uso"));

        assertThat(authorize(filter, exchange(PROJECTS_PATH, TOKEN_HEADER, "token-de-uso")))
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void prefersTheProjectTokenWhenBothTokensAreConfigured() {
        PortfolioAdminAccessFilter filter = new PortfolioAdminAccessFilter(
                new PortfolioAdminAuthorizer("token-del-proyecto", "token-de-uso"));

        assertThat(authorize(filter, exchange(PROJECTS_PATH, TOKEN_HEADER, "token-de-uso")))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void acceptsABearerToken() {
        PortfolioAdminAccessFilter filter = filter("token-configurado");
        MockServerWebExchange exchange = exchange(
                PROJECTS_PATH,
                "Authorization",
                "Bearer token-configurado"
        );

        assertThat(authorize(filter, exchange)).isEqualTo(HttpStatus.OK);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void acceptsAnAuthenticatedAdminSession() {
        PortfolioAdminAccessFilter filter = filter("token-configurado");
        MockWebSession session = new MockWebSession();
        session.getAttributes().put(AdminSessions.ADMIN_USERNAME_ATTRIBUTE, "sgdev-admin");
        MockServerWebExchange exchange = MockServerWebExchange.builder(MockServerHttpRequest.get(PROJECTS_PATH))
                .session(session)
                .build();

        assertThat(authorize(filter, exchange)).isEqualTo(HttpStatus.OK);
        assertThat(chainCalled).isTrue();
    }

    @Test
    void rejectsASessionWithoutTheAdminAttribute() {
        PortfolioAdminAccessFilter filter = filter("token-configurado");
        MockServerWebExchange exchange = MockServerWebExchange.builder(MockServerHttpRequest.get(PROJECTS_PATH))
                .session(new MockWebSession())
                .build();

        assertThat(authorize(filter, exchange)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void ignoresPathsOutsideTheProjectsAdminApi() {
        PortfolioAdminAccessFilter filter = filter("");

        assertThat(authorize(filter, exchange("/api/admin/usage/ips"))).isEqualTo(HttpStatus.OK);
        assertThat(chainCalled).isTrue();
    }

    private HttpStatus authorize(PortfolioAdminAccessFilter filter, MockServerWebExchange exchange) {
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        return HttpStatus.valueOf(exchange.getResponse().getStatusCode().value());
    }

    private MockServerWebExchange exchange(String path, String header, String value) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header(header, value)
                .build();
        return MockServerWebExchange.from(request);
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    private PortfolioAdminAccessFilter filter(String token) {
        return new PortfolioAdminAccessFilter(new PortfolioAdminAuthorizer(token, ""));
    }
}
