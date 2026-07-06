package dev.sg.portfolio.usage;

import dev.sg.portfolio.config.IpPromptLimitProperties;
import dev.sg.portfolio.domain.IpUsageAdminRow;
import dev.sg.portfolio.domain.PromptLimitStatus;
import dev.sg.portfolio.domain.TokenGrantRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IpPromptLimitService {

    private static final String REQUEST_PENDING = "pending";
    private static final String REQUEST_GRANTED = "granted";

    private final JdbcTemplate jdbcTemplate;
    private final IpPromptLimitProperties properties;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);

    public IpPromptLimitService(JdbcTemplate jdbcTemplate, IpPromptLimitProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public PromptLimitStatus reservePrompt(String clientIp) {
        return reserveTokens(clientIp, properties.chatTokenCost(), 0);
    }

    public PromptLimitStatus reserveVoiceMinute(String clientIp) {
        return reserveTokens(clientIp, properties.voiceTokenCost(), properties.voiceSessionSeconds());
    }

    public PromptLimitStatus voiceMinuteStatus(String clientIp) {
        return statusForCost(clientIp, properties.voiceTokenCost(), properties.voiceSessionSeconds());
    }

    public PromptLimitStatus status(String clientIp) {
        return statusForCost(clientIp, properties.chatTokenCost(), 0);
    }

    public PromptLimitStatus requestMoreTokens(String clientIp) {
        if (!properties.enabled()) {
            return disabledStatus();
        }

        ensureSchema();
        Visitor visitor = ensureVisitor(clientIp);
        jdbcTemplate.update("""
                UPDATE ip_prompt_usage
                SET token_request_count = COALESCE(token_request_count, 0) + 1,
                    last_token_request_at = CURRENT_TIMESTAMP,
                    request_status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE ip_hash = ?
                """, REQUEST_PENDING, visitor.ipHash());

        Usage usage = loadUsage(visitor.ipHash());
        return toStatus(usage, properties.chatTokenCost(), 0, visitor.created());
    }

    public List<IpUsageAdminRow> adminRows() {
        ensureSchema();
        return jdbcTemplate.query("""
                SELECT ip_hash,
                       client_ip,
                       prompt_count,
                       COALESCE(token_limit, ?) AS token_limit,
                       COALESCE(voice_seconds_used, 0) AS voice_seconds_used,
                       COALESCE(token_request_count, 0) AS token_request_count,
                       last_token_request_at,
                       last_granted_at,
                       request_status,
                       admin_note,
                       first_seen_at,
                       updated_at
                FROM ip_prompt_usage
                ORDER BY CASE WHEN request_status = 'pending' THEN 0 ELSE 1 END,
                         updated_at DESC
                """, (resultSet, rowNum) -> toAdminRow(resultSet), properties.maxTokens());
    }

    public IpUsageAdminRow grantTokens(TokenGrantRequest request) {
        TokenGrantRequest safeRequest = request == null
                ? new TokenGrantRequest("", "", 0, "")
                : request;
        int tokens = safeRequest.tokens();
        if (tokens <= 0) {
            throw new IllegalArgumentException("La cantidad de tokens debe ser mayor a 0.");
        }

        ensureSchema();
        String ipHash = normalizeHash(safeRequest.ipHash());
        if (!StringUtils.hasText(ipHash)) {
            if (!StringUtils.hasText(safeRequest.clientIp())) {
                throw new IllegalArgumentException("Falta ipHash o clientIp para acreditar tokens.");
            }
            ipHash = ensureVisitor(safeRequest.clientIp()).ipHash();
        }

        int updated = jdbcTemplate.update("""
                UPDATE ip_prompt_usage
                SET token_limit = COALESCE(token_limit, ?) + ?,
                    request_status = ?,
                    admin_note = ?,
                    last_granted_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE ip_hash = ?
                """,
                properties.maxTokens(),
                tokens,
                REQUEST_GRANTED,
                trimToLength(safeRequest.note(), 500),
                ipHash
        );
        if (updated == 0) {
            throw new IllegalArgumentException("No encontre una IP registrada con ese identificador.");
        }

        return loadAdminRow(ipHash);
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public int maxPrompts() {
        return properties.maxTokens();
    }

    public int maxTokens() {
        return properties.maxTokens();
    }

    public int voiceMinuteCost() {
        return properties.voiceTokenCost();
    }

    public int voiceSessionSeconds() {
        return properties.voiceSessionSeconds();
    }

    public int maxVoiceSeconds() {
        return properties.maxVoiceSeconds();
    }

    public String adminToken() {
        return properties.adminToken();
    }

    public PromptLimitStatus reserveCredits(String clientIp, int cost) {
        return reserveTokens(clientIp, Math.max(1, cost), 0);
    }

    private PromptLimitStatus reserveTokens(String clientIp, int tokenCost, int voiceSecondsCost) {
        int normalizedTokenCost = Math.max(1, tokenCost);
        int normalizedVoiceSecondsCost = Math.max(0, voiceSecondsCost);
        if (!properties.enabled()) {
            return disabledStatus();
        }

        ensureSchema();
        Visitor visitor = ensureVisitor(clientIp);
        int updated = jdbcTemplate.update("""
                UPDATE ip_prompt_usage
                SET prompt_count = prompt_count + ?,
                    voice_seconds_used = COALESCE(voice_seconds_used, 0) + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE ip_hash = ?
                  AND prompt_count + ? <= COALESCE(token_limit, ?)
                  AND COALESCE(voice_seconds_used, 0) + ? <= ?
                """,
                normalizedTokenCost,
                normalizedVoiceSecondsCost,
                visitor.ipHash(),
                normalizedTokenCost,
                properties.maxTokens(),
                normalizedVoiceSecondsCost,
                properties.maxVoiceSeconds()
        );

        Usage usage = loadUsage(visitor.ipHash());
        return toStatus(
                usage,
                normalizedTokenCost,
                normalizedVoiceSecondsCost,
                visitor.created(),
                updated > 0
        );
    }

    private PromptLimitStatus statusForCost(String clientIp, int tokenCost, int voiceSecondsCost) {
        if (!properties.enabled()) {
            return disabledStatus();
        }

        ensureSchema();
        Visitor visitor = ensureVisitor(clientIp);
        Usage usage = loadUsage(visitor.ipHash());
        return toStatus(usage, Math.max(1, tokenCost), Math.max(0, voiceSecondsCost), visitor.created());
    }

    private PromptLimitStatus disabledStatus() {
        return PromptLimitStatus.disabled(
                properties.maxTokens(),
                properties.maxVoiceSeconds(),
                properties.chatTokenCost(),
                properties.voiceTokenCost(),
                properties.voiceSessionSeconds()
        );
    }

    private PromptLimitStatus toStatus(
            Usage usage,
            int tokenCost,
            int voiceSecondsCost,
            boolean newVisitor
    ) {
        int tokenRemaining = Math.max(0, usage.tokenLimit() - usage.tokenUsed());
        int voiceSecondsRemaining = Math.max(0, properties.maxVoiceSeconds() - usage.voiceSecondsUsed());
        boolean allowed = tokenRemaining >= tokenCost && voiceSecondsRemaining >= voiceSecondsCost;
        return toStatus(usage, tokenCost, voiceSecondsCost, newVisitor, allowed);
    }

    private PromptLimitStatus toStatus(
            Usage usage,
            int tokenCost,
            int voiceSecondsCost,
            boolean newVisitor,
            boolean allowed
    ) {
        int tokenRemaining = Math.max(0, usage.tokenLimit() - usage.tokenUsed());
        int voiceSecondsRemaining = Math.max(0, properties.maxVoiceSeconds() - usage.voiceSecondsUsed());
        return new PromptLimitStatus(
                true,
                allowed,
                usage.tokenUsed(),
                tokenRemaining,
                usage.tokenLimit(),
                usage.voiceSecondsUsed(),
                voiceSecondsRemaining,
                properties.maxVoiceSeconds(),
                properties.chatTokenCost(),
                properties.voiceTokenCost(),
                properties.voiceSessionSeconds(),
                newVisitor,
                REQUEST_PENDING.equalsIgnoreCase(usage.requestStatus())
        );
    }

    private void ensureSchema() {
        if (!schemaReady.compareAndSet(false, true)) {
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ip_prompt_usage (
                    ip_hash VARCHAR(64) PRIMARY KEY,
                    client_ip VARCHAR(128) NOT NULL,
                    prompt_count INT NOT NULL,
                    token_limit INT,
                    voice_seconds_used INT,
                    token_request_count INT,
                    last_token_request_at TIMESTAMP,
                    last_granted_at TIMESTAMP,
                    request_status VARCHAR(32),
                    admin_note VARCHAR(500),
                    first_seen_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);

        addColumnIfMissing("ALTER TABLE ip_prompt_usage ADD COLUMN IF NOT EXISTS token_limit INT");
        addColumnIfMissing("ALTER TABLE ip_prompt_usage ADD COLUMN IF NOT EXISTS voice_seconds_used INT");
        addColumnIfMissing("ALTER TABLE ip_prompt_usage ADD COLUMN IF NOT EXISTS token_request_count INT");
        addColumnIfMissing("ALTER TABLE ip_prompt_usage ADD COLUMN IF NOT EXISTS last_token_request_at TIMESTAMP");
        addColumnIfMissing("ALTER TABLE ip_prompt_usage ADD COLUMN IF NOT EXISTS last_granted_at TIMESTAMP");
        addColumnIfMissing("ALTER TABLE ip_prompt_usage ADD COLUMN IF NOT EXISTS request_status VARCHAR(32)");
        addColumnIfMissing("ALTER TABLE ip_prompt_usage ADD COLUMN IF NOT EXISTS admin_note VARCHAR(500)");

        jdbcTemplate.update("UPDATE ip_prompt_usage SET token_limit = ? WHERE token_limit IS NULL", properties.maxTokens());
        jdbcTemplate.update("UPDATE ip_prompt_usage SET voice_seconds_used = 0 WHERE voice_seconds_used IS NULL");
        jdbcTemplate.update("UPDATE ip_prompt_usage SET token_request_count = 0 WHERE token_request_count IS NULL");
        jdbcTemplate.update("UPDATE ip_prompt_usage SET request_status = 'none' WHERE request_status IS NULL");
    }

    private void addColumnIfMissing(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException error) {
            String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
            if (!message.contains("duplicate") && !message.contains("already exists")) {
                throw error;
            }
        }
    }

    private Visitor ensureVisitor(String clientIp) {
        String normalizedIp = normalize(clientIp);
        String ipHash = hash(normalizedIp);
        try {
            jdbcTemplate.update("""
                    INSERT INTO ip_prompt_usage (
                        ip_hash,
                        client_ip,
                        prompt_count,
                        token_limit,
                        voice_seconds_used,
                        token_request_count,
                        request_status,
                        first_seen_at,
                        updated_at
                    )
                    VALUES (?, ?, 0, ?, 0, 0, 'none', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, ipHash, normalizedIp, properties.maxTokens());
            return new Visitor(ipHash, normalizedIp, true);
        } catch (DuplicateKeyException ignored) {
            return new Visitor(ipHash, normalizedIp, false);
        }
    }

    private Usage loadUsage(String ipHash) {
        return jdbcTemplate.queryForObject(
                """
                SELECT ip_hash,
                       client_ip,
                       prompt_count,
                       COALESCE(token_limit, ?) AS token_limit,
                       COALESCE(voice_seconds_used, 0) AS voice_seconds_used,
                       COALESCE(token_request_count, 0) AS token_request_count,
                       request_status
                FROM ip_prompt_usage
                WHERE ip_hash = ?
                """,
                (resultSet, rowNum) -> toUsage(resultSet),
                properties.maxTokens(),
                ipHash
        );
    }

    private IpUsageAdminRow loadAdminRow(String ipHash) {
        return jdbcTemplate.queryForObject(
                """
                SELECT ip_hash,
                       client_ip,
                       prompt_count,
                       COALESCE(token_limit, ?) AS token_limit,
                       COALESCE(voice_seconds_used, 0) AS voice_seconds_used,
                       COALESCE(token_request_count, 0) AS token_request_count,
                       last_token_request_at,
                       last_granted_at,
                       request_status,
                       admin_note,
                       first_seen_at,
                       updated_at
                FROM ip_prompt_usage
                WHERE ip_hash = ?
                """,
                (resultSet, rowNum) -> toAdminRow(resultSet),
                properties.maxTokens(),
                ipHash
        );
    }

    private Usage toUsage(ResultSet resultSet) throws SQLException {
        return new Usage(
                resultSet.getString("ip_hash"),
                resultSet.getString("client_ip"),
                resultSet.getInt("prompt_count"),
                resultSet.getInt("token_limit"),
                resultSet.getInt("voice_seconds_used"),
                resultSet.getInt("token_request_count"),
                resultSet.getString("request_status")
        );
    }

    private IpUsageAdminRow toAdminRow(ResultSet resultSet) throws SQLException {
        int tokenUsed = resultSet.getInt("prompt_count");
        int tokenLimit = resultSet.getInt("token_limit");
        int voiceSecondsUsed = resultSet.getInt("voice_seconds_used");
        return new IpUsageAdminRow(
                resultSet.getString("ip_hash"),
                resultSet.getString("client_ip"),
                tokenUsed,
                Math.max(0, tokenLimit - tokenUsed),
                tokenLimit,
                voiceSecondsUsed,
                Math.max(0, properties.maxVoiceSeconds() - voiceSecondsUsed),
                properties.maxVoiceSeconds(),
                resultSet.getInt("token_request_count"),
                resultSet.getString("request_status"),
                resultSet.getString("admin_note"),
                localDateTime(resultSet, "first_seen_at"),
                localDateTime(resultSet, "updated_at"),
                localDateTime(resultSet, "last_token_request_at"),
                localDateTime(resultSet, "last_granted_at")
        );
    }

    private LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String normalize(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }

        return clientIp.trim().toLowerCase();
    }

    private String normalizeHash(String ipHash) {
        if (!StringUtils.hasText(ipHash)) {
            return "";
        }
        return ipHash.trim().toLowerCase();
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public String safetyIdentifier(String clientIp) {
        return hash(normalize(clientIp));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no esta disponible", error);
        }
    }

    private record Visitor(String ipHash, String clientIp, boolean created) {
    }

    private record Usage(
            String ipHash,
            String clientIp,
            int tokenUsed,
            int tokenLimit,
            int voiceSecondsUsed,
            int tokenRequestCount,
            String requestStatus
    ) {
    }
}
