package dev.sg.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "portfolio.free-model")
public record FreeModelProperties(
        boolean enabled,
        String baseUrl,
        String model
) {
    public boolean configured() {
        return enabled && StringUtils.hasText(baseUrl);
    }
}
