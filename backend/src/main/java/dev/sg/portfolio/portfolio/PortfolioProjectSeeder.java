package dev.sg.portfolio.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ejecuta el seed inicial de proyectos al arrancar, habilitado por
 * {@code portfolio.projects.seed-defaults}. Es idempotente: solo actúa con la tabla vacía y no
 * modifica datos existentes.
 */
@Component
public class PortfolioProjectSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PortfolioProjectSeeder.class);

    private final PortfolioProjectService projectService;
    private final boolean enabled;

    public PortfolioProjectSeeder(
            PortfolioProjectService projectService,
            @Value("${portfolio.projects.seed-defaults:true}") boolean enabled
    ) {
        this.projectService = projectService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            boolean inserted = projectService.seedArgentiCommerceIfEmpty();
            if (inserted) {
                log.info("Seed inicial de proyectos: ArgentiCommerce insertado en una tabla vacia.");
            }
        } catch (RuntimeException error) {
            log.warn("No pude ejecutar el seed inicial de proyectos: {}", error.getMessage());
        }
    }
}
