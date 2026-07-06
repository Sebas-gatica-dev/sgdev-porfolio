package dev.sg.portfolio.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sg.portfolio.domain.AppointmentEntry;
import dev.sg.portfolio.domain.AppointmentChatRequest;
import dev.sg.portfolio.domain.AppointmentMutationResponse;
import dev.sg.portfolio.domain.AppointmentSlotSuggestion;
import dev.sg.portfolio.domain.AvailabilitySearchRequest;
import dev.sg.portfolio.domain.AvailabilitySearchResponse;
import dev.sg.portfolio.domain.BookAppointmentRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    @Test
    void booksWhenNameAndConfirmationArriveTogether() {
        AppointmentDemoService demoService = mock(AppointmentDemoService.class);
        when(demoService.searchAvailability(any())).thenReturn(availableAtTen());
        when(demoService.book(any())).thenReturn(bookedAtTen());
        AppointmentFreeChatService freeChat = new AppointmentFreeChatService(demoService);

        freeChat.prepare(new AppointmentChatRequest("el 9 de julio", "session-c", "traumatology"));
        freeChat.prepare(new AppointmentChatRequest("a las 10", "session-c", "traumatology"));
        AppointmentFreeChatService.AppointmentFreeTurn turn = freeChat.prepare(
                new AppointmentChatRequest("Sebastian confirmo", "session-c", "traumatology")
        );

        ArgumentCaptor<BookAppointmentRequest> captor = ArgumentCaptor.forClass(BookAppointmentRequest.class);
        verify(demoService).book(captor.capture());
        assertThat(turn.action()).isEqualTo("book");
        assertThat(captor.getValue().patientName()).isEqualTo("Sebastian");
    }

    @Test
    void doesNotTreatPlainConfirmationAsPatientName() {
        AppointmentDemoService demoService = mock(AppointmentDemoService.class);
        when(demoService.searchAvailability(any())).thenReturn(availableAtTen());
        AppointmentFreeChatService freeChat = new AppointmentFreeChatService(demoService);

        freeChat.prepare(new AppointmentChatRequest("el 9 de julio", "session-d", "traumatology"));
        freeChat.prepare(new AppointmentChatRequest("a las 10", "session-d", "traumatology"));
        AppointmentFreeChatService.AppointmentFreeTurn turn = freeChat.prepare(
                new AppointmentChatRequest("ok", "session-d", "traumatology")
        );

        assertThat(turn.action()).isEqualTo("pending");
        assertThat(turn.fallbackReply()).contains("nombre de pila");
    }

    @Test
    void searchesAvailabilityForBroadAfternoonPreference() {
        AppointmentDemoService demoService = mock(AppointmentDemoService.class);
        when(demoService.searchAvailability(any())).thenReturn(new AvailabilitySearchResponse(
                "traumatology",
                "WINDOW_SEARCH",
                "Busqueda por rango.",
                List.of(new AppointmentSlotSuggestion(
                        "trauma",
                        "Dr. Hernan Varela",
                        "Traumatologo",
                        "2026-07-09T14:00:00",
                        "2026-07-09T14:30:00"
                ))
        ));
        AppointmentFreeChatService freeChat = new AppointmentFreeChatService(demoService);

        AppointmentFreeChatService.AppointmentFreeTurn turn = freeChat.prepare(
                new AppointmentChatRequest("el 9 de julio por la tarde", "session-e", "traumatology")
        );

        ArgumentCaptor<AvailabilitySearchRequest> captor = ArgumentCaptor.forClass(AvailabilitySearchRequest.class);
        verify(demoService).searchAvailability(captor.capture());
        assertThat(turn.action()).isEqualTo("availability");
        assertThat(captor.getValue().preferredTimeFrom()).isEqualTo("14:00");
        assertThat(captor.getValue().preferredTimeTo()).isEqualTo("18:00");
    }

    private AvailabilitySearchResponse availableAtTen() {
        return new AvailabilitySearchResponse(
                "traumatology",
                "AVAILABLE",
                "Ese horario esta libre.",
                List.of(new AppointmentSlotSuggestion(
                        "trauma",
                        "Dr. Hernan Varela",
                        "Traumatologo",
                        "2026-07-09T10:00:00",
                        "2026-07-09T10:30:00"
                ))
        );
    }

    private AppointmentMutationResponse bookedAtTen() {
        return new AppointmentMutationResponse(
                "BOOKED",
                new AppointmentEntry(
                        "appointment-1",
                        "trauma",
                        "traumatology",
                        "Dr. Hernan Varela",
                        "Traumatologo",
                        "Sebastian",
                        "2026-07-09T10:00:00",
                        "2026-07-09T10:30:00",
                        true
                )
        );
    }
}
