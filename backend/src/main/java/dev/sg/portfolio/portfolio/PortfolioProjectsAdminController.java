package dev.sg.portfolio.portfolio;

import dev.sg.portfolio.domain.PortfolioProjectImageUploadRequest;
import dev.sg.portfolio.domain.PortfolioProjectUpsertRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/projects")
public class PortfolioProjectsAdminController {

    private final PortfolioProjectService projectService;
    private final PortfolioAdminAuthorizer authorizer;

    public PortfolioProjectsAdminController(
            PortfolioProjectService projectService,
            PortfolioAdminAuthorizer authorizer
    ) {
        this.projectService = projectService;
        this.authorizer = authorizer;
    }

    @GetMapping
    public ResponseEntity<Object> list(ServerHttpRequest request) {
        ResponseEntity<Object> rejected = rejectIfUnauthorized(request);
        if (rejected != null) {
            return rejected;
        }
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "items", projectService.listAdmin(),
                "maxImageBytes", projectService.maxImageBytes()
        ));
    }

    @PostMapping
    public ResponseEntity<Object> save(
            @RequestBody PortfolioProjectUpsertRequest payload,
            ServerHttpRequest request
    ) {
        ResponseEntity<Object> rejected = rejectIfUnauthorized(request);
        if (rejected != null) {
            return rejected;
        }
        try {
            return ResponseEntity.ok(Map.of("ok", true, "item", projectService.save(payload)));
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Object> delete(
            @PathVariable String projectId,
            ServerHttpRequest request
    ) {
        ResponseEntity<Object> rejected = rejectIfUnauthorized(request);
        if (rejected != null) {
            return rejected;
        }
        try {
            projectService.deleteProject(projectId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    @PostMapping("/{projectId}/images")
    public ResponseEntity<Object> uploadImage(
            @PathVariable String projectId,
            @RequestBody PortfolioProjectImageUploadRequest payload,
            ServerHttpRequest request
    ) {
        ResponseEntity<Object> rejected = rejectIfUnauthorized(request);
        if (rejected != null) {
            return rejected;
        }
        try {
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "item", projectService.uploadImage(projectId, payload)
            ));
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    @DeleteMapping("/{projectId}/images/{imageId}")
    public ResponseEntity<Object> deleteImage(
            @PathVariable String projectId,
            @PathVariable String imageId,
            ServerHttpRequest request
    ) {
        ResponseEntity<Object> rejected = rejectIfUnauthorized(request);
        if (rejected != null) {
            return rejected;
        }
        try {
            projectService.deleteImage(projectId, imageId);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException error) {
            return badRequest(error);
        }
    }

    private ResponseEntity<Object> rejectIfUnauthorized(ServerHttpRequest request) {
        PortfolioAdminAuthorizer.AuthorizationResult result = authorizer.authorize(request);
        if (result.allowed()) {
            return null;
        }
        return ResponseEntity.status(result.status()).body(Map.of(
                "ok", false,
                "message", result.message()
        ));
    }

    private ResponseEntity<Object> badRequest(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "ok", false,
                "message", error.getMessage()
        ));
    }
}
