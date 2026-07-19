package dev.sg.portfolio.portfolio;

import dev.sg.portfolio.domain.PortfolioProject;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PortfolioProjectsController {

    private final PortfolioProjectService projectService;

    public PortfolioProjectsController(PortfolioProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/projects")
    public Map<String, Object> listProjects() {
        return Map.of("ok", true, "items", projectService.listPublished());
    }

    @GetMapping("/projects/{slug}")
    public ResponseEntity<Object> project(@PathVariable String slug) {
        final var project = findPublishedOrNull(slug);
        if (project == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "ok", false,
                    "message", "Proyecto no encontrado."
            ));
        }
        return ResponseEntity.ok(Map.of("ok", true, "item", project));
    }

    private PortfolioProject findPublishedOrNull(String slug) {
        try {
            return projectService.findPublished(slug);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @GetMapping("/project-media/{storageKey}")
    public ResponseEntity<?> media(@PathVariable String storageKey) {
        var media = projectService.media(storageKey);
        if (media == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(MediaType.parseMediaType(media.mimeType()))
                .body(media.resource());
    }
}
