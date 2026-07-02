package dev.sg.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sg.portfolio.config.IpPromptLimitProperties;
import dev.sg.portfolio.domain.PromptLimitStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IpPromptLimitServiceTest {

    @Test
    void capsPromptsPerIpWhenEnabled() {
        IpPromptLimitService service = service(true, 2);

        PromptLimitStatus first = service.reservePrompt("203.0.113.10");
        PromptLimitStatus second = service.reservePrompt("203.0.113.10");
        PromptLimitStatus third = service.reservePrompt("203.0.113.10");

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
        assertThat(third.allowed()).isFalse();
        assertThat(third.used()).isEqualTo(2);
    }

    @Test
    void chargesFiveCreditsForVoiceMinutes() {
        IpPromptLimitService service = service(true, 6);

        PromptLimitStatus voice = service.reserveVoiceMinute("203.0.113.30");
        PromptLimitStatus chat = service.reservePrompt("203.0.113.30");
        PromptLimitStatus exhausted = service.reservePrompt("203.0.113.30");

        assertThat(voice.allowed()).isTrue();
        assertThat(voice.used()).isEqualTo(5);
        assertThat(voice.remaining()).isEqualTo(1);
        assertThat(chat.allowed()).isTrue();
        assertThat(chat.remaining()).isZero();
        assertThat(exhausted.allowed()).isFalse();
        assertThat(exhausted.used()).isEqualTo(6);
    }

    @Test
    void doesNotConsumeWhenRemainingCreditsAreBelowCost() {
        IpPromptLimitService service = service(true, 5);

        assertThat(service.reservePrompt("203.0.113.40").allowed()).isTrue();
        PromptLimitStatus rejectedVoice = service.reserveVoiceMinute("203.0.113.40");
        PromptLimitStatus finalChat = service.reservePrompt("203.0.113.40");

        assertThat(rejectedVoice.allowed()).isFalse();
        assertThat(rejectedVoice.used()).isEqualTo(1);
        assertThat(rejectedVoice.remaining()).isEqualTo(4);
        assertThat(finalChat.allowed()).isTrue();
        assertThat(finalChat.used()).isEqualTo(2);
    }

    @Test
    void ignoresPromptCapWhenDisabled() {
        IpPromptLimitService service = service(false, 1);

        assertThat(service.reservePrompt("203.0.113.20").allowed()).isTrue();
        assertThat(service.reservePrompt("203.0.113.20").allowed()).isTrue();
    }

    @Test
    void ignoresPersistedExhaustedUsageWhenDisabled() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        IpPromptLimitService enabledService = service(jdbcTemplate, true, 1);
        String clientIp = "203.0.113.55";

        assertThat(enabledService.reservePrompt(clientIp).allowed()).isTrue();
        assertThat(enabledService.reservePrompt(clientIp).allowed()).isFalse();

        IpPromptLimitService disabledService = service(jdbcTemplate, false, 1);
        PromptLimitStatus disabledStatus = disabledService.reservePrompt(clientIp);

        assertThat(disabledStatus.enabled()).isFalse();
        assertThat(disabledStatus.allowed()).isTrue();
        assertThat(disabledStatus.used()).isZero();
        assertThat(disabledStatus.remaining()).isEqualTo(1);
    }

    private IpPromptLimitService service(boolean enabled, int maxPrompts) {
        return service(new JdbcTemplate(dataSource()), enabled, maxPrompts);
    }

    private IpPromptLimitService service(JdbcTemplate jdbcTemplate, boolean enabled, int maxPrompts) {
        return new IpPromptLimitService(
                jdbcTemplate,
                new IpPromptLimitProperties(enabled, maxPrompts, 1, 5, 60)
        );
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
