package dev.sg.portfolio.domain;

public record FreeModelOffer(
        boolean enabled,
        String runtime,
        String model,
        String title,
        String message
) {
}
