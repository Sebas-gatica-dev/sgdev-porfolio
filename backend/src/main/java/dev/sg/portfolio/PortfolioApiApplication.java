package dev.sg.portfolio;

import dev.sg.portfolio.config.OpenAiProperties;
import dev.sg.portfolio.config.ContactMailProperties;
import dev.sg.portfolio.config.IpPromptLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        OpenAiProperties.class,
        IpPromptLimitProperties.class,
        ContactMailProperties.class
})
public class PortfolioApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApiApplication.class, args);
    }
}
