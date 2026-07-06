package dev.sg.portfolio.appointment;

import dev.sg.portfolio.domain.AppointmentActivity;
import dev.sg.portfolio.domain.AppointmentCalendarDay;
import dev.sg.portfolio.domain.AppointmentDoctor;
import dev.sg.portfolio.domain.AppointmentEntry;
import dev.sg.portfolio.domain.AppointmentMutationResponse;
import dev.sg.portfolio.domain.AppointmentScheduleResponse;
import dev.sg.portfolio.domain.AppointmentSlotSuggestion;
import dev.sg.portfolio.domain.AvailabilitySearchRequest;
import dev.sg.portfolio.domain.AvailabilitySearchResponse;
import dev.sg.portfolio.domain.BookAppointmentRequest;
import dev.sg.portfolio.domain.RescheduleAppointmentRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AppointmentDemoService {

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(13, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(14, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(18, 0);
    private static final int SLOT_MINUTES = 30;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final List<DoctorSeed> DOCTORS = List.of(
            new DoctorSeed("trauma", "traumatology", "Dr. Hernan Varela", "Traumatologo"),
            new DoctorSeed("control", "follow-up", "Dra. Paula Mendez", "Consulta de control"),
            new DoctorSeed("cardio", "cardiology", "Dr. Tomas Ibarra", "Cardiologo")
    );

    private final JdbcTemplate jdbcTemplate;
    private final int cleanupRetentionDays;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);
    private final Object schemaLock = new Object();

    public AppointmentDemoService(
            JdbcTemplate jdbcTemplate,
            @Value("${portfolio.appointment-demo.cleanup-retention-days:2}") int cleanupRetentionDays
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanupRetentionDays = Math.max(1, cleanupRetentionDays);
    }

    public AppointmentScheduleResponse schedule(String sessionId, int requestedDays) {
        ensureSchema();
        int days = Math.max(1, Math.min(30, requestedDays));
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(days - 1L);
        ensureSeededRange(start, end);

        List<AppointmentDoctor> doctors = doctors();
        List<AppointmentEntry> appointments = jdbcTemplate.query("""
                        SELECT a.id, a.doctor_id, a.consultation_type, d.display_name, d.specialty,
                               a.patient_name, a.start_at, a.end_at, a.demo_session_id
                        FROM appointment_bookings a
                        JOIN appointment_doctors d ON d.id = a.doctor_id
                        WHERE a.status = 'ACTIVE'
                          AND a.start_at >= ?
                          AND a.start_at < ?
                        ORDER BY a.start_at, d.display_name
                        """,
                (resultSet, rowNum) -> toAppointmentEntry(resultSet, sessionId),
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );

        Map<LocalDate, List<AppointmentEntry>> byDay = new LinkedHashMap<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = start.plusDays(offset);
            byDay.put(date, new ArrayList<>());
        }
        appointments.forEach(appointment -> byDay
                .get(LocalDateTime.parse(appointment.startAt()).toLocalDate())
                .add(appointment));

        List<AppointmentCalendarDay> calendarDays = byDay.entrySet().stream()
                .map(entry -> new AppointmentCalendarDay(
                        entry.getKey().toString(),
                        isWorkingDay(entry.getKey()),
                        entry.getValue()
                ))
                .toList();

        return new AppointmentScheduleResponse(
                doctors,
                calendarDays,
                WORKDAY_START.toString(),
                LUNCH_START.toString(),
                LUNCH_END.toString(),
                WORKDAY_END.toString()
        );
    }

    public List<AppointmentActivity> activity(String sessionId, int requestedLimit) {
        ensureSchema();
        int limit = Math.max(1, Math.min(30, requestedLimit));
        return jdbcTemplate.query("""
                        SELECT action, detail, created_at
                        FROM appointment_audit_log
                        WHERE demo_session_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """,
                (resultSet, rowNum) -> new AppointmentActivity(
                        resultSet.getString("action"),
                        resultSet.getString("detail"),
                        resultSet.getTimestamp("created_at").toLocalDateTime().format(DATE_TIME_FORMATTER)
                ),
                normalizeSessionId(sessionId),
                limit
        );
    }

    public AvailabilitySearchResponse searchAvailability(AvailabilitySearchRequest request) {
        ensureSchema();
        String sessionId = normalizeSessionId(request.sessionId());
        String consultationType = normalizeConsultationType(request.consultationType());
        LocalDate dateFrom = parseDate(request.dateFrom(), LocalDate.now());
        LocalDate dateTo = parseDate(request.dateTo(), dateFrom.plusDays(14));
        if (dateTo.isBefore(dateFrom)) {
            dateTo = dateFrom;
        }
        LocalTime preferredTimeFrom = parseTime(request.preferredTimeFrom());
        LocalTime preferredTimeTo = parseTime(request.preferredTimeTo());
        ensureSeededRange(dateFrom, dateTo);

        List<AppointmentSlotSuggestion> suggestions = availableSlots(
                consultationType,
                dateFrom,
                dateTo,
                preferredTimeFrom,
                preferredTimeTo,
                6
        );
        if (suggestions.isEmpty() && preferredTimeFrom != null) {
            suggestions = availableSlots(
                    consultationType,
                    dateFrom,
                    dateTo,
                    null,
                    null,
                    6
            );
        }
        RequestedSlotStatus requestedSlot = requestedSlotStatus(
                consultationType,
                dateFrom,
                dateTo,
                preferredTimeFrom,
                preferredTimeTo
        );

        logActivity(sessionId, "SELECT", "Consulta de disponibilidad para " + readableConsultation(consultationType)
                + " entre " + dateFrom + " y " + dateTo + ".");

        return new AvailabilitySearchResponse(
                consultationType,
                requestedSlot.status(),
                requestedSlot.reason(),
                suggestions
        );
    }

    public AppointmentMutationResponse book(BookAppointmentRequest request) {
        ensureSchema();
        String sessionId = normalizeSessionId(request.sessionId());
        String consultationType = normalizeConsultationType(request.consultationType());
        String patientName = normalizePatientName(request.patientName());
        LocalDateTime startAt = parseStartAt(request.startAt());
        validateBookableSlot(consultationType, startAt, null);
        AppointmentDoctor doctor = doctorForConsultation(consultationType);

        String id = UUID.randomUUID().toString();
        LocalDateTime endAt = startAt.plusMinutes(SLOT_MINUTES);
        try {
            jdbcTemplate.update("""
                    INSERT INTO appointment_bookings (
                        id, demo_session_id, consultation_type, doctor_id, patient_name,
                        start_at, end_at, source, status, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'agent', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    id,
                    sessionId,
                    consultationType,
                    doctor.id(),
                    patientName,
                    startAt,
                    endAt
            );
        } catch (DuplicateKeyException error) {
            throw new IllegalArgumentException("Ese horario acaba de ocuparse. Pedi otra alternativa.");
        }

        logActivity(sessionId, "INSERT", "Turno guardado para " + doctor.name() + " el "
                + startAt.toLocalDate() + " a las " + startAt.toLocalTime() + ".");
        return new AppointmentMutationResponse("BOOKED", loadAppointment(id, sessionId));
    }

    public AppointmentMutationResponse reschedule(RescheduleAppointmentRequest request) {
        ensureSchema();
        String sessionId = normalizeSessionId(request.sessionId());
        ExistingAppointment existing = currentAppointment(sessionId);
        if (existing == null) {
            throw new IllegalArgumentException("Todavia no hay un turno activo para reprogramar en esta llamada.");
        }

        LocalDateTime nextStart = parseStartAt(request.startAt());
        validateBookableSlot(existing.consultationType(), nextStart, existing.id());
        LocalDateTime nextEnd = nextStart.plusMinutes(SLOT_MINUTES);
        int updated;
        try {
            updated = jdbcTemplate.update("""
                    UPDATE appointment_bookings
                    SET start_at = ?, end_at = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND status = 'ACTIVE'
                    """,
                    nextStart,
                    nextEnd,
                    existing.id()
            );
        } catch (DuplicateKeyException error) {
            throw new IllegalArgumentException("Ese horario acaba de ocuparse. Pedi otra alternativa.");
        }
        if (updated == 0) {
            throw new IllegalArgumentException("No pude encontrar el turno activo de esta llamada.");
        }

        logActivity(sessionId, "UPDATE", "Turno reprogramado del " + existing.startAt().toLocalDate()
                + " a las " + existing.startAt().toLocalTime() + " al " + nextStart.toLocalDate()
                + " a las " + nextStart.toLocalTime() + ".");
        return new AppointmentMutationResponse("RESCHEDULED", loadAppointment(existing.id(), sessionId));
    }

    @Scheduled(cron = "${portfolio.appointment-demo.cleanup-cron:0 20 4 * * *}")
    public void cleanupExpiredDemoData() {
        ensureSchema();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cleanupRetentionDays);
        jdbcTemplate.update("""
                DELETE FROM appointment_audit_log
                WHERE created_at < ?
                """, cutoff);
        jdbcTemplate.update("""
                DELETE FROM appointment_bookings
                WHERE end_at < ?
                """, cutoff);
    }

    private void ensureSchema() {
        if (schemaReady.get()) {
            return;
        }

        synchronized (schemaLock) {
            if (schemaReady.get()) {
                return;
            }

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS appointment_doctors (
                        id VARCHAR(32) PRIMARY KEY,
                        consultation_type VARCHAR(32) NOT NULL UNIQUE,
                        display_name VARCHAR(120) NOT NULL,
                        specialty VARCHAR(120) NOT NULL
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS appointment_bookings (
                        id VARCHAR(36) PRIMARY KEY,
                        demo_session_id VARCHAR(64) NOT NULL,
                        consultation_type VARCHAR(32) NOT NULL,
                        doctor_id VARCHAR(32) NOT NULL REFERENCES appointment_doctors(id),
                        patient_name VARCHAR(120) NOT NULL,
                        start_at TIMESTAMP NOT NULL,
                        end_at TIMESTAMP NOT NULL,
                        source VARCHAR(32) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        CONSTRAINT appointment_unique_doctor_slot UNIQUE (doctor_id, start_at)
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS appointment_audit_log (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        demo_session_id VARCHAR(64) NOT NULL,
                        action VARCHAR(24) NOT NULL,
                        detail VARCHAR(320) NOT NULL,
                        created_at TIMESTAMP NOT NULL
                    )
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_appointment_bookings_start_at
                    ON appointment_bookings(start_at)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_appointment_audit_session_created
                    ON appointment_audit_log(demo_session_id, created_at)
                    """);

            for (DoctorSeed doctor : DOCTORS) {
                try {
                    jdbcTemplate.update("""
                            INSERT INTO appointment_doctors (id, consultation_type, display_name, specialty)
                            VALUES (?, ?, ?, ?)
                            """,
                            doctor.id(),
                            doctor.consultationType(),
                            doctor.name(),
                            doctor.specialty()
                    );
                } catch (DuplicateKeyException ignored) {
                    // La demo puede reiniciarse sobre una base ya sembrada.
                }
            }
            schemaReady.set(true);
        }
    }

    private void ensureSeededRange(LocalDate start, LocalDate end) {
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (!isWorkingDay(day)) {
                continue;
            }
            for (DoctorSeed doctor : DOCTORS) {
                for (LocalTime time : seededTimesFor(doctor.id(), day.getDayOfWeek())) {
                    LocalDateTime startAt = LocalDateTime.of(day, time);
                    LocalDateTime endAt = startAt.plusMinutes(SLOT_MINUTES);
                    try {
                        jdbcTemplate.update("""
                                INSERT INTO appointment_bookings (
                                    id, demo_session_id, consultation_type, doctor_id, patient_name,
                                    start_at, end_at, source, status, created_at, updated_at
                                )
                                VALUES (?, 'seed', ?, ?, 'Turno ocupado', ?, ?, 'seed', 'ACTIVE',
                                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                                """,
                                UUID.randomUUID().toString(),
                                doctor.consultationType(),
                                doctor.id(),
                                startAt,
                                endAt
                        );
                    } catch (DuplicateKeyException ignored) {
                        // Otro hilo pudo sembrar el mismo horario.
                    }
                }
            }
        }
    }

    private List<AppointmentDoctor> doctors() {
        return jdbcTemplate.query("""
                        SELECT id, consultation_type, display_name, specialty
                        FROM appointment_doctors
                        ORDER BY display_name
                        """,
                (resultSet, rowNum) -> new AppointmentDoctor(
                        resultSet.getString("id"),
                        resultSet.getString("consultation_type"),
                        resultSet.getString("display_name"),
                        resultSet.getString("specialty")
                )
        );
    }

    private AppointmentDoctor doctorForConsultation(String consultationType) {
        return jdbcTemplate.queryForObject("""
                        SELECT id, consultation_type, display_name, specialty
                        FROM appointment_doctors
                        WHERE consultation_type = ?
                        """,
                (resultSet, rowNum) -> new AppointmentDoctor(
                        resultSet.getString("id"),
                        resultSet.getString("consultation_type"),
                        resultSet.getString("display_name"),
                        resultSet.getString("specialty")
                ),
                consultationType
        );
    }

    private List<AppointmentSlotSuggestion> availableSlots(
            String consultationType,
            LocalDate dateFrom,
            LocalDate dateTo,
            LocalTime preferredTimeFrom,
            LocalTime preferredTimeTo,
            int limit
    ) {
        AppointmentDoctor doctor = doctorForConsultation(consultationType);
        List<AppointmentSlotSuggestion> suggestions = new ArrayList<>();
        LocalDateTime earliestAllowed = LocalDateTime.now().plusMinutes(30);

        for (LocalDate day = dateFrom; !day.isAfter(dateTo) && suggestions.size() < limit; day = day.plusDays(1)) {
            if (!isWorkingDay(day)) {
                continue;
            }
            List<LocalTime> candidates = allWorkingSlots().stream()
                    .filter(time -> preferredTimeFrom == null || !time.isBefore(preferredTimeFrom))
                    .filter(time -> preferredTimeTo == null || !time.plusMinutes(SLOT_MINUTES).isAfter(preferredTimeTo))
                    .toList();
            for (LocalTime candidate : candidates) {
                LocalDateTime startAt = LocalDateTime.of(day, candidate);
                if (startAt.isBefore(earliestAllowed)) {
                    continue;
                }
                if (slotAvailable(doctor.id(), startAt, null)) {
                    suggestions.add(new AppointmentSlotSuggestion(
                            doctor.id(),
                            doctor.name(),
                            doctor.specialty(),
                            startAt.format(DATE_TIME_FORMATTER),
                            startAt.plusMinutes(SLOT_MINUTES).format(DATE_TIME_FORMATTER)
                    ));
                    if (suggestions.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return suggestions;
    }

    private RequestedSlotStatus requestedSlotStatus(
            String consultationType,
            LocalDate dateFrom,
            LocalDate dateTo,
            LocalTime preferredTimeFrom,
            LocalTime preferredTimeTo
    ) {
        if (!dateFrom.equals(dateTo) || preferredTimeFrom == null) {
            return new RequestedSlotStatus("WINDOW_SEARCH", "Busqueda por rango; no hubo un unico horario exacto.");
        }
        if (!isWorkingDay(dateFrom)) {
            return new RequestedSlotStatus("NOT_WORKDAY", "Ese dia no hay atencion porque solo se trabaja de lunes a viernes.");
        }
        LocalDateTime requestedStart = LocalDateTime.of(dateFrom, preferredTimeFrom);
        if (!withinWorkingHours(preferredTimeFrom)) {
            return new RequestedSlotStatus("OUTSIDE_HOURS", "Ese horario cae fuera de la agenda medica disponible.");
        }
        if (requestedStart.isBefore(LocalDateTime.now().plusMinutes(30))) {
            return new RequestedSlotStatus("PAST", "Ese horario ya no esta disponible porque quedo demasiado cerca o en el pasado.");
        }
        if (preferredTimeTo != null && preferredTimeTo.isBefore(preferredTimeFrom.plusMinutes(SLOT_MINUTES))) {
            return new RequestedSlotStatus("INVALID_RANGE", "El rango pedido no alcanza para un turno de 30 minutos.");
        }

        AppointmentDoctor doctor = doctorForConsultation(consultationType);
        if (slotAvailable(doctor.id(), requestedStart, null)) {
            return new RequestedSlotStatus("AVAILABLE", "Ese horario esta libre.");
        }
        return new RequestedSlotStatus("OCCUPIED", "Ese horario ya figura ocupado en la agenda.");
    }

    private void validateBookableSlot(String consultationType, LocalDateTime startAt, String ignoredAppointmentId) {
        if (!isWorkingDay(startAt.toLocalDate())) {
            throw new IllegalArgumentException("Solo se pueden reservar turnos de lunes a viernes.");
        }
        if (!withinWorkingHours(startAt.toLocalTime())) {
            throw new IllegalArgumentException("Los turnos solo pueden iniciar entre 08:00-12:30 o 14:00-17:30.");
        }
        if (startAt.isBefore(LocalDateTime.now().plusMinutes(30))) {
            throw new IllegalArgumentException("Ese horario ya no esta disponible.");
        }
        AppointmentDoctor doctor = doctorForConsultation(consultationType);
        if (!slotAvailable(doctor.id(), startAt, ignoredAppointmentId)) {
            throw new IllegalArgumentException("Ese horario ya esta ocupado.");
        }
    }

    private boolean slotAvailable(String doctorId, LocalDateTime startAt, String ignoredAppointmentId) {
        LocalDateTime endAt = startAt.plusMinutes(SLOT_MINUTES);
        Integer count = ignoredAppointmentId == null
                ? jdbcTemplate.queryForObject("""
                                SELECT COUNT(*)
                                FROM appointment_bookings
                                WHERE doctor_id = ?
                                  AND start_at < ?
                                  AND end_at > ?
                                  AND status = 'ACTIVE'
                                """,
                        Integer.class,
                        doctorId,
                        endAt,
                        startAt
                )
                : jdbcTemplate.queryForObject("""
                                SELECT COUNT(*)
                                FROM appointment_bookings
                                WHERE doctor_id = ?
                                  AND start_at < ?
                                  AND end_at > ?
                                  AND status = 'ACTIVE'
                                  AND id <> ?
                                """,
                        Integer.class,
                        doctorId,
                        endAt,
                        startAt,
                        ignoredAppointmentId
                );
        return count != null && count == 0;
    }

    private AppointmentEntry loadAppointment(String id, String sessionId) {
        return jdbcTemplate.queryForObject("""
                        SELECT a.id, a.doctor_id, a.consultation_type, d.display_name, d.specialty,
                               a.patient_name, a.start_at, a.end_at, a.demo_session_id
                        FROM appointment_bookings a
                        JOIN appointment_doctors d ON d.id = a.doctor_id
                        WHERE a.id = ?
                        """,
                (resultSet, rowNum) -> toAppointmentEntry(resultSet, sessionId),
                id
        );
    }

    private ExistingAppointment currentAppointment(String sessionId) {
        List<ExistingAppointment> appointments = jdbcTemplate.query("""
                        SELECT id, consultation_type, start_at
                        FROM appointment_bookings
                        WHERE demo_session_id = ?
                          AND status = 'ACTIVE'
                        ORDER BY updated_at DESC, created_at DESC
                        LIMIT 1
                        """,
                (resultSet, rowNum) -> new ExistingAppointment(
                        resultSet.getString("id"),
                        resultSet.getString("consultation_type"),
                        resultSet.getTimestamp("start_at").toLocalDateTime()
                ),
                sessionId
        );
        return appointments.isEmpty() ? null : appointments.get(0);
    }

    private AppointmentEntry toAppointmentEntry(ResultSet resultSet, String sessionId) throws SQLException {
        String ownerSession = resultSet.getString("demo_session_id");
        return new AppointmentEntry(
                resultSet.getString("id"),
                resultSet.getString("doctor_id"),
                resultSet.getString("consultation_type"),
                resultSet.getString("display_name"),
                resultSet.getString("specialty"),
                resultSet.getString("patient_name"),
                resultSet.getTimestamp("start_at").toLocalDateTime().format(DATE_TIME_FORMATTER),
                resultSet.getTimestamp("end_at").toLocalDateTime().format(DATE_TIME_FORMATTER),
                StringUtils.hasText(sessionId) && sessionId.equals(ownerSession)
        );
    }

    private void logActivity(String sessionId, String action, String detail) {
        jdbcTemplate.update("""
                INSERT INTO appointment_audit_log (demo_session_id, action, detail, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                normalizeSessionId(sessionId),
                action,
                detail
        );
    }

    private List<LocalTime> allWorkingSlots() {
        List<LocalTime> slots = new ArrayList<>();
        for (LocalTime time = WORKDAY_START; time.isBefore(LUNCH_START); time = time.plusMinutes(SLOT_MINUTES)) {
            slots.add(time);
        }
        for (LocalTime time = LUNCH_END; time.isBefore(WORKDAY_END); time = time.plusMinutes(SLOT_MINUTES)) {
            slots.add(time);
        }
        return slots;
    }

    private boolean withinWorkingHours(LocalTime time) {
        return (!time.isBefore(WORKDAY_START) && time.isBefore(LUNCH_START))
                || (!time.isBefore(LUNCH_END) && time.isBefore(WORKDAY_END));
    }

    private boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private List<LocalTime> seededTimesFor(String doctorId, DayOfWeek dayOfWeek) {
        int weekdayShift = dayOfWeek.getValue() % 3;
        if ("trauma".equals(doctorId)) {
            return List.of(
                    List.of(LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30)).get(weekdayShift),
                    LocalTime.of(11, 0),
                    LocalTime.of(15, 30)
            );
        }
        if ("control".equals(doctorId)) {
            return List.of(
                    LocalTime.of(9, 0),
                    List.of(LocalTime.of(11, 30), LocalTime.of(12, 0), LocalTime.of(12, 30)).get(weekdayShift),
                    LocalTime.of(16, 0)
            );
        }
        return List.of(
                List.of(LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 30)).get(weekdayShift),
                LocalTime.of(14, 30),
                LocalTime.of(17, 0)
        );
    }

    private String normalizeConsultationType(String consultationType) {
        String normalized = StringUtils.hasText(consultationType) ? consultationType.trim().toLowerCase() : "";
        boolean supported = DOCTORS.stream().anyMatch(doctor -> doctor.consultationType().equals(normalized));
        if (!supported) {
            throw new IllegalArgumentException("Tipo de consulta no soportado.");
        }
        return normalized;
    }

    private String readableConsultation(String consultationType) {
        return switch (consultationType) {
            case "traumatology" -> "consulta con traumatologo";
            case "follow-up" -> "consulta de control";
            case "cardiology" -> "consulta con cardiologo";
            default -> consultationType;
        };
    }

    private String normalizePatientName(String patientName) {
        if (!StringUtils.hasText(patientName)) {
            throw new IllegalArgumentException("Falta el nombre del paciente.");
        }
        return patientName.trim();
    }

    private String normalizeSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId.trim() : "anonymous-demo";
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        return StringUtils.hasText(value) ? LocalDate.parse(value.trim()) : fallback;
    }

    private LocalTime parseTime(String value) {
        return StringUtils.hasText(value) ? LocalTime.parse(value.trim()) : null;
    }

    private LocalDateTime parseStartAt(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Falta el horario del turno.");
        }
        return LocalDateTime.parse(value.trim());
    }

    private record DoctorSeed(String id, String consultationType, String name, String specialty) {
    }

    private record RequestedSlotStatus(String status, String reason) {
    }

    private record ExistingAppointment(String id, String consultationType, LocalDateTime startAt) {
    }
}
