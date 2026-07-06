package dev.sg.portfolio.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sg.portfolio.domain.AppointmentChatRequest;
import org.junit.jupiter.api.Test;

class AppointmentFreeChatServiceTest {

    private final AppointmentFreeChatService service = new AppointmentFreeChatService(null);

    @Test
    void keepsNaturalLanguageDateAsPendingState() {
        service.prepare(new AppointmentChatRequest("Hola Google", "session-a", "traumatology"));

        AppointmentFreeChatService.AppointmentFreeTurn turn = service.prepare(
                new AppointmentChatRequest("el 9 de julio", "session-a", "traumatology")
        );

        assertThat(turn.action()).isEqualTo("pending");
        assertThat(turn.fallbackReply()).contains("julio");
        assertThat(turn.fallbackReply()).contains("horario");
        assertThat(turn.fallbackReply()).doesNotContain("Estoy listo para buscar");
    }

    @Test
    void detectsFarewellAsEndCallAction() {
        AppointmentFreeChatService.AppointmentFreeTurn turn = service.prepare(
                new AppointmentChatRequest("gracias, chau", "session-b", "traumatology")
        );

        assertThat(turn.action()).isEqualTo("end_call");
        assertThat(turn.fallbackReply()).contains("Cierro la llamada");
    }
}
