package dev.sg.portfolio.appointment;

import dev.sg.portfolio.domain.AppointmentChatRequest;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class AppointmentConversationTest {
    private final JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    private final AppointmentDemoService appointments = new AppointmentDemoService(db, 2);
    private final AppointmentFreeChatService chat = new AppointmentFreeChatService(appointments);
    private final String date = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString();

    private AppointmentFreeChatService.AppointmentFreeTurn say(String message) {
        return chat.prepare(new AppointmentChatRequest(message, "conversation", "traumatology"));
    }

    @Test void retainsBareNameThroughAvailabilityAndCorrections() {
        say("Hola");
        assertThat(say("Sebastian").fallbackReply()).contains("Sebastian");
        say("el " + date);
        say("10:00");
        assertThat(say("me equivoque, mi nombre es Pedro").fallbackReply()).contains("Pedro");
        say("mejor a las 12");
        assertThat(say("si confirmo").action()).isEqualTo("book");
        assertThat(db.queryForObject("SELECT patient_name FROM appointment_bookings WHERE demo_session_id = 'conversation'", String.class)).isEqualTo("Pedro");
        assertThat(db.queryForObject("SELECT start_at FROM appointment_bookings WHERE demo_session_id = 'conversation'", java.sql.Timestamp.class).toLocalDateTime().getHour()).isEqualTo(12);
    }

    @Test void correctsPersistedNameAndReschedulesWithOnlyNewTime() {
        say("soy Ana, reserva el " + date + " a las 10");
        assertThat(say("me equivoque, mi nombre es Lucia").action()).isEqualTo("update_name");
        assertThat(say("cambiar a las 12").action()).isEqualTo("reschedule");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_bookings WHERE demo_session_id = 'conversation'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT patient_name FROM appointment_bookings WHERE demo_session_id = 'conversation'", String.class)).isEqualTo("Lucia");
    }

    @Test void retainsNameWhenSelectingAnOfferedAlternative() {
        say("soy Ana");
        say("que horarios hay");
        String answer = say("la primera").fallbackReply();
        assertThat(answer).contains("Ana", "Confirmame");
        assertThat(say("confirmo").action()).isEqualTo("book");
    }

    @Test void negativeConfirmationDoesNotBookOrBecomeAName() {
        say("el " + date + " a las 10");
        say("Ana");
        assertThat(say("no confirmo, me equivoque").action()).isNotEqualTo("book");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_bookings WHERE demo_session_id = 'conversation'", Integer.class)).isZero();
        assertThat(say("mejor a las 12").fallbackReply()).contains("Ana");
    }

    @Test void handlesInvalidDatesAndPreservesName() {
        say("Ana");
        assertThat(say("31/02/2026 a las 25").action()).isEqualTo("error");
        assertThat(say("el " + date + " a las 10").fallbackReply()).contains("Ana");
    }

    @Test void neverConfusesNamesAcrossSessions() {
        say("Ana");
        chat.prepare(new AppointmentChatRequest("Pedro", "another-session", "traumatology"));
        assertThat(say("el " + date + " a las 10").fallbackReply()).contains("Ana").doesNotContain("Pedro");
    }

    @Test void correctsDateOfExistingReservationWithoutAskingNameAgain() {
        say("soy Ana, reserva el " + date + " a las 10");
        String nextDate = LocalDate.parse(date).plusDays(1).toString();
        assertThat(say("me equivoque, cambiar al " + nextDate).action()).isEqualTo("reschedule");
        assertThat(db.queryForObject("SELECT start_at FROM appointment_bookings WHERE demo_session_id = 'conversation'", java.sql.Timestamp.class)
                .toLocalDateTime().toLocalDate().toString()).isEqualTo(nextDate);
    }

    @Test void doesNotBookNegatedRequestEvenWithAllFields() {
        say("no reserves, soy Ana el " + date + " a las 10");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_bookings WHERE demo_session_id = 'conversation'", Integer.class)).isZero();
    }

    @Test void retainsDetailsWhenChosenSlotIsOccupied() {
        say("soy Ana el " + date + " a las 11");
        assertThat(say("confirmo").action()).isEqualTo("availability");
        say("12");
        assertThat(say("si").action()).isEqualTo("book");
        assertThat(db.queryForObject("SELECT patient_name FROM appointment_bookings WHERE demo_session_id = 'conversation'", String.class)).isEqualTo("Ana");
    }

    @Test void restoresPersistedReservationAfterConversationStateIsLost() {
        say("soy Ana, reserva el " + date + " a las 10");
        var restarted = new AppointmentFreeChatService(appointments);
        var turn = restarted.prepare(new AppointmentChatRequest("cambiar a las 12", "conversation", "traumatology"));
        assertThat(turn.action()).isEqualTo("reschedule");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_bookings WHERE demo_session_id = 'conversation'", Integer.class)).isEqualTo(1);
    }

    @Test void reproducesReportedVoiceConversationWithoutInventingPatientName() {
        say("Hola");
        say("Sebastián");
        say("a las 9 de la mañana");
        say("septiembre");
        String tuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).toString();
        say("para el " + tuesday);
        say("mi nombre no es Sebastián Mi nombre es Juancito");
        assertThat(say("Guárdalo").action()).isEqualTo("book");
        var moved = say("sabes que me equivoqué Necesito que muevas ese turno de las 9 de la mañana de la almohadas para las 12:30");
        assertThat(moved.action()).isEqualTo("reschedule");
        assertThat(moved.fallbackReply()).doesNotContain("para las. Confirmame");
        say("Guárdalo ya");
        assertThat(db.queryForObject("SELECT patient_name FROM appointment_bookings WHERE demo_session_id = 'conversation'", String.class)).isEqualTo("Juancito");
        assertThat(say("No yo soy Sebastián").action()).isEqualTo("update_name");
        say("horario a 12:30");
        say("guardarlo");
        say("guardar");
        assertThat(db.queryForObject("SELECT patient_name FROM appointment_bookings WHERE demo_session_id = 'conversation'", String.class)).isEqualTo("Sebastián");
        assertThat(db.queryForObject("SELECT start_at FROM appointment_bookings WHERE demo_session_id = 'conversation'", java.sql.Timestamp.class)
                .toLocalDateTime().toLocalTime().toString()).isEqualTo("12:30");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_bookings WHERE demo_session_id = 'conversation'", Integer.class)).isEqualTo(1);
    }

    @Test void numberedWeekdayUsesRequestedDayInsteadOfNearestWeekday() {
        LocalDate target = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).plusWeeks(1);
        say("soy Ana");
        say("para el martes " + target.getDayOfMonth() + " a las 9");
        assertThat(say("guardar").action()).isEqualTo("book");
        assertThat(db.queryForObject("SELECT start_at FROM appointment_bookings WHERE demo_session_id = 'conversation'", java.sql.Timestamp.class)
                .toLocalDateTime().toLocalDate()).isEqualTo(target);
    }

    @Test void infinitiveSaveConfirmsPendingTimeWithoutRenamingPatient() {
        say("soy Ana, reserva el " + date + " a las 10");
        say("horario a 12:30");
        assertThat(say("guardarlo").action()).isEqualTo("reschedule");
        say("horario a 12:00");
        assertThat(say("guardar").action()).isEqualTo("reschedule");
        assertThat(db.queryForObject("SELECT patient_name FROM appointment_bookings WHERE demo_session_id = 'conversation'", String.class)).isEqualTo("Ana");
    }

    @Test void picksDestinationWhenOldAndNewTimesAreBothSpoken() {
        say("soy Ana, reserva el " + date + " a las 10");
        assertThat(say("mover de las 10:00 para las 12:30").action()).isEqualTo("reschedule");
        assertThat(db.queryForObject("SELECT start_at FROM appointment_bookings WHERE demo_session_id = 'conversation'", java.sql.Timestamp.class)
                .toLocalDateTime().toLocalTime().toString()).isEqualTo("12:30");
    }

    @Test void nameCorrectionDoesNotDiscardTimeAwaitingConfirmation() {
        say("soy Ana, reserva el " + date + " a las 10");
        say("horario a 12:30");
        say("No yo soy Sebastián");
        assertThat(say("guardar").action()).isEqualTo("reschedule");
        assertThat(db.queryForObject("SELECT patient_name FROM appointment_bookings WHERE demo_session_id = 'conversation'", String.class)).isEqualTo("Sebastián");
    }
}
