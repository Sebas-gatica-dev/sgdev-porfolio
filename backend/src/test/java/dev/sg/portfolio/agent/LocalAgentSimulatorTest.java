package dev.sg.portfolio.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalAgentSimulatorTest {

    private final LocalAgentSimulator simulator = new LocalAgentSimulator();

    @Test
    void usesCvFallbackForExplicitProfileQuestions() {
        assertThat(simulator.shouldUseCvBackedProfileFallback(
                "Resumime la experiencia laboral de Sebastian Gatica."
        )).isTrue();
        assertThat(simulator.shouldUseCvBackedProfileFallback(
                "Cual es el stack tecnico de Sebastian?"
        )).isTrue();
        assertThat(simulator.shouldUseCvBackedProfileFallback(
                "Tenes el CV actualizado?"
        )).isTrue();
    }

    @Test
    void avoidsCvFallbackForCurrentProjectQuestions() {
        assertThat(simulator.shouldUseCvBackedProfileFallback(
                "Que tecnologias usa este proyecto?"
        )).isFalse();
        assertThat(simulator.shouldUseCvBackedProfileFallback(
                "Analiza este proyecto React y su arquitectura."
        )).isFalse();
        assertThat(simulator.shouldUseCvBackedProfileFallback(
                "Necesito revisar el codigo del repositorio."
        )).isFalse();
    }

    @Test
    void localProfileAnswerUsesCvSummary() {
        String answer = simulator.buildAnswer(
                "Contame la experiencia laboral de Sebastian.",
                "fallback de prueba"
        );

        assertThat(answer)
                .contains("Modo demo local")
                .contains("Sebastian Gatica")
                .contains("más de 2 años")
                .doesNotContain("Input recibido");
    }

    @Test
    void qwenProfileAnswerDoesNotNestDemoFallback() {
        String answer = simulator.buildFreeModelAnswer(
                "Contame la experiencia laboral de Sebastian.",
                "qwen3:0.6b",
                "timeout de prueba"
        );

        assertThat(answer)
                .contains("Modo Qwen local")
                .contains("Sebastian Gatica")
                .doesNotContain("Modo demo local");
        assertThat(answer.indexOf("Motivo:")).isEqualTo(answer.lastIndexOf("Motivo:"));
    }
}
