package dev.sg.portfolio.portfolio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sg.portfolio.domain.PortfolioProject;
import dev.sg.portfolio.domain.PortfolioProjectImage;
import dev.sg.portfolio.domain.PortfolioProjectImageUploadRequest;
import dev.sg.portfolio.domain.PortfolioProjectUpsertRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PortfolioProjectService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,118}[a-z0-9]$");
    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile("^[a-f0-9-]+\\.(?:jpg|png|webp)$");
    private static final Set<String> STATUSES = Set.of("draft", "published", "archived");
    private static final Set<String> IMAGE_KINDS = Set.of("cover", "gallery");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Path mediaRoot;
    private final long maxImageBytes;
    private volatile boolean schemaReady;

    public PortfolioProjectService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${portfolio.projects.media-dir:./data/project-media}") String mediaDir,
            @Value("${portfolio.projects.max-image-bytes:5242880}") long maxImageBytes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.mediaRoot = Path.of(mediaDir).toAbsolutePath().normalize();
        this.maxImageBytes = Math.max(256 * 1024, maxImageBytes);
    }

    public List<PortfolioProject> listPublished() {
        ensureSchema();
        return loadProjects("WHERE status = 'published' ORDER BY featured DESC, sort_order ASC, published_at DESC, title ASC");
    }

    public PortfolioProject findPublished(String slug) {
        ensureSchema();
        String normalizedSlug = normalizeSlug(slug);
        return loadProjects("WHERE status = 'published' AND slug = ?", normalizedSlug)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<PortfolioProject> listAdmin() {
        ensureSchema();
        return loadProjects("ORDER BY CASE status WHEN 'draft' THEN 0 WHEN 'published' THEN 1 ELSE 2 END, sort_order ASC, updated_at DESC");
    }

    @Transactional
    public PortfolioProject save(PortfolioProjectUpsertRequest request) {
        ensureSchema();
        PortfolioProjectUpsertRequest safe = validate(request);
        String id = normalizeId(safe.id());
        if (!StringUtils.hasText(id)) {
            id = findIdBySlug(safe.slug());
        }

        String techStackJson = writeTechStack(safe.techStack());
        Timestamp publishedAt = "published".equals(safe.status()) ? Timestamp.valueOf(LocalDateTime.now()) : null;
        if (StringUtils.hasText(id)) {
            int updated = jdbcTemplate.update("""
                    UPDATE portfolio_projects
                    SET slug = ?, title = ?, summary = ?, description_md = ?, live_url = ?, repository_url = ?,
                        infra_app_slug = ?, tech_stack_json = ?, status = ?, featured = ?, sort_order = ?,
                        published_at = CASE
                            WHEN ? = 'published' THEN COALESCE(published_at, ?)
                            ELSE NULL
                        END,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    safe.slug(), safe.title(), safe.summary(), safe.description(), safe.liveUrl(), safe.repositoryUrl(),
                    safe.infraAppSlug(), techStackJson, safe.status(), safe.featured(), safe.sortOrder(),
                    safe.status(), publishedAt, id
            );
            if (updated == 0) {
                throw new IllegalArgumentException("No encontre el proyecto solicitado.");
            }
        } else {
            id = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO portfolio_projects (
                        id, slug, title, summary, description_md, live_url, repository_url, infra_app_slug,
                        tech_stack_json, status, featured, sort_order, created_at, updated_at, published_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                    """,
                    id, safe.slug(), safe.title(), safe.summary(), safe.description(), safe.liveUrl(), safe.repositoryUrl(),
                    safe.infraAppSlug(), techStackJson, safe.status(), safe.featured(), safe.sortOrder(), publishedAt
            );
        }

        return findAdminById(id);
    }

    @Transactional
    public void deleteProject(String id) {
        ensureSchema();
        String normalizedId = requireId(id);
        List<String> keys = jdbcTemplate.query(
                "SELECT storage_key FROM portfolio_project_images WHERE project_id = ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                normalizedId
        );
        int deleted = jdbcTemplate.update("DELETE FROM portfolio_projects WHERE id = ?", normalizedId);
        if (deleted == 0) {
            throw new IllegalArgumentException("No encontre el proyecto solicitado.");
        }
        keys.forEach(this::deleteMediaQuietly);
    }

    public PortfolioProjectImage uploadImage(String projectId, PortfolioProjectImageUploadRequest request) {
        ensureSchema();
        String normalizedProjectId = requireId(projectId);
        if (findAdminById(normalizedProjectId) == null) {
            throw new IllegalArgumentException("No encontre el proyecto solicitado.");
        }
        if (request == null || !StringUtils.hasText(request.dataBase64())) {
            throw new IllegalArgumentException("Falta la imagen codificada en base64.");
        }

        String kind = normalizeKind(request.kind());
        byte[] bytes = decodeImage(request.dataBase64());
        ImageFormat format = detectImage(bytes);
        ImageDimensions dimensions = readDimensions(bytes, format);
        String imageId = UUID.randomUUID().toString();
        String storageKey = imageId + "." + format.extension();
        Path target = mediaPath(storageKey);

        List<String> replacedCoverKeys = new ArrayList<>();
        if ("cover".equals(kind)) {
            replacedCoverKeys.addAll(jdbcTemplate.query(
                    "SELECT storage_key FROM portfolio_project_images WHERE project_id = ? AND kind = 'cover'",
                    (resultSet, rowNum) -> resultSet.getString(1),
                    normalizedProjectId
            ));
        }

        try {
            Files.createDirectories(mediaRoot);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if ("cover".equals(kind)) {
                jdbcTemplate.update("DELETE FROM portfolio_project_images WHERE project_id = ? AND kind = 'cover'", normalizedProjectId);
            }
            jdbcTemplate.update("""
                    INSERT INTO portfolio_project_images (
                        id, project_id, kind, storage_key, alt_text, sort_order, mime_type,
                        size_bytes, width_px, height_px, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    imageId,
                    normalizedProjectId,
                    kind,
                    storageKey,
                    trim(request.altText(), 240),
                    request.sortOrder(),
                    format.mimeType(),
                    bytes.length,
                    dimensions.width(),
                    dimensions.height()
            );
        } catch (IOException | RuntimeException error) {
            deleteMediaQuietly(storageKey);
            throw new IllegalArgumentException("No pude guardar la imagen: " + error.getMessage(), error);
        }

        replacedCoverKeys.forEach(this::deleteMediaQuietly);
        return findImage(imageId);
    }

    @Transactional
    public void deleteImage(String projectId, String imageId) {
        ensureSchema();
        String normalizedProjectId = requireId(projectId);
        String normalizedImageId = requireId(imageId);
        List<String> keys = jdbcTemplate.query(
                "SELECT storage_key FROM portfolio_project_images WHERE id = ? AND project_id = ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                normalizedImageId,
                normalizedProjectId
        );
        int deleted = jdbcTemplate.update(
                "DELETE FROM portfolio_project_images WHERE id = ? AND project_id = ?",
                normalizedImageId,
                normalizedProjectId
        );
        if (deleted == 0) {
            throw new IllegalArgumentException("No encontre la imagen solicitada.");
        }
        keys.forEach(this::deleteMediaQuietly);
    }

    public MediaFile media(String storageKey) {
        if (!StringUtils.hasText(storageKey) || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            return null;
        }
        ensureSchema();
        List<String> mimeTypes = jdbcTemplate.query(
                "SELECT mime_type FROM portfolio_project_images WHERE storage_key = ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                storageKey
        );
        if (mimeTypes.isEmpty()) {
            return null;
        }
        try {
            Resource resource = new UrlResource(mediaPath(storageKey).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return null;
            }
            return new MediaFile(resource, mimeTypes.getFirst());
        } catch (IOException error) {
            return null;
        }
    }

    public long maxImageBytes() {
        return maxImageBytes;
    }

    private List<PortfolioProject> loadProjects(String suffix, Object... args) {
        String sql = """
                SELECT id, slug, title, summary, description_md, live_url, repository_url, infra_app_slug,
                       tech_stack_json, status, featured, sort_order, created_at, updated_at, published_at
                FROM portfolio_projects
                """ + suffix;
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> toProject(resultSet), args);
    }

    private PortfolioProject findAdminById(String id) {
        List<PortfolioProject> matches = loadProjects("WHERE id = ?", id);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private String findIdBySlug(String slug) {
        List<String> matches = jdbcTemplate.query(
                "SELECT id FROM portfolio_projects WHERE slug = ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                slug
        );
        return matches.isEmpty() ? "" : matches.getFirst();
    }

    private PortfolioProject toProject(ResultSet resultSet) throws SQLException {
        String id = resultSet.getString("id");
        return new PortfolioProject(
                id,
                resultSet.getString("slug"),
                resultSet.getString("title"),
                resultSet.getString("summary"),
                resultSet.getString("description_md"),
                resultSet.getString("live_url"),
                resultSet.getString("repository_url"),
                resultSet.getString("infra_app_slug"),
                readTechStack(resultSet.getString("tech_stack_json")),
                resultSet.getString("status"),
                resultSet.getBoolean("featured"),
                resultSet.getInt("sort_order"),
                localDateTime(resultSet, "created_at"),
                localDateTime(resultSet, "updated_at"),
                localDateTime(resultSet, "published_at"),
                loadImages(id)
        );
    }

    private List<PortfolioProjectImage> loadImages(String projectId) {
        return jdbcTemplate.query("""
                SELECT id, project_id, kind, storage_key, alt_text, sort_order, mime_type,
                       size_bytes, width_px, height_px, created_at
                FROM portfolio_project_images
                WHERE project_id = ?
                ORDER BY CASE kind WHEN 'cover' THEN 0 ELSE 1 END, sort_order ASC, created_at ASC
                """, (resultSet, rowNum) -> toImage(resultSet), projectId);
    }

    private PortfolioProjectImage findImage(String imageId) {
        List<PortfolioProjectImage> matches = jdbcTemplate.query("""
                SELECT id, project_id, kind, storage_key, alt_text, sort_order, mime_type,
                       size_bytes, width_px, height_px, created_at
                FROM portfolio_project_images
                WHERE id = ?
                """, (resultSet, rowNum) -> toImage(resultSet), imageId);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private PortfolioProjectImage toImage(ResultSet resultSet) throws SQLException {
        String key = resultSet.getString("storage_key");
        return new PortfolioProjectImage(
                resultSet.getString("id"),
                resultSet.getString("project_id"),
                resultSet.getString("kind"),
                key,
                "/api/project-media/" + key,
                resultSet.getString("alt_text"),
                resultSet.getInt("sort_order"),
                resultSet.getString("mime_type"),
                resultSet.getLong("size_bytes"),
                nullableInt(resultSet, "width_px"),
                nullableInt(resultSet, "height_px"),
                localDateTime(resultSet, "created_at")
        );
    }

    private synchronized void ensureSchema() {
        if (schemaReady) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portfolio_projects (
                    id VARCHAR(36) PRIMARY KEY,
                    slug VARCHAR(120) NOT NULL UNIQUE,
                    title VARCHAR(160) NOT NULL,
                    summary VARCHAR(600) NOT NULL,
                    description_md TEXT NOT NULL,
                    live_url VARCHAR(500),
                    repository_url VARCHAR(500),
                    infra_app_slug VARCHAR(120),
                    tech_stack_json TEXT NOT NULL,
                    status VARCHAR(24) NOT NULL,
                    featured BOOLEAN NOT NULL,
                    sort_order INT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    published_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS portfolio_project_images (
                    id VARCHAR(36) PRIMARY KEY,
                    project_id VARCHAR(36) NOT NULL,
                    kind VARCHAR(24) NOT NULL,
                    storage_key VARCHAR(180) NOT NULL UNIQUE,
                    alt_text VARCHAR(240),
                    sort_order INT NOT NULL,
                    mime_type VARCHAR(80) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    width_px INT,
                    height_px INT,
                    created_at TIMESTAMP NOT NULL,
                    CONSTRAINT portfolio_project_images_project_fk
                        FOREIGN KEY (project_id) REFERENCES portfolio_projects(id) ON DELETE CASCADE
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS portfolio_projects_status_order_idx ON portfolio_projects(status, sort_order)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS portfolio_project_images_project_idx ON portfolio_project_images(project_id, sort_order)");
        schemaReady = true;
    }

    private PortfolioProjectUpsertRequest validate(PortfolioProjectUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Faltan los datos del proyecto.");
        }
        String slug = normalizeSlug(request.slug());
        String title = requireText(request.title(), "El nombre es obligatorio.", 160);
        String summary = requireText(request.summary(), "El resumen es obligatorio.", 600);
        return new PortfolioProjectUpsertRequest(
                normalizeId(request.id()),
                slug,
                title,
                summary,
                trim(request.description(), 20_000),
                normalizeUrl(request.liveUrl(), true),
                normalizeUrl(request.repositoryUrl(), false),
                normalizeOptionalSlug(request.infraAppSlug()),
                normalizeTechStack(request.techStack()),
                normalizeStatus(request.status()),
                request.featured(),
                Math.max(-10_000, Math.min(10_000, request.sortOrder()))
        );
    }

    private String normalizeSlug(String value) {
        String slug = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("Slug invalido. Usa entre 3 y 120 caracteres: letras, numeros y guiones.");
        }
        return slug;
    }

    private String normalizeOptionalSlug(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String slug = value.trim().toLowerCase(Locale.ROOT);
        if (!slug.matches("^[a-z0-9][a-z0-9_-]{0,118}[a-z0-9]$")) {
            throw new IllegalArgumentException("infraAppSlug invalido.");
        }
        return slug;
    }

    private String normalizeStatus(String value) {
        String status = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "draft";
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado de proyecto invalido.");
        }
        return status;
    }

    private String normalizeKind(String value) {
        String kind = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "gallery";
        if (!IMAGE_KINDS.contains(kind)) {
            throw new IllegalArgumentException("El tipo de imagen debe ser cover o gallery.");
        }
        return kind;
    }

    private String normalizeUrl(String value, boolean allowRelative) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String url = trim(value, 500);
        if ((allowRelative && url.startsWith("/")) || url.startsWith("https://") || url.startsWith("http://")) {
            return url;
        }
        throw new IllegalArgumentException("URL invalida. Usa http(s)" + (allowRelative ? " o una ruta /local" : "") + ".");
    }

    private List<String> normalizeTechStack(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> trim(value, 60))
                .distinct()
                .limit(20)
                .toList();
    }

    private String writeTechStack(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("No pude serializar el stack tecnico.", error);
        }
    }

    private List<String> readTechStack(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException error) {
            return List.of();
        }
    }

    private byte[] decodeImage(String value) {
        String encoded = value.trim();
        int comma = encoded.indexOf(',');
        if (encoded.startsWith("data:") && comma >= 0) {
            encoded = encoded.substring(comma + 1);
        }
        long approximateBytes = (encoded.length() * 3L) / 4L;
        if (approximateBytes > maxImageBytes + 16) {
            throw new IllegalArgumentException("La imagen supera el maximo de " + maxImageBytes + " bytes.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > maxImageBytes) {
                throw new IllegalArgumentException("La imagen esta vacia o supera el maximo permitido.");
            }
            return bytes;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("La imagen base64 es invalida o demasiado grande.", error);
        }
    }

    private ImageFormat detectImage(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47) {
            return new ImageFormat("png", "image/png");
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return new ImageFormat("jpg", "image/jpeg");
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new ImageFormat("webp", "image/webp");
        }
        throw new IllegalArgumentException("Formato no permitido. Usa JPEG, PNG o WebP.");
    }

    private ImageDimensions readDimensions(byte[] bytes, ImageFormat format) {
        if ("webp".equals(format.extension())) {
            return new ImageDimensions(null, null);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("El archivo no contiene una imagen valida.");
            }
            return new ImageDimensions(image.getWidth(), image.getHeight());
        } catch (IOException error) {
            throw new IllegalArgumentException("No pude leer la imagen.", error);
        }
    }

    private Path mediaPath(String storageKey) {
        Path resolved = mediaRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(mediaRoot)) {
            throw new IllegalArgumentException("Ruta de imagen invalida.");
        }
        return resolved;
    }

    private void deleteMediaQuietly(String storageKey) {
        try {
            Files.deleteIfExists(mediaPath(storageKey));
        } catch (IOException ignored) {
            // The database remains authoritative; orphan cleanup can retry later.
        }
    }

    private String normalizeId(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("ID de proyecto invalido.");
        }
    }

    private String requireId(String value) {
        String id = normalizeId(value);
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Falta el ID solicitado.");
        }
        return id;
    }

    private String requireText(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return trim(value, maxLength);
    }

    private String trim(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Integer nullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    public record MediaFile(Resource resource, String mimeType) {
    }

    private record ImageFormat(String extension, String mimeType) {
    }

    private record ImageDimensions(Integer width, Integer height) {
    }
}
