package dev.sg.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient openAiWebClient(OpenAiProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    WebClient mailtrapWebClient(ContactMailProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.mailtrapBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.mailtrapToken())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    WebClient freeModelWebClient(FreeModelProperties properties) {
        String baseUrl = properties.baseUrl() == null || properties.baseUrl().isBlank()
                ? "http://localhost:8796"
                : properties.baseUrl();
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (properties.appSlug() != null && !properties.appSlug().isBlank()) {
            builder.defaultHeader("X-SGInfra-App-Slug", properties.appSlug());
        }
        if (properties.appToken() != null && !properties.appToken().isBlank()) {
            builder.defaultHeader("X-SGInfra-App-Token", properties.appToken());
        }
        return builder.build();
    }
}
