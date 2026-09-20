package dev.sg.portfolio.appointment;

import dev.sg.portfolio.domain.AgentTrace;
import dev.sg.portfolio.domain.AppointmentChatRequest;
import dev.sg.portfolio.domain.AppointmentMutationResponse;
import dev.sg.portfolio.domain.AppointmentSlotSuggestion;
import dev.sg.portfolio.domain.AvailabilitySearchRequest;
import dev.sg.portfolio.domain.AvailabilitySearchResponse;
import dev.sg.portfolio.domain.BookAppointmentRequest;
import dev.sg.portfolio.domain.RescheduleAppointmentRequest;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import dev.sg.portfolio.service.FreeModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;

@Component
public class AppointmentFreeChatService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern SHORT_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile(
            "\\b(?:el\\s+)?(?:dia\\s+)?(?:(\\d{1,2})|(uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintidos|veintitres|veinticuatro|veinticinco|veintiseis|veintisiete|veintiocho|veintinueve|treinta|treinta\\s+y\\s+uno))\\s+(?:de\\s+)?(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\b(?:a\\s+las|para\\s+las|las|tipo|sobre\\s+las|desde|despues\\s+de|antes\\s+de)\\s+(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:hs?|horas?)?\\b"
    );
    private static final Pattern WORD_TIME_PATTERN = Pattern.compile(
            "\\b(?:a\\s+las|para\\s+las|las|tipo|sobre\\s+las|desde|despues\\s+de|antes\\s+de)\\s+(una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\\s+y\\s+(media|treinta|cuarto|quince))?\\s*(?:de\\s+la\\s+(manana|tarde|noche))?\\b"
    );
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?iu)\\b(?:me llamo|mi nombre es|soy|a nombre de|para)\\s+([\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){0,2})"
    );
    private static final Pattern PATIENT_NAME_PATTERN = Pattern.compile(
            "(?iu)\\b(?:para\\s+(?:el|la)\\s+paciente|paciente|a\\s+nombre\\s+de|me\\s+llamo|mi\\s+nombre\\s+es|soy)\\s+([\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){0,3})"
    );
    private static final Pattern REVERSED_NAME_PATTERN = Pattern.compile(
            "(?iu)\\b([\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){0,2})\\s+es\\s+mi\\s+nombre\\b"
    );
    private static final Pattern FAREWELL_PATTERN = Pattern.compile(
            "\\b(?:chau|adios|hasta\\s+luego|nos\\s+vemos|gracias\\s+eso\\s+es\\s+todo|eso\\s+es\\s+todo|corta(?:r)?\\s+la\\s+llamada|termina(?:r)?\\s+la\\s+llamada)\\b"
    );
    private static final Pattern GRATITUDE_PATTERN = Pattern.compile(
            "\\b(?:gracias|muchas\\s+gracias|te\\s+agradezco)\\b"
    );

    private final AppointmentDemoService appointmentDemoService;
    private final Map<String, String> requestedTypes = new ConcurrentHashMap<>();
    private final Map<String, String> conversationTypes = new ConcurrentHashMap<>();
    private final Map<String, PendingAppointment> pendingAppointments = new ConcurrentHashMap<>();
    private final Map<String, CompletedAppointment> completedAppointments = new ConcurrentHashMap<>();
    private final Map<String, List<AppointmentSlotSuggestion>> offeredSlots = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    public AppointmentFreeChatService(AppointmentDemoService appointmentDemoService) {
        this.appointmentDemoService = appointmentDemoService;
    }

    public synchronized AppointmentFreeTurn prepare(AppointmentChatRequest request) {
        String message = request == null || request.message() == null ? "" : request.message().trim();
        String sessionId = normalizeSessionId(request == null ? null : request.sessionId());
        long now = System.currentTimeMillis();
        lastSeen.entrySet().removeIf(entry -> {
            if (now - entry.getValue() < Duration.ofHours(2).toMillis()) return false;
            pendingAppointments.remove(entry.getKey());
            completedAppointments.remove(entry.getKey());
            offeredSlots.remove(entry.getKey());
            return true;
        });
        requestedTypes.keySet().removeIf(id -> lastSeen.keySet().stream().noneMatch(key -> key.startsWith(id + "|")));
        conversationTypes.keySet().retainAll(requestedTypes.keySet());
        String requestedType = selectConsultationType(request == null ? null : request.consultationType(), "");
        String previousRequest = requestedTypes.put(sessionId, requestedType);
        String baseType = requestedType.equals(previousRequest) ? conversationTypes.getOrDefault(sessionId, requestedType) : requestedType;
        String consultationType = selectConsultationType(baseType, message);
        conversationTypes.put(sessionId, consultationType);
        String stateKey = sessionId + "|" + consultationType;
        lastSeen.put(stateKey, now);
        try {
            ParsedAppointmentMessage parsed = parseMessage(message);
            ToolRun toolRun = runTool(sessionId, consultationType, parsed, message);
            PendingAppointment pending = pendingAppointments.get(stateKey);
            String fallbackReply = fallbackReply(consultationType, toolRun, parsed, pending);
            return new AppointmentFreeTurn(
                    toolRun.action(),
                    toolRun.detail(),
                    instructions(consultationType, toolRun, parsed, pending, fallbackReply),
                    fallbackReply,
                    List.of(new AgentTrace(
                            "Qwen appointment tools",
                            toolRun.detail(),
                            toolRun.toolApplied() ? "connected" : "running"
                    ))
            );
        } catch (IllegalArgumentException | java.time.DateTimeException error) {
            String fallbackReply = "No pude completar esa accion: " + error.getMessage()
                    + " Probemos con otro dia u horario dentro de la agenda de la demo.";
            return new AppointmentFreeTurn(
                    "error",
                    error.getMessage(),
                    instructions(
                            consultationType,
                            new ToolRun("error", error.getMessage(), null, null, true),
                            parseMessage(""),
                            pendingAppointments.get(stateKey),
                            fallbackReply
                    ),
                    fallbackReply,
                    List.of(new AgentTrace("Qwen appointment tools", error.getMessage(), "fallback"))
            );
        }
    }

    private ToolRun runTool(
            String sessionId,
            String consultationType,
            ParsedAppointmentMessage parsed,
            String message
    ) {
        if (!StringUtils.hasText(message)) {
            return new ToolRun(
                    "none",
                    "Sin mensaje del paciente; se mantiene la llamada de turnos.",
                    null,
                    null,
                    false
            );
        }

        String stateKey = sessionId + "|" + consultationType;
        PendingAppointment currentPending = pendingAppointments.get(stateKey);
        boolean newBooking = normalize(message).matches(".*\\b(?:(?:otro|otra|nuevo|nueva|segundo|segunda|tercer|tercero)\\s+(?:turno|cita|reserva)|(?:turno|cita|reserva)\\s+(?:mas|nuevo|nueva|adicional)|(?:quiero|necesito)\\s+(?:reservar\\s+|agendar\\s+|sacar\\s+)?(?:un|una)\\s+(?:turno|cita|reserva))\\b.*")
                && !isNameCorrection(message) && !parsed.rescheduleIntent();
        CompletedAppointment completed = completedAppointments.get(stateKey);
        if (completed == null && appointmentDemoService != null && (currentPending == null || currentPending.reschedule())) {
            var saved = appointmentDemoService.activeAppointment(sessionId, consultationType);
            if (saved != null) {
                completed = new CompletedAppointment(saved.patientName(), saved.doctorName(),
                        LocalDateTime.parse(saved.startAt()), false);
                completedAppointments.put(stateKey, completed);
            }
        }
        if (newBooking) {
            String previousName = currentPending != null ? currentPending.patientName() : completed == null ? "" : completed.patientName();
            pendingAppointments.put(stateKey, new PendingAppointment(consultationType, null, null, previousName, false));
            completedAppointments.remove(stateKey);
            offeredSlots.remove(stateKey);
            completed = null;
        } else if (currentPending != null && !currentPending.reschedule()) {
            completed = null;
        }
        if (completed != null
                && parsed.gratitudeIntent()
                && !parsed.farewellIntent()
                && parsed.date() == null
                && parsed.time() == null
                && !parsed.hasTimeWindow()
                && !parsed.availabilityIntent()
                && !parsed.bookingIntent()
                && !parsed.rescheduleIntent()
                && !parsed.confirmationIntent()) {
            return new ToolRun(
                    "post_booking_ack",
                    "De nada, " + completed.patientName() + ". Tu turno con "
                            + completed.doctorName() + " quedo reservado para el "
                            + spokenDateTime(completed.startAt()) + ". Si necesitas algo mas, te escucho.",
                    null,
                    null,
                    false
            );
        }

        if (parsed.farewellIntent()) {
            pendingAppointments.remove(stateKey);
            completedAppointments.remove(stateKey);
            offeredSlots.remove(stateKey);
            return new ToolRun(
                    "end_call",
                    "Gracias por probar la demo. Cierro la llamada.",
                    null,
                    null,
                    false
            );
        }

        PendingAppointment pending = pendingAppointments.get(stateKey);
        if (pending == null && completed != null) {
            pending = new PendingAppointment(consultationType, completed.startAt().toLocalDate(),
                    completed.startAt().toLocalTime(), completed.patientName(), true);
        }
        String effectiveConsultationType = pending == null ? consultationType : pending.consultationType();
        LocalDate date = parsed.date() == null && pending != null ? pending.date() : parsed.date();
        LocalTime time = parsed.time() == null && pending != null ? pending.time() : parsed.time();
        List<AppointmentSlotSuggestion> offered = offeredSlots.getOrDefault(stateKey, List.of());
        String normalizedMessage = normalize(message);
        int choice = normalizedMessage.matches(".*\\b(?:primer[oa]?|1ra?|uno)\\b.*") ? 0
                : normalizedMessage.matches(".*\\b(?:segund[oa]|2da?|dos)\\b.*") ? 1
                : normalizedMessage.matches(".*\\b(?:tercer[oa]?|3ra?|tres)\\b.*") ? 2 : -1;
        if (parsed.date() == null && parsed.time() == null && choice >= 0 && choice < offered.size()) {
            LocalDateTime chosen = LocalDateTime.parse(offered.get(choice).startAt());
            date = chosen.toLocalDate();
            time = chosen.toLocalTime();
        } else if (date == null && time != null) {
            LocalTime selectedTime = time;
            List<LocalDateTime> matching = offered.stream().map(slot -> LocalDateTime.parse(slot.startAt()))
                    .filter(slot -> slot.toLocalTime().equals(selectedTime)).toList();
            if (matching.size() == 1) {
                date = matching.getFirst().toLocalDate();
            }
        }
        String patientName = StringUtils.hasText(parsed.patientName())
                ? parsed.patientName()
                : pending == null ? "" : pending.patientName();
        if (!StringUtils.hasText(patientName) || isNameCorrection(message)) {
            String bareName = extractBarePatientName(message);
            if (StringUtils.hasText(bareName)) {
                patientName = bareName;
            }
        }
        boolean reschedule = completed != null || (pending != null && pending.reschedule());
        boolean confirmationForPending = parsed.confirmationIntent() && pending != null;

        if (completed != null && date != null && time != null
                && date.equals(completed.startAt().toLocalDate()) && time.equals(completed.startAt().toLocalTime())
                && patientName.equals(completed.patientName()) && !parsed.rescheduleIntent()
                && !parsed.availabilityIntent()) {
            return new ToolRun("post_booking_ack", "Tu turno para " + patientName + " sigue reservado para el "
                    + spokenDateTime(completed.startAt()) + ". Podes corregir el nombre o cambiar el dia y horario.", null, null, false);
        }

        // Always retain partial data, even when availability cannot find an exact slot.
        pendingAppointments.put(stateKey, new PendingAppointment(effectiveConsultationType, date, time, patientName, reschedule));

        if (completed != null && StringUtils.hasText(parsed.patientName()) && !patientName.equals(completed.patientName())
                && parsed.date() == null && parsed.time() == null) {
            AppointmentMutationResponse mutation = appointmentDemoService.rename(sessionId, patientName, effectiveConsultationType);
            completedAppointments.put(stateKey, CompletedAppointment.from(mutation, false));
            // A name correction must not discard a time/date change still awaiting confirmation.
            pendingAppointments.put(stateKey, new PendingAppointment(effectiveConsultationType, date, time, patientName, true));
            return new ToolRun("update_name", "Nombre corregido. " + formatAppointment(mutation), null, mutation, true);
        }

        if (StringUtils.hasText(patientName)
                && date == null
                && time == null
                && !parsed.hasTimeWindow()
                && !parsed.confirmationIntent()
                && !parsed.availabilityIntent()
                && !parsed.bookingIntent()
                && !parsed.rescheduleIntent()) {
            pendingAppointments.put(
                    stateKey,
                    new PendingAppointment(effectiveConsultationType, null, null, patientName, reschedule)
            );
            return new ToolRun(
                    "pending",
                    "Perfecto, " + patientName + ". Decime que dia y horario preferis para la consulta.",
                    null,
                    null,
                    false
            );
        }

        if (date != null && time == null && !parsed.hasTimeWindow()) {
            pendingAppointments.put(
                    stateKey,
                    new PendingAppointment(effectiveConsultationType, date, null, patientName, reschedule)
            );
            return new ToolRun(
                    "pending",
                    "Perfecto, tengo " + spokenDate(date) + ". Decime a que horario preferis.",
                    null,
                    null,
                    false
            );
        }

        if (date == null && time != null) {
            pendingAppointments.put(
                    stateKey,
                    new PendingAppointment(effectiveConsultationType, null, time, patientName, reschedule)
            );
            return new ToolRun(
                    "pending",
                    "Tengo el horario " + spokenTime(time) + ". Decime para que dia queres el turno.",
                    null,
                    null,
                    false
            );
        }

        boolean exactSlot = date != null && time != null;
        if (pending != null && exactSlot && StringUtils.hasText(patientName) && confirmationForPending) {
            ParsedAppointmentMessage effectiveParsed = parsed.withAppointment(
                    date,
                    time,
                    patientName,
                    true,
                    reschedule
            );
            AvailabilitySearchResponse availability = searchAvailability(
                    sessionId,
                    effectiveConsultationType,
                    effectiveParsed
            );
            if (!"AVAILABLE".equals(availability.requestedSlotStatus())) {
                pendingAppointments.put(stateKey, new PendingAppointment(effectiveConsultationType, date, time, patientName, reschedule));
                return new ToolRun(
                        "availability",
                        availabilityDetail(availability, effectiveParsed),
                        availability,
                        null,
                        true
                );
            }

            AppointmentMutationResponse mutation;
            String action;
            if (reschedule) {
                mutation = appointmentDemoService.reschedule(
                        new RescheduleAppointmentRequest(
                                sessionId,
                                LocalDateTime.of(date, time).format(DATE_TIME_FORMATTER)
                        ), effectiveConsultationType
                );
                action = "reschedule";
            } else {
                mutation = appointmentDemoService.book(
                        new BookAppointmentRequest(
                                sessionId,
                                effectiveConsultationType,
                                patientName,
                                LocalDateTime.of(date, time).format(DATE_TIME_FORMATTER)
                        )
                );
                action = "book";
            }
            pendingAppointments.remove(stateKey);
            completedAppointments.put(
                    stateKey,
                    CompletedAppointment.from(mutation, reschedule)
            );
            return new ToolRun(
                    action,
                    (reschedule ? "Turno reprogramado para " : "Turno reservado para ")
                            + formatAppointment(mutation),
                    availability,
                    mutation,
                    true
            );
        }

        if (pending != null && exactSlot && !StringUtils.hasText(patientName) && confirmationForPending) {
            pendingAppointments.put(
                    stateKey,
                    new PendingAppointment(effectiveConsultationType, date, time, "", reschedule)
            );
            return new ToolRun(
                    "pending",
                    "Tengo el horario " + spokenDateTime(LocalDateTime.of(date, time))
                            + ". Para guardarlo necesito tu nombre de pila.",
                    null,
                    null,
                    false
            );
        }

        if (pending != null && exactSlot && StringUtils.hasText(patientName)
                && parsed.date() == null && parsed.time() == null) {
            pendingAppointments.put(
                    stateKey,
                    new PendingAppointment(effectiveConsultationType, date, time, patientName, reschedule)
            );
            return new ToolRun(
                    "pending",
                    "Tengo " + spokenDateTime(LocalDateTime.of(date, time))
                            + " para " + patientName + ". Confirmame si lo guardo.",
                    null,
                    null,
                    false
            );
        }

        boolean shouldCheckAvailability = parsed.availabilityIntent()
                || parsed.bookingIntent()
                || parsed.rescheduleIntent()
                || parsed.date() != null
                || parsed.time() != null
                || parsed.hasTimeWindow()
                || exactSlot;

        AvailabilitySearchResponse availability = shouldCheckAvailability
                ? searchAvailability(
                sessionId,
                effectiveConsultationType,
                parsed.withAppointment(date, time, patientName, parsed.bookingIntent(), reschedule)
        )
                : null;
        if (availability != null) {
            offeredSlots.put(stateKey, availability.availableSlots().stream().limit(3).toList());
        }

        if (reschedule && parsed.rescheduleIntent() && exactSlot && availability != null
                && "AVAILABLE".equals(availability.requestedSlotStatus())) {
            AppointmentMutationResponse mutation = appointmentDemoService.reschedule(
                    new RescheduleAppointmentRequest(
                            sessionId,
                            LocalDateTime.of(date, time).format(DATE_TIME_FORMATTER)
                    ), effectiveConsultationType
            );
            pendingAppointments.remove(stateKey);
            completedAppointments.put(
                    stateKey,
                    CompletedAppointment.from(mutation, true)
            );
            return new ToolRun(
                    "reschedule",
                    "Turno reprogramado para " + formatAppointment(mutation),
                    availability,
                    mutation,
                    true
            );
        }

        if (!reschedule && parsed.bookingIntent() && exactSlot && StringUtils.hasText(parsed.patientName())
                && availability != null && "AVAILABLE".equals(availability.requestedSlotStatus())) {
            AppointmentMutationResponse mutation = appointmentDemoService.book(
                    new BookAppointmentRequest(
                            sessionId,
                            effectiveConsultationType,
                            patientName,
                            LocalDateTime.of(date, time).format(DATE_TIME_FORMATTER)
                    )
            );
            pendingAppointments.remove(stateKey);
            completedAppointments.put(
                    stateKey,
                    CompletedAppointment.from(mutation, false)
            );
            return new ToolRun(
                    "book",
                    "Turno reservado para " + formatAppointment(mutation),
                    availability,
                    mutation,
                    true
            );
        }

        if (exactSlot && availability != null && "AVAILABLE".equals(availability.requestedSlotStatus())) {
            pendingAppointments.put(
                    stateKey,
                    new PendingAppointment(
                            consultationType,
                            date,
                            time,
                            patientName,
                            reschedule
                    )
            );
        } else if (exactSlot && availability != null) {
            pendingAppointments.put(
                    stateKey,
                    new PendingAppointment(
                            consultationType,
                            date,
                            time,
                            patientName,
                            reschedule
                    )
            );
        }

        if (availability != null) {
            return new ToolRun(
                    "availability",
                    availabilityDetail(
                            availability,
                            parsed.withAppointment(date, time, patientName, parsed.bookingIntent(), reschedule)
                    ),
                    availability,
                    null,
                    true
            );
        }

        return new ToolRun(
                "none",
                "No hizo falta ejecutar una tool; falta dia, horario o confirmacion.",
                null,
                null,
                false
        );
    }

    private AvailabilitySearchResponse searchAvailability(
            String sessionId,
            String consultationType,
            ParsedAppointmentMessage parsed
    ) {
        LocalDate dateFrom = parsed.date() == null ? LocalDate.now() : parsed.date();
        LocalDate dateTo = parsed.date() == null ? dateFrom.plusDays(14) : parsed.date();
        LocalTime preferredTimeFrom = parsed.time() == null ? parsed.preferredTimeFrom() : parsed.time();
        LocalTime preferredTimeTo = parsed.time() == null ? parsed.preferredTimeTo() : parsed.time().plusMinutes(30);
        return appointmentDemoService.searchAvailability(
                new AvailabilitySearchRequest(
                        sessionId,
                        consultationType,
                        dateFrom.toString(),
                        dateTo.toString(),
                        preferredTimeFrom == null ? null : preferredTimeFrom.toString(),
                        preferredTimeTo == null ? null : preferredTimeTo.toString()
                )
        );
    }

    private String instructions(
            String consultationType,
            ToolRun toolRun,
            ParsedAppointmentMessage parsed,
            PendingAppointment pending,
            String fallbackReply
    ) {
        LocalDate effectiveDate = parsed.date() == null && pending != null ? pending.date() : parsed.date();
        LocalTime effectiveTime = parsed.time() == null && pending != null ? pending.time() : parsed.time();
        String effectivePatientName = effectivePatientName(parsed, pending);
        return """
                Estas respondiendo en la demo Medical appointment workflow del portfolio de Sebastian Gatica.
                Runtime actual: Qwen local + Web Speech del navegador. No digas que estas usando OpenAI.

                Reglas:
                - Sos un asistente operativo de turnos medicos, no das diagnosticos ni consejos clinicos.
                - El backend ya ejecuto la tool indicada abajo. No inventes otra operacion.
                - Si la tool ejecutada es "book" o "reschedule", responde solo con la respuesta base sugerida y no pidas nombre, horario ni confirmacion.
                - Para reservar, pedi nombre de pila. No pidas apellido ni datos sensibles.
                - Si falta nombre de pila, fecha, horario o confirmacion, pedi solo ese dato.
                - Si el estado guardado ya tiene nombre de pila, no lo vuelvas a pedir.
                - Si hay una reserva o reprogramacion, confirmala como persistida y menciona que el calendario visible se actualizo.
                - Si solo hubo disponibilidad, ofrece hasta tres alternativas. Si ya hay nombre guardado, pedi solo horario elegido o confirmacion antes de guardar.
                - Si el usuario se despide, agradece brevemente e indica que vas a cortar la llamada.
                - Si varias alternativas son del mismo dia, nombra el dia una sola vez y agrupa los horarios.
                - Esta respuesta se va a leer en voz alta con Web Speech. Escribi como hablaria una persona.
                - No incluyas razonamiento interno, etiquetas think, JSON ni listas tecnicas.
                - No uses Markdown, bullets, corchetes, parentesis, guiones, barras, fechas ISO, codigos ni horarios tipo 08:00.
                - Deci fechas como "lunes 6 de julio" y horarios como "8 de la manana" o "2 y media de la tarde".

                Contexto:
                - Consulta: %s.
                - Fecha detectada: %s.
                - Horario detectado: %s.
                - Paciente detectado: %s.
                - Estado guardado de la llamada: %s.
                - Tool ejecutada: %s.
                - Resultado de tool: %s.
                - Disponibilidad: %s
                - Respuesta base sugerida: %s
                """.formatted(
                readableConsultation(consultationType),
                effectiveDate == null ? "sin fecha exacta" : spokenDate(effectiveDate),
                effectiveTime == null ? "sin horario exacto" : spokenTime(effectiveTime),
                StringUtils.hasText(effectivePatientName) ? effectivePatientName : "sin nombre",
                pendingStateSummary(pending),
                toolRun.action(),
                toolRun.detail(),
                availabilitySummary(toolRun.availability()),
                fallbackReply
        );
    }

    private String fallbackReply(
            String consultationType,
            ToolRun toolRun,
            ParsedAppointmentMessage parsed,
            PendingAppointment pending
    ) {
        LocalDate effectiveDate = parsed.date() == null && pending != null ? pending.date() : parsed.date();
        LocalTime effectiveTime = parsed.time() == null && pending != null ? pending.time() : parsed.time();
        String effectivePatientName = effectivePatientName(parsed, pending);
        if ("book".equals(toolRun.action()) || "reschedule".equals(toolRun.action()) || "update_name".equals(toolRun.action())) {
            return "Listo, " + toolRun.detail()
                    + " Ya queda reflejado en la agenda visible de la demo.";
        }

        if ("end_call".equals(toolRun.action())) {
            return toolRun.detail();
        }

        if ("post_booking_ack".equals(toolRun.action())) {
            return toolRun.detail();
        }

        if ("availability".equals(toolRun.action()) && toolRun.availability() != null) {
            AvailabilitySearchResponse availability = toolRun.availability();
            StringBuilder reply = new StringBuilder();
            if ("AVAILABLE".equals(availability.requestedSlotStatus()) && effectiveDate != null && effectiveTime != null) {
                reply.append("Ese horario esta disponible para ")
                        .append(readableConsultation(consultationType))
                        .append(". ");
                if (StringUtils.hasText(effectivePatientName)) {
                    reply.append("Tengo ")
                            .append(spokenDateTime(LocalDateTime.of(effectiveDate, effectiveTime)))
                            .append(" para ")
                            .append(effectivePatientName)
                            .append(". Confirmame si lo guardo.");
                } else {
                    reply.append("Pasame tu nombre de pila y confirmame si lo guardo.");
                }
            } else {
                reply.append(availability.requestedSlotReason()).append(" ");
                if (availability.availableSlots().isEmpty()) {
                    reply.append("No encontre alternativas en la ventana consultada. Probemos con otro dia u horario.");
                } else {
                    reply.append("Te puedo ofrecer ")
                            .append(spokenSlotList(availability.availableSlots(), 3))
                            .append(". Si queres reservar una, decime ")
                            .append(StringUtils.hasText(effectivePatientName) ? "el horario elegido." : "tu nombre de pila y el horario elegido.");
                }
            }
            return reply.toString();
        }

        if ("error".equals(toolRun.action())) {
            return "No pude completar esa accion: " + toolRun.detail()
                    + " Probemos con otro dia u horario dentro de la agenda de la demo.";
        }

        if ("pending".equals(toolRun.action())) {
            return toolRun.detail();
        }

        if (StringUtils.hasText(effectivePatientName)) {
            return "Estoy listo para buscar un turno de " + readableConsultation(consultationType)
                    + " para " + effectivePatientName + ". Decime que dia y horario preferis.";
        }

        return "Estoy listo para buscar un turno de " + readableConsultation(consultationType)
                + ". Decime que dia y horario preferis. Antes de guardar la reserva tambien te voy a pedir tu nombre de pila.";
    }

    public String voiceFriendlyReply(String reply, String fallbackReply) {
        String base = StringUtils.hasText(reply) ? reply : fallbackReply;
        if (!StringUtils.hasText(base)) {
            return "";
        }
        String withoutThinking = base.replaceAll("(?is)<think>.*?</think>", " ");
        String withoutMarkdown = withoutThinking
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "")
                .replaceAll("\\*\\*|__", "");

        String spokenDates = replaceDateTimesForSpeech(withoutMarkdown);
        return spokenDates
                .replaceAll("[\\[\\]{}()]", " ")
                .replaceAll("[|*_#>]", " ")
                .replaceAll("\\s*[-/]+\\s*", " ")
                .replaceAll("(?U)(?<=[\\p{L}])(?=\\d)", " ")
                .replaceAll("(?U)(?<=\\d)(?=\\p{L})", " ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\s*;\\s*", ", ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private ParsedAppointmentMessage parseMessage(String message) {
        String normalized = normalize(message);
        LocalDate date = extractDate(message, normalized);
        LocalTime time = extractTime(message, normalized);
        TimeWindow timeWindow = extractTimeWindow(normalized, time);
        String patientName = extractPatientName(message);
        boolean rescheduleIntent = containsAny(normalized, "reprogram", "cambi", "mover", "muev", "mov", "pasar", "pasa", "modific", "traslad");
        boolean confirmationIntent = containsAny(
                normalized,
                "confirm",
                "confirmo",
                "dale",
                "ok",
                "perfecto",
                "correcto",
                "guardalo",
                "guardar",
                "guarda",
                "reservalo",
                "reservar",
                "agendalo"
        ) || Pattern.compile("\\bsi\\b").matcher(normalized).find();
        if (Pattern.compile("\\b(?:no|equivoque|equivocado|error|mejor)\\b").matcher(normalized).find()) {
            confirmationIntent = false;
        }
        boolean bookingIntent = containsAny(normalized, "reserv", "agend", "sacar", "guardar")
                || confirmationIntent;
        if (Pattern.compile("\\bno\\b").matcher(normalized).find()) {
            bookingIntent = false;
        }
        boolean availabilityIntent = containsAny(normalized, "turno", "dispon", "horario", "consulta", "hay", "puedo", "necesito", "quiero");
        boolean farewellIntent = FAREWELL_PATTERN.matcher(normalized).find();
        boolean gratitudeIntent = GRATITUDE_PATTERN.matcher(normalized).find();
        return new ParsedAppointmentMessage(
                date,
                time,
                timeWindow.from(),
                timeWindow.to(),
                patientName,
                availabilityIntent,
                bookingIntent,
                rescheduleIntent,
                confirmationIntent,
                farewellIntent,
                gratitudeIntent
        );
    }

    private LocalDate extractDate(String original, String normalized) {
        String normalizedDateText = normalized
                .replaceAll("\\b(?:por|a|de)\\s+la\\s+manana\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(original);
        if (isoMatcher.find()) {
            return LocalDate.parse(isoMatcher.group(1));
        }

        Matcher shortDateMatcher = SHORT_DATE_PATTERN.matcher(original);
        if (shortDateMatcher.find()) {
            int day = Integer.parseInt(shortDateMatcher.group(1));
            int month = Integer.parseInt(shortDateMatcher.group(2));
            int year = shortDateMatcher.group(3) == null
                    ? Year.now().getValue()
                    : normalizeYear(shortDateMatcher.group(3));
            LocalDate candidate = LocalDate.of(year, month, day);
            if (candidate.isBefore(LocalDate.now().minusDays(1)) && shortDateMatcher.group(3) == null) {
                return candidate.plusYears(1);
            }
            return candidate;
        }

        Matcher monthDateMatcher = MONTH_DATE_PATTERN.matcher(normalizedDateText);
        if (monthDateMatcher.find()) {
            int day = StringUtils.hasText(monthDateMatcher.group(1))
                    ? Integer.parseInt(monthDateMatcher.group(1))
                    : dayNumber(monthDateMatcher.group(2));
            int month = monthNumber(monthDateMatcher.group(3));
            if (day > 0 && month > 0) {
                LocalDate candidate = LocalDate.of(Year.now().getValue(), month, day);
                if (candidate.isBefore(LocalDate.now().minusDays(1))) {
                    candidate = candidate.plusYears(1);
                }
                return candidate;
            }
        }

        if (normalizedDateText.contains("pasado manana")) {
            return LocalDate.now().plusDays(2);
        }
        if (Pattern.compile("\\bmanana\\b").matcher(normalizedDateText).find()) {
            return LocalDate.now().plusDays(1);
        }
        if (Pattern.compile("\\bhoy\\b").matcher(normalizedDateText).find()) {
            return LocalDate.now();
        }

        for (DayName dayName : DayName.values()) {
            if (normalizedDateText.contains(dayName.token())) {
                Matcher numberedDay = Pattern.compile("\\b" + dayName.token() + "\\s+(\\d{1,2})\\b").matcher(normalizedDateText);
                if (numberedDay.find()) {
                    int day = Integer.parseInt(numberedDay.group(1));
                    LocalDate today = LocalDate.now();
                    LocalDate month = today.withDayOfMonth(1);
                    if (day < today.getDayOfMonth()) month = month.plusMonths(1);
                    LocalDate candidate = month.withDayOfMonth(day);
                    if (candidate.getDayOfWeek() != dayName.dayOfWeek()) {
                        throw new java.time.DateTimeException("El numero de dia no coincide con el dia de la semana");
                    }
                    return candidate;
                }
                return nextOrSame(dayName.dayOfWeek());
            }
        }

        return null;
    }

    private LocalTime extractTime(String original, String normalized) {
        LocalTime selected = null;
        int position = -1;
        Matcher bare = Pattern.compile("^(?:mejor\\s+)?(\\d{1,2})(?::(\\d{2}))?(?:\\s*(?:hs?|horas?))?[.!]?$|\\b(\\d{1,2}):(\\d{2})\\b").matcher(normalized);
        while (bare.find()) {
            String hour = bare.group(1) == null ? bare.group(3) : bare.group(1);
            String minute = bare.group(1) == null ? bare.group(4) : bare.group(2);
            selected = spokenClock(Integer.parseInt(hour), minute == null ? 0 : Integer.parseInt(minute), normalized.substring(bare.end()));
            position = bare.start();
        }
        Matcher numeric = TIME_PATTERN.matcher(normalized);
        while (numeric.find()) {
            if (numeric.start() < position) continue;
            int minute = numeric.group(2) == null ? 0 : Integer.parseInt(numeric.group(2));
            String tail = normalized.substring(numeric.end());
            if (tail.stripLeading().startsWith("y media")) minute = 30;
            if (tail.stripLeading().startsWith("y cuarto")) minute = 15;
            selected = spokenClock(Integer.parseInt(numeric.group(1)), minute, tail);
            position = numeric.start();
        }
        Matcher words = WORD_TIME_PATTERN.matcher(normalized);
        while (words.find()) {
            if (words.start() < position) continue;
            int minute = switch (words.group(2) == null ? "" : words.group(2)) {
                case "media", "treinta" -> 30;
                case "cuarto", "quince" -> 15;
                default -> 0;
            };
            selected = spokenClock(hourNumber(words.group(1)), minute, words.group(3) == null ? "" : words.group(3));
            position = words.start();
        }
        // In a correction such as "de las 9 a las 12:30", the final time is the destination.
        return selected;
    }

    private LocalTime spokenClock(int hour, int minute, String tail) {
        String period = tail.substring(0, Math.min(tail.length(), 22));
        if (hour < 12 && containsAny(period, "tarde", "noche")) hour += 12;
        return LocalTime.of(hour, minute);
    }

    private TimeWindow extractTimeWindow(String normalized, LocalTime exactTime) {
        if (exactTime != null) {
            return new TimeWindow(null, null);
        }
        if (containsAny(normalized, "por la manana", "a la manana", "de la manana", "primera hora", "temprano")) {
            return new TimeWindow(LocalTime.of(8, 0), LocalTime.of(13, 0));
        }
        if (containsAny(normalized, "por la tarde", "a la tarde", "de la tarde", "despues del mediodia")) {
            return new TimeWindow(LocalTime.of(14, 0), LocalTime.of(18, 0));
        }
        if (containsAny(normalized, "al mediodia", "cerca del mediodia")) {
            return new TimeWindow(LocalTime.of(12, 0), LocalTime.of(13, 0));
        }
        return new TimeWindow(null, null);
    }

    private String extractPatientName(String message) {
        Matcher correction = Pattern.compile("(?iu)(?:mi nombre (?:correcto )?es|el nombre es|me equivoqu[eé][, ]+(?:es |era )?|(?:cambi[aá]|cambiar|correg[ií]|corregir) (?:el |mi )?nombre (?:a|por)|no[, ]+soy)\\s*([\\p{L}]{2,})").matcher(message);
        if (correction.find() && validPatientName(correction.group(1))) {
            return correction.group(1);
        }
        Matcher patientMatcher = PATIENT_NAME_PATTERN.matcher(message);
        if (patientMatcher.find()) {
            String candidate = cleanPatientName(patientMatcher.group(1));
            if (validPatientName(candidate)) {
                return firstGivenName(candidate);
            }
        }

        Matcher reversedMatcher = REVERSED_NAME_PATTERN.matcher(message);
        if (reversedMatcher.find()) {
            String candidate = cleanPatientName(reversedMatcher.group(1));
            if (validPatientName(candidate)) {
                return firstGivenName(candidate);
            }
        }

        Matcher matcher = NAME_PATTERN.matcher(message);
        if (!matcher.find()) {
            return "";
        }
        String candidate = matcher.group(1)
                .replaceAll("(?iu)\\s+(?:el|la|los|las|manana|mañana|hoy|pasado|a|con|y)\\b.*$", "")
                .trim();
        String normalized = normalize(candidate);
        if (!validPatientName(candidate)
                || containsAny(normalized, "turno", "consulta", "cardiologo", "traumatologo", "control", "manana", "hoy", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")) {
            return "";
        }
        return firstGivenName(candidate);
    }

    private String extractBarePatientName(String message) {
        String explicit = extractPatientName(message);
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if (message.matches(".*\\d.*")) {
            return "";
        }
        String candidate = cleanPatientName(message);
        return validPatientName(candidate) ? firstGivenName(candidate) : "";
    }

    private String cleanPatientName(String value) {
        return value
                .replaceAll("(?iu)^\\s*(?:el|la)\\s+paciente\\s+", "")
                .replaceAll("(?iu)\\s+(?:el|la|los|las|manana|mañana|hoy|pasado|lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo|a|con|y|para|por|turno|consulta|horario)\\b.*$", "")
                .replaceAll("[^\\p{L}\\s'-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean validPatientName(String candidate) {
        String normalized = normalize(candidate);
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        if (firstGivenName(candidate).length() < 2) {
            return false;
        }
        if (List.of("hola", "buen", "buenas", "buenos", "muchas", "tengo", "sabes", "quisiera", "perdon", "quiero", "necesito", "para", "por", "el", "la", "las", "los", "a", "mi", "me", "no", "si", "soy", "gracias", "confirmo", "confirmado", "confirmar", "dale", "ok", "perfecto", "correcto", "guarda", "guardar", "guardarlo", "guardalo", "reservar", "reservarlo", "reservalo", "agendar", "agendalo", "mejor", "cambiar", "cambia", "corregir", "equivoque", "primero", "primera", "segundo", "segunda", "tercero", "tercera", "ocho", "nueve", "diez", "once", "doce").contains(normalize(firstGivenName(candidate)))) {
            return false;
        }
        if (List.of("a", "al", "de", "del", "confirmo", "confirma", "confirmar", "dale", "ok", "si").contains(normalized)) {
            return false;
        }
        if (containsAny(
                normalized,
                "turno",
                "consulta",
                "cardiologo",
                "traumatologo",
                "control",
                "manana",
                "hoy",
                "lunes",
                "martes",
                "miercoles",
                "jueves",
                "viernes",
                "sabado",
                "domingo"
        )) {
            return false;
        }
        return candidate.split("\\s+").length <= 4;
    }

    private String firstGivenName(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "";
        }
        return candidate.trim().split("\\s+")[0];
    }

    public Mono<AppointmentFreeTurn> prepareAsync(AppointmentChatRequest request, FreeModelClient model) {
        return Mono.fromCallable(() -> {
            String message = request.message() == null ? "" : request.message();
            ParsedAppointmentMessage parsed = parseMessage(message);
            boolean understood = parsed.date() != null || parsed.time() != null || parsed.hasTimeWindow()
                    || StringUtils.hasText(parsed.patientName()) || parsed.confirmationIntent()
                    || parsed.farewellIntent() || parsed.gratitudeIntent()
                    || parsed.bookingIntent() || parsed.rescheduleIntent() || parsed.availabilityIntent()
                    || containsAny(normalize(message), "turno", "cita", "mendez", "ibarra", "varela", "cardio", "control", "traumato")
                    || (message.trim().split("\\s+").length <= 2 && validPatientName(cleanPatientName(message)))
                    || containsAny(normalize(message), "primer", "segund", "tercer");
            return understood;
        }).onErrorReturn(true).flatMap(understood -> {
            if (understood || !model.configured()) {
                return Mono.fromCallable(() -> prepare(request));
            }
            String instructions = "Extrae datos del ULTIMO mensaje para turnos. Responde SOLO JSON con patientName, date (YYYY-MM-DD), time (HH:mm), todos strings vacios si no aparecen. No inventes ni confirmes reservas. Hoy es "
                    + LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                    + ". Si corrige un dato devuelve el dato nuevo. Nunca copies datos del estado como si fueran nuevos. Estado: "
                    + pendingStateSummary(pendingAppointments.get(normalizeSessionId(request.sessionId()) + "|"
                            + conversationTypes.getOrDefault(normalizeSessionId(request.sessionId()), selectConsultationType(request.consultationType(), request.message()))));
            return model.streamText(request.message(), instructions, "").reduce("", String::concat)
                    .timeout(Duration.ofSeconds(15))
                    .map(reply -> {
                        try {
                            int start = reply.indexOf('{');
                            int end = reply.lastIndexOf('}');
                            var data = new ObjectMapper().readTree(reply.substring(start, end + 1));
                            String name = data.path("patientName").asText("");
                            String date = data.path("date").asText("");
                            String time = data.path("time").asText("");
                            String extra = "";
                            if (name.matches("[\\p{L}]{2,30}") && validPatientName(name)
                                    && normalize(request.message()).contains(normalize(name))) {
                                extra += ". Me llamo " + name;
                            }
                            if (date.matches("\\d{4}-\\d{2}-\\d{2}") && normalize(request.message()).matches(".*\\b(?:dia|fecha|semana|mes|lunes|martes|miercoles|jueves|viernes|manana|hoy)\\b.*")) {
                                LocalDate value = LocalDate.parse(date);
                                if (!value.isBefore(LocalDate.now()) && !value.isAfter(LocalDate.now().plusDays(30))) extra += ". El " + date;
                            }
                            if (time.matches("\\d{2}:\\d{2}") && normalize(request.message()).matches(".*\\b(?:hora|horario|las|mediodia|tarde|temprano)\\b.*")) {
                                LocalTime.parse(time);
                                extra += ". A las " + time;
                            }
                            return new AppointmentChatRequest(request.message() + extra, request.sessionId(), request.consultationType());
                        } catch (Exception ignored) {
                            return request;
                        }
                    }).onErrorReturn(request).map(this::prepare);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private boolean isNameCorrection(String message) {
        return containsAny(normalize(message), "nombre", "me equivoque", "no soy", "no, soy");
    }

    private int dayNumber(String value) {
        return switch (normalize(value)) {
            case "uno" -> 1;
            case "dos" -> 2;
            case "tres" -> 3;
            case "cuatro" -> 4;
            case "cinco" -> 5;
            case "seis" -> 6;
            case "siete" -> 7;
            case "ocho" -> 8;
            case "nueve" -> 9;
            case "diez" -> 10;
            case "once" -> 11;
            case "doce" -> 12;
            case "trece" -> 13;
            case "catorce" -> 14;
            case "quince" -> 15;
            case "dieciseis" -> 16;
            case "diecisiete" -> 17;
            case "dieciocho" -> 18;
            case "diecinueve" -> 19;
            case "veinte" -> 20;
            case "veintiuno" -> 21;
            case "veintidos" -> 22;
            case "veintitres" -> 23;
            case "veinticuatro" -> 24;
            case "veinticinco" -> 25;
            case "veintiseis" -> 26;
            case "veintisiete" -> 27;
            case "veintiocho" -> 28;
            case "veintinueve" -> 29;
            case "treinta" -> 30;
            case "treinta y uno" -> 31;
            default -> 0;
        };
    }

    private int monthNumber(String value) {
        return switch (normalize(value)) {
            case "enero" -> 1;
            case "febrero" -> 2;
            case "marzo" -> 3;
            case "abril" -> 4;
            case "mayo" -> 5;
            case "junio" -> 6;
            case "julio" -> 7;
            case "agosto" -> 8;
            case "septiembre", "setiembre" -> 9;
            case "octubre" -> 10;
            case "noviembre" -> 11;
            case "diciembre" -> 12;
            default -> 0;
        };
    }

    private int hourNumber(String value) {
        return switch (normalize(value)) {
            case "una" -> 1;
            case "dos" -> 2;
            case "tres" -> 3;
            case "cuatro" -> 4;
            case "cinco" -> 5;
            case "seis" -> 6;
            case "siete" -> 7;
            case "ocho" -> 8;
            case "nueve" -> 9;
            case "diez" -> 10;
            case "once" -> 11;
            case "doce" -> 12;
            default -> 0;
        };
    }

    private String selectConsultationType(String requestedType, String message) {
        String normalized = normalize(message);
        if (containsAny(normalized, "cardio", "corazon", "con tomas", "ibarra")) return "cardiology";
        if (containsAny(normalized, "control", "seguimiento", "con paula", "mendez")) return "follow-up";
        if (containsAny(normalized, "traumato", "con hernan", "varela")) return "traumatology";
        String requested = normalize(requestedType);
        return "follow-up".equals(requested) || "cardiology".equals(requested) ? requested : "traumatology";
    }

    private String availabilityDetail(AvailabilitySearchResponse availability, ParsedAppointmentMessage parsed) {
        String requested = parsed.date() == null || parsed.time() == null
                ? "busqueda por rango"
                : spokenDateTime(LocalDateTime.of(parsed.date(), parsed.time()));
        return "Disponibilidad consultada para " + requested
                + ". Estado: " + availability.requestedSlotStatus()
                + ". " + availability.requestedSlotReason()
                + " Alternativas: " + spokenSlotList(availability.availableSlots(), 3);
    }

    private String availabilitySummary(AvailabilitySearchResponse availability) {
        if (availability == null) {
            return "sin consulta de disponibilidad";
        }
        return "estado=" + availability.requestedSlotStatus()
                + "; motivo=" + availability.requestedSlotReason()
                + "; alternativas=" + spokenSlotList(availability.availableSlots(), 6);
    }

    private String spokenSlotList(List<AppointmentSlotSuggestion> slots, int limit) {
        if (slots == null || slots.isEmpty()) {
            return "sin alternativas";
        }

        Map<String, List<String>> timesByDateAndDoctor = new LinkedHashMap<>();
        Map<String, String> dateByGroup = new LinkedHashMap<>();
        Map<String, String> doctorByGroup = new LinkedHashMap<>();

        slots.stream().limit(limit).forEach(slot -> {
            LocalDateTime startAt = LocalDateTime.parse(slot.startAt());
            String groupKey = startAt.toLocalDate() + "|" + slot.doctorName();
            timesByDateAndDoctor.computeIfAbsent(groupKey, ignored -> new ArrayList<>())
                    .add(spokenTime(startAt.toLocalTime()));
            dateByGroup.putIfAbsent(groupKey, spokenDate(startAt.toLocalDate()));
            doctorByGroup.putIfAbsent(groupKey, slot.doctorName());
        });

        List<String> groups = new ArrayList<>();
        timesByDateAndDoctor.forEach((groupKey, times) -> groups.add(
                dateByGroup.get(groupKey)
                        + ", horarios "
                        + naturalJoin(times)
                        + " con "
                        + doctorByGroup.get(groupKey)
        ));
        return naturalJoin(groups);
    }

    private String naturalJoin(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        if (values.size() == 2) {
            return values.getFirst() + " y " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " y "
                + values.getLast();
    }

    private String formatAppointment(AppointmentMutationResponse mutation) {
        return mutation.appointment().doctorName()
                + " el " + spokenDateTime(LocalDateTime.parse(mutation.appointment().startAt()));
    }

    private String effectivePatientName(ParsedAppointmentMessage parsed, PendingAppointment pending) {
        if (StringUtils.hasText(parsed.patientName())) {
            return parsed.patientName();
        }
        return pending == null ? "" : pending.patientName();
    }

    private String pendingStateSummary(PendingAppointment pending) {
        if (pending == null) {
            return "sin datos guardados";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(pending.patientName())) {
            parts.add("nombre " + pending.patientName());
        }
        if (pending.date() != null) {
            parts.add("fecha " + spokenDate(pending.date()));
        }
        if (pending.time() != null) {
            parts.add("horario " + spokenTime(pending.time()));
        }
        if (parts.isEmpty()) {
            return "sin datos guardados";
        }
        return naturalJoin(parts);
    }

    private String replaceDateTimesForSpeech(String value) {
        Pattern dateTimePattern = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})(?:[T\\s]?)(\\d{1,2}):(\\d{2})\\b");
        Matcher dateTimeMatcher = dateTimePattern.matcher(value);
        StringBuffer dateTimeBuffer = new StringBuffer();
        while (dateTimeMatcher.find()) {
            String replacement = dateTimeMatcher.group();
            try {
                LocalDate date = LocalDate.of(
                        Integer.parseInt(dateTimeMatcher.group(1)),
                        Integer.parseInt(dateTimeMatcher.group(2)),
                        Integer.parseInt(dateTimeMatcher.group(3))
                );
                LocalTime time = LocalTime.of(
                        Integer.parseInt(dateTimeMatcher.group(4)),
                        Integer.parseInt(dateTimeMatcher.group(5))
                );
                replacement = spokenDateTime(LocalDateTime.of(date, time));
            } catch (RuntimeException ignored) {
                // Keep the original fragment if the model produced an invalid date or time.
            }
            dateTimeMatcher.appendReplacement(
                    dateTimeBuffer,
                    Matcher.quoteReplacement(replacement)
            );
        }
        dateTimeMatcher.appendTail(dateTimeBuffer);

        Matcher dateMatcher = ISO_DATE_PATTERN.matcher(dateTimeBuffer.toString());
        StringBuffer dateBuffer = new StringBuffer();
        while (dateMatcher.find()) {
            String replacement = dateMatcher.group();
            try {
                replacement = spokenDate(LocalDate.parse(dateMatcher.group(1)));
            } catch (RuntimeException ignored) {
                // Keep the original fragment if the model produced an invalid date.
            }
            dateMatcher.appendReplacement(
                    dateBuffer,
                    Matcher.quoteReplacement(replacement)
            );
        }
        dateMatcher.appendTail(dateBuffer);

        Pattern clockPattern = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
        Matcher clockMatcher = clockPattern.matcher(dateBuffer.toString());
        StringBuffer clockBuffer = new StringBuffer();
        while (clockMatcher.find()) {
            String replacement = clockMatcher.group();
            try {
                replacement = spokenTime(LocalTime.of(
                        Integer.parseInt(clockMatcher.group(1)),
                        Integer.parseInt(clockMatcher.group(2))
                ));
            } catch (RuntimeException ignored) {
                // Keep the original fragment if the model produced an invalid time.
            }
            clockMatcher.appendReplacement(
                    clockBuffer,
                    Matcher.quoteReplacement(replacement)
            );
        }
        clockMatcher.appendTail(clockBuffer);
        return clockBuffer.toString();
    }

    private String spokenDateTime(LocalDateTime value) {
        return spokenDate(value.toLocalDate()) + " a las " + spokenTime(value.toLocalTime());
    }

    private String spokenDate(LocalDate value) {
        return dayName(value.getDayOfWeek()) + " " + value.getDayOfMonth() + " de " + monthName(value.getMonthValue());
    }

    private String spokenTime(LocalTime value) {
        int hour = value.getHour();
        int minute = value.getMinute();
        int spokenHour = hour % 12 == 0 ? 12 : hour % 12;
        String period = hour < 12 ? "de la manana" : "de la tarde";
        if (hour == 12) {
            period = "del mediodia";
        }
        if (minute == 0) {
            return spokenHour + " " + period;
        }
        if (minute == 30) {
            return spokenHour + " y media " + period;
        }
        return spokenHour + " y " + minute + " " + period;
    }

    private String dayName(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "lunes";
            case TUESDAY -> "martes";
            case WEDNESDAY -> "miercoles";
            case THURSDAY -> "jueves";
            case FRIDAY -> "viernes";
            case SATURDAY -> "sabado";
            case SUNDAY -> "domingo";
        };
    }

    private String monthName(int month) {
        return switch (month) {
            case 1 -> "enero";
            case 2 -> "febrero";
            case 3 -> "marzo";
            case 4 -> "abril";
            case 5 -> "mayo";
            case 6 -> "junio";
            case 7 -> "julio";
            case 8 -> "agosto";
            case 9 -> "septiembre";
            case 10 -> "octubre";
            case 11 -> "noviembre";
            case 12 -> "diciembre";
            default -> "";
        };
    }

    private LocalDate nextOrSame(DayOfWeek dayOfWeek) {
        LocalDate today = LocalDate.now();
        int daysUntil = dayOfWeek.getValue() - today.getDayOfWeek().getValue();
        if (daysUntil < 0) {
            daysUntil += 7;
        }
        return today.plusDays(daysUntil);
    }

    private int normalizeYear(String value) {
        int year = Integer.parseInt(value);
        return year < 100 ? 2000 + year : year;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String normalizeSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId.trim() : "anonymous-demo";
    }

    private String readableConsultation(String consultationType) {
        return switch (consultationType) {
            case "cardiology" -> "consulta con cardiologo";
            case "follow-up" -> "consulta de control";
            default -> "consulta con traumatologo";
        };
    }

    public record AppointmentFreeTurn(
            String action,
            String detail,
            String instructions,
            String fallbackReply,
            List<AgentTrace> traces
    ) {
    }

    private record ParsedAppointmentMessage(
            LocalDate date,
            LocalTime time,
            LocalTime preferredTimeFrom,
            LocalTime preferredTimeTo,
            String patientName,
            boolean availabilityIntent,
            boolean bookingIntent,
            boolean rescheduleIntent,
            boolean confirmationIntent,
            boolean farewellIntent,
            boolean gratitudeIntent
    ) {
        private ParsedAppointmentMessage withAppointment(
                LocalDate nextDate,
                LocalTime nextTime,
                String nextPatientName,
                boolean nextBookingIntent,
                boolean nextRescheduleIntent
        ) {
            return new ParsedAppointmentMessage(
                    nextDate,
                    nextTime,
                    preferredTimeFrom,
                    preferredTimeTo,
                    nextPatientName,
                    availabilityIntent,
                    nextBookingIntent,
                    nextRescheduleIntent,
                    confirmationIntent,
                    farewellIntent,
                    gratitudeIntent
            );
        }

        private boolean hasTimeWindow() {
            return preferredTimeFrom != null && preferredTimeTo != null;
        }
    }

    private record TimeWindow(LocalTime from, LocalTime to) {
    }

    private record PendingAppointment(
            String consultationType,
            LocalDate date,
            LocalTime time,
            String patientName,
            boolean reschedule
    ) {
        private PendingAppointment withPatientName(String nextPatientName) {
            return new PendingAppointment(
                    consultationType,
                    date,
                    time,
                    nextPatientName,
                    reschedule
            );
        }
    }

    private record CompletedAppointment(
            String patientName,
            String doctorName,
            LocalDateTime startAt,
            boolean reschedule
    ) {
        private static CompletedAppointment from(AppointmentMutationResponse mutation, boolean reschedule) {
            return new CompletedAppointment(
                    mutation.appointment().patientName(),
                    mutation.appointment().doctorName(),
                    LocalDateTime.parse(mutation.appointment().startAt()),
                    reschedule
            );
        }
    }

    private record ToolRun(
            String action,
            String detail,
            AvailabilitySearchResponse availability,
            AppointmentMutationResponse mutation,
            boolean toolApplied
    ) {
    }

    private enum DayName {
        MONDAY("lunes", DayOfWeek.MONDAY),
        TUESDAY("martes", DayOfWeek.TUESDAY),
        WEDNESDAY("miercoles", DayOfWeek.WEDNESDAY),
        THURSDAY("jueves", DayOfWeek.THURSDAY),
        FRIDAY("viernes", DayOfWeek.FRIDAY);

        private final String token;
        private final DayOfWeek dayOfWeek;

        DayName(String token, DayOfWeek dayOfWeek) {
            this.token = token;
            this.dayOfWeek = dayOfWeek;
        }

        private String token() {
            return token;
        }

        private DayOfWeek dayOfWeek() {
            return dayOfWeek;
        }
    }
}
