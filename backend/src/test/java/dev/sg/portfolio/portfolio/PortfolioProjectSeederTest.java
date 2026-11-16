package dev.sg.portfolio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sg.portfolio.domain.PortfolioProject;
import dev.sg.portfolio.domain.PortfolioProjectUpsertRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class PortfolioProjectSeederTest {

    @TempDir
    Path tempDir;

    @Test
    void insertsArgentiCommerceOnlyOnceIntoAnEmptyTable() {
        PortfolioProjectService service = service();
        assertThat(service.listAdmin()).isEmpty();

        assertThat(service.seedArgentiCommerceIfEmpty()).isTrue();
        assertThat(service.seedArgentiCommerceIfEmpty()).isFalse();

        List<PortfolioProject> projects = service.listAdmin();
        assertThat(projects).hasSize(1);

        PortfolioProject project = projects.getFirst();
        assertThat(project.slug()).isEqualTo("argenticommerce");
        assertThat(project.title()).isEqualTo("ArgentiCommerce");
        assertThat(project.status()).isEqualTo("published");
        assertThat(project.featured()).isTrue();
        assertThat(project.sortOrder()).isEqualTo(10);
        assertThat(project.liveUrl()).isEqualTo("/argenticommerce/");
        assertThat(project.repositoryUrl()).isEqualTo("https://github.com/Sebas-gatica-dev/Argenticommerce");
        assertThat(project.infraAppSlug()).isEqualTo("argenticommerce");
        assertThat(project.techStack()).containsExactly(
                "Next.js",
                "TypeScript",
                "Spring Boot",
                "PostgreSQL",
                "Flyway",
                "Docker",
                "Nginx"
        );
        assertThat(project.summary()).isNotBlank();
        assertThat(project.description()).contains("Multi-tenant", "RBAC", "Mateando");
        assertThat(project.images()).isEmpty();
        assertThat(project.publishedAt()).isNotNull();
    }

    @Test
    void doesNotInsertWhenAnyOtherProjectExistsWithoutTheSlug() {
        PortfolioProjectService service = service();
        service.save(project("otro-proyecto", "draft"));

        assertThat(service.seedArgentiCommerceIfEmpty()).isFalse();

        assertThat(service.listAdmin()).extracting(PortfolioProject::slug)
                .containsExactly("otro-proyecto");
        assertThat(service.findPublished("argenticommerce")).isNull();
    }

    @Test
    void keepsAnExistingArgentiCommerceDraftUnchanged() {
        PortfolioProjectService service = service();
        PortfolioProject draft = service.save(project("argenticommerce", "draft"));

        assertThat(service.seedArgentiCommerceIfEmpty()).isFalse();

        PortfolioProject stored = service.listAdmin().getFirst();
        assertThat(stored.id()).isEqualTo(draft.id());
        assertThat(stored.status()).isEqualTo("draft");
        assertThat(stored.featured()).isFalse();
        assertThat(stored.sortOrder()).isEqualTo(3);
        assertThat(stored.liveUrl()).isEmpty();
        assertThat(service.listPublished()).isEmpty();
    }

    private PortfolioProjectService service() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:seed-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return new PortfolioProjectService(
                new JdbcTemplate(dataSource),
                new ObjectMapper(),
                tempDir.resolve(UUID.randomUUID().toString()).toString(),
                5 * 1024 * 1024
        );
    }

    private PortfolioProjectUpsertRequest project(String slug, String status) {
        return new PortfolioProjectUpsertRequest(
                "",
                slug,
                "Proyecto " + slug,
                "Resumen del proyecto " + slug,
                "## Detalle\n\nContenido de prueba.",
                "",
                "https://github.com/Sebas-gatica-dev/Argenticommerce",
                "",
                List.of("Java", "Spring Boot"),
                status,
                false,
                3
        );
    }
}
