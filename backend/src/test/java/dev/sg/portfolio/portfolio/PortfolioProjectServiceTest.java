package dev.sg.portfolio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sg.portfolio.domain.PortfolioProject;
import dev.sg.portfolio.domain.PortfolioProjectImageUploadRequest;
import dev.sg.portfolio.domain.PortfolioProjectUpsertRequest;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class PortfolioProjectServiceTest {

    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    @TempDir
    Path tempDir;

    @Test
    void keepsDraftsPrivateAndPublishesTheSameProject() {
        PortfolioProjectService service = service();

        PortfolioProject draft = service.save(project("draft"));
        assertThat(service.listPublished()).isEmpty();
        assertThat(service.listAdmin()).extracting(PortfolioProject::slug).containsExactly("argenticommerce");

        PortfolioProject published = service.save(project("published"));
        assertThat(published.id()).isEqualTo(draft.id());
        assertThat(published.publishedAt()).isNotNull();
        assertThat(service.findPublished("argenticommerce").techStack())
                .containsExactly("Java", "Spring Boot", "Next.js");
    }

    @Test
    void storesAndReplacesTheCoverImage() {
        PortfolioProjectService service = service();
        PortfolioProject project = service.save(project("published"));

        var first = service.uploadImage(project.id(), image("cover", "Portada inicial"));
        assertThat(first.mimeType()).isEqualTo("image/png");
        assertThat(first.width()).isEqualTo(1);
        assertThat(service.media(first.storageKey())).isNotNull();

        var replacement = service.uploadImage(project.id(), image("cover", "Portada final"));
        PortfolioProject refreshed = service.findPublished("argenticommerce");
        assertThat(refreshed.images()).hasSize(1);
        assertThat(refreshed.images().getFirst().id()).isEqualTo(replacement.id());
        assertThat(service.media(first.storageKey())).isNull();
    }

    @Test
    void rejectsUnsafeProjectAndImageInputs() {
        PortfolioProjectService service = service();

        assertThatThrownBy(() -> service.save(new PortfolioProjectUpsertRequest(
                "", "../admin", "Proyecto", "Resumen", "", "javascript:alert(1)", "", "", List.of(), "draft", false, 0
        ))).isInstanceOf(IllegalArgumentException.class);

        PortfolioProject project = service.save(project("draft"));
        assertThatThrownBy(() -> service.uploadImage(project.id(), new PortfolioProjectImageUploadRequest(
                "payload.txt", "text/plain", Base64.getEncoder().encodeToString("not-image".getBytes()), "gallery", "", 0
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    private PortfolioProjectService service() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:projects-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return new PortfolioProjectService(
                new JdbcTemplate(dataSource),
                new ObjectMapper(),
                tempDir.resolve(UUID.randomUUID().toString()).toString(),
                5 * 1024 * 1024
        );
    }

    private PortfolioProjectUpsertRequest project(String status) {
        return new PortfolioProjectUpsertRequest(
                "",
                "argenticommerce",
                "ArgentiCommerce",
                "Plataforma SaaS multi-tenant para ecommerce.",
                "## Producto completo\n\nFrontend, API, datos y despliegue productivo.",
                "/argenticommerce/",
                "https://github.com/Sebas-gatica-dev/Argenticommerce",
                "argenticommerce",
                List.of("Java", "Spring Boot", "Next.js"),
                status,
                true,
                10
        );
    }

    private PortfolioProjectImageUploadRequest image(String kind, String altText) {
        return new PortfolioProjectImageUploadRequest(
                "cover.png",
                "image/png",
                ONE_PIXEL_PNG,
                kind,
                altText,
                0
        );
    }
}
