package dev.sg.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "contact.mail")
public record ContactMailProperties(
        String toAddress,
        String fromAddress,
        String mailtrapToken,
        String mailtrapBaseUrl,
        String mailtrapInboxId
) {
    public boolean mailtrapReady() {
        return StringUtils.hasText(toAddress)
                && StringUtils.hasText(fromAddress)
                && StringUtils.hasText(mailtrapToken);
    }
}
