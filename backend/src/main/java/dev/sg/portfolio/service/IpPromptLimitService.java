package dev.sg.portfolio.service;

import dev.sg.portfolio.config.IpPromptLimitProperties;
import dev.sg.portfolio.domain.PromptLimitStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IpPromptLimitService {

    private final JdbcTemplate jdbcTemplate;
    private final IpPromptLimitProperties properties;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);

    public IpPromptLimitService(JdbcTemplate jdbcTemplate, IpPromptLimitProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public PromptLimitStatus reservePrompt(String clientIp) {
        return reserveCredits(clientIp, properties.chatCost());
    }

    public PromptLimitStatus reserveVoiceMinute(String clientIp) {
        return reserveCredits(clientIp, properties.voiceMinuteCost());
    }

    public PromptLimitStatus voiceMinuteStatus(String clientIp) {
        return statusForCost(clientIp, properties.voiceMinuteCost());
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public int maxPrompts() {
        return properties.maxPrompts();
    }

    public int voiceMinuteCost() {
        return properties.voiceMinuteCost();
    }

    public PromptLimitStatus status(String clientIp) {
        return statusForCost(clientIp, properties.chatCost());
    }

    private PromptLimitStatus statusForCost(String clientIp, int cost) {
        int normalizedCost = Math.max(1, cost);
        int maxPrompts = properties.maxPrompts();
        if (!properties.enabled()) {
            return PromptLimitStatus.disabled(maxPrompts);
        }

        ensureSchema();
        String normalizedIp = normalize(clientIp);
        String ipHash = hash(normalizedIp);
        int used = loadPromptCount(ipHash);
        int remaining = Math.max(0, maxPrompts - used);
        return new PromptLimitStatus(
                true,
                remaining >= normalizedCost,
                used,
                remaining,
                maxPrompts
        );
    }

    public PromptLimitStatus reserveCredits(String clientIp, int cost) {
        int normalizedCost = Math.max(1, cost);
        int maxPrompts = properties.maxPrompts();
        if (!properties.enabled()) {
            return PromptLimitStatus.disabled(maxPrompts);
        }

        ensureSchema();
        String normalizedIp = normalize(clientIp);
        String ipHash = hash(normalizedIp);
        insertIfMissing(ipHash, normalizedIp);

        int updated = jdbcTemplate.update("""
                UPDATE ip_prompt_usage
                SET prompt_count = prompt_count + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE ip_hash = ?
                  AND prompt_count + ? <= ?
                """, normalizedCost, ipHash, normalizedCost, maxPrompts);

        Usage usage = loadUsage(ipHash);
        int remaining = Math.max(0, maxPrompts - usage.promptCount());
        return new PromptLimitStatus(true, updated > 0, usage.promptCount(), remaining, maxPrompts);
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
                    first_seen_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
    }

    private void insertIfMissing(String ipHash, String clientIp) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO ip_prompt_usage (ip_hash, client_ip, prompt_count, first_seen_at, updated_at)
                    VALUES (?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, ipHash, clientIp);
        } catch (DuplicateKeyException ignored) {
            // Existing visitor: keep first_seen_at and current counter intact.
        }
    }

    private Usage loadUsage(String ipHash) {
        return jdbcTemplate.queryForObject(
                "SELECT prompt_count FROM ip_prompt_usage WHERE ip_hash = ?",
                (resultSet, rowNum) -> toUsage(resultSet),
                ipHash
        );
    }

    private int loadPromptCount(String ipHash) {
        try {
            return loadUsage(ipHash).promptCount();
        } catch (EmptyResultDataAccessException ignored) {
            return 0;
        }
    }

    private Usage toUsage(ResultSet resultSet) throws SQLException {
        return new Usage(resultSet.getInt("prompt_count"));
    }

    private String normalize(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }

        return clientIp.trim().toLowerCase();
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

    private record Usage(int promptCount) {
    }
}
