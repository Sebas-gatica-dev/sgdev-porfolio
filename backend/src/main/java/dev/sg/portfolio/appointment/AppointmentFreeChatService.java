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
    private static final Pattern FAREWELL_PATTERN = Pattern.compile(
            "\\b(?:chau|adios|hasta\\s+luego|nos\\s+vemos|gracias\\s+eso\\s+es\\s+todo|eso\\s+es\\s+todo|corta(?:r)?\\s+la\\s+llamada|termina(?:r)?\\s+la\\s+llamada)\\b"
    );

    private final AppointmentDemoService appointmentDemoService;
    private final Map<String, PendingAppointment> pendingAppointments = new ConcurrentHashMap<>();

    public AppointmentFreeChatService(AppointmentDemoService appointmentDemoService) {
        this.appointmentDemoService = appointmentDemoService;
    }

    public AppointmentFreeTurn prepare(AppointmentChatRequest request) {
        String message = request == null || request.message() == null ? "" : request.message().trim();
        String sessionId = normalizeSessionId(request == null ? null : request.sessionId());
        String consultationType = selectConsultationType(request == null ? null : request.consultationType(), message);
        ParsedAppointmentMessage parsed = parseMessage(message);

        try {
            ToolRun toolRun = runTool(sessionId, consultationType, parsed, message);
            String fallbackReply = fallbackReply(consultationType, toolRun, parsed);
            return new AppointmentFreeTurn(
                    toolRun.action(),
                    toolRun.detail(),
                    instructions(consultationType, toolRun, parsed, fallbackReply),
                    fallbackReply,
                    List.of(new AgentTrace(
                            "Qwen appointment tools",
                            toolRun.detail(),
                            toolRun.toolApplied() ? "connected" : "running"
                    ))
            );
        } catch (IllegalArgumentException error) {
            String fallbackReply = "No pude completar esa accion: " + error.getMessage()
                    + " Probemos con otro dia u horario dentro de la agenda de la demo.";
            return new AppointmentFreeTurn(
                    "error",
                    error.getMessage(),
                    instructions(
                            consultationType,
                            new ToolRun("error", error.getMessage(), null, null, true),
                            parsed,
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

        if (parsed.farewellIntent()) {
            pendingAppointments.remove(sessionId);
            return new ToolRun(
                    "end_call",
                    "Gracias por probar la demo. Cierro la llamada.",
                    null,
                    null,
                    false
            );
        }

        PendingAppointment pending = pendingAppointments.get(sessionId);
        String effectiveConsultationType = pending == null ? consultationType : pending.consultationType();
        LocalDate date = parsed.date() == null && pending != null ? pending.date() : parsed.date();
        LocalTime time = parsed.time() == null && pending != null ? pending.time() : parsed.time();
        String patientName = StringUtils.hasText(parsed.patientName())
                ? parsed.patientName()
                : pending == null ? "" : pending.patientName();
        if (!StringUtils.hasText(patientName) && pending != null) {
            String bareName = extractBarePatientName(message);
            if (StringUtils.hasText(bareName)) {
                patientName = bareName;
            }
        }
        boolean reschedule = parsed.rescheduleIntent() || (pending != null && pending.reschedule());
        boolean confirmationForPending = parsed.confirmationIntent() && pending != null;

        if (date != null && time == null) {
            pendingAppointments.put(
                    sessionId,
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
                    sessionId,
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
                pendingAppointments.remove(sessionId);
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
                        )
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
            pendingAppointments.remove(sessionId);
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
                    sessionId,
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
                    sessionId,
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
                || exactSlot;

        AvailabilitySearchResponse availability = shouldCheckAvailability
                ? searchAvailability(
                sessionId,
                effectiveConsultationType,
                parsed.withAppointment(date, time, patientName, parsed.bookingIntent(), reschedule)
        )
                : null;

        if (parsed.rescheduleIntent() && exactSlot && availability != null
                && "AVAILABLE".equals(availability.requestedSlotStatus())) {
            AppointmentMutationResponse mutation = appointmentDemoService.reschedule(
                    new RescheduleAppointmentRequest(
                            sessionId,
                            LocalDateTime.of(date, time).format(DATE_TIME_FORMATTER)
                    )
            );
            pendingAppointments.remove(sessionId);
            return new ToolRun(
                    "reschedule",
                    "Turno reprogramado para " + formatAppointment(mutation),
                    availability,
                    mutation,
                    true
            );
        }

        if (parsed.bookingIntent() && exactSlot && StringUtils.hasText(parsed.patientName())
                && availability != null && "AVAILABLE".equals(availability.requestedSlotStatus())) {
            AppointmentMutationResponse mutation = appointmentDemoService.book(
                    new BookAppointmentRequest(
                            sessionId,
                            effectiveConsultationType,
                            patientName,
                            LocalDateTime.of(date, time).format(DATE_TIME_FORMATTER)
                    )
            );
            pendingAppointments.remove(sessionId);
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
                    sessionId,
                    new PendingAppointment(
                            consultationType,
                            date,
                            time,
                            patientName,
                            reschedule
                    )
            );
        } else if (exactSlot && availability != null) {
            pendingAppointments.remove(sessionId);
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
        LocalTime timeTo = parsed.time() == null ? null : parsed.time().plusMinutes(30);
        return appointmentDemoService.searchAvailability(
                new AvailabilitySearchRequest(
                        sessionId,
                        consultationType,
                        dateFrom.toString(),
                        dateTo.toString(),
                        parsed.time() == null ? null : parsed.time().toString(),
                        timeTo == null ? null : timeTo.toString()
                )
        );
    }

    private String instructions(
            String consultationType,
            ToolRun toolRun,
            ParsedAppointmentMessage parsed,
            String fallbackReply
    ) {
        return """
                Estas respondiendo en la demo Medical appointment workflow del portfolio de Sebastian Gatica.
                Runtime actual: Qwen local + Web Speech del navegador. No digas que estas usando OpenAI.

                Reglas:
                - Sos un asistente operativo de turnos medicos, no das diagnosticos ni consejos clinicos.
                - El backend ya ejecuto la tool indicada abajo. No inventes otra operacion.
                - Si la tool ejecutada es "book" o "reschedule", responde solo con la respuesta base sugerida y no pidas nombre, horario ni confirmacion.
                - Para reservar, pedi nombre de pila. No pidas apellido ni datos sensibles.
                - Si falta nombre de pila, fecha, horario o confirmacion, pedi solo ese dato.
                - Si hay una reserva o reprogramacion, confirmala como persistida y menciona que el calendario visible se actualizo.
                - Si solo hubo disponibilidad, ofrece hasta tres alternativas y pedi nombre de pila + confirmacion antes de guardar.
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
                - Tool ejecutada: %s.
                - Resultado de tool: %s.
                - Disponibilidad: %s
                - Respuesta base sugerida: %s
                """.formatted(
                readableConsultation(consultationType),
                parsed.date() == null ? "sin fecha exacta" : spokenDate(parsed.date()),
                parsed.time() == null ? "sin horario exacto" : spokenTime(parsed.time()),
                StringUtils.hasText(parsed.patientName()) ? parsed.patientName() : "sin nombre",
                toolRun.action(),
                toolRun.detail(),
                availabilitySummary(toolRun.availability()),
                fallbackReply
        );
    }

    private String fallbackReply(
            String consultationType,
            ToolRun toolRun,
            ParsedAppointmentMessage parsed
    ) {
        if ("book".equals(toolRun.action()) || "reschedule".equals(toolRun.action())) {
            return "Listo, " + toolRun.detail()
                    + " Ya queda reflejado en la agenda visible de la demo.";
        }

        if ("end_call".equals(toolRun.action())) {
            return toolRun.detail();
        }

        if ("availability".equals(toolRun.action()) && toolRun.availability() != null) {
            AvailabilitySearchResponse availability = toolRun.availability();
            StringBuilder reply = new StringBuilder();
            if ("AVAILABLE".equals(availability.requestedSlotStatus()) && parsed.date() != null && parsed.time() != null) {
                reply.append("Ese horario esta disponible para ")
                        .append(readableConsultation(consultationType))
                        .append(". ");
                if (StringUtils.hasText(parsed.patientName())) {
                    reply.append("Para guardarlo necesito que confirmes la reserva para ")
                            .append(parsed.patientName())
                            .append(".");
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
                            .append(". Si queres reservar una, decime tu nombre de pila y el horario elegido.");
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
        String patientName = extractPatientName(message);
        boolean rescheduleIntent = containsAny(normalized, "reprogram", "cambiar", "mover", "pasar", "modificar");
        boolean confirmationIntent = containsAny(
                normalized,
                "confirm",
                "confirmo",
                "dale",
                "ok",
                "perfecto",
                "correcto",
                "guardalo",
                "reservalo",
                "agendalo"
        ) || Pattern.compile("\\bsi\\b").matcher(normalized).find();
        boolean bookingIntent = containsAny(normalized, "reserv", "agend", "sacar", "guardar")
                || confirmationIntent;
        boolean availabilityIntent = containsAny(normalized, "turno", "dispon", "horario", "consulta", "hay", "puedo", "necesito", "quiero");
        boolean farewellIntent = FAREWELL_PATTERN.matcher(normalized).find();
        return new ParsedAppointmentMessage(
                date,
                time,
                patientName,
                availabilityIntent,
                bookingIntent,
                rescheduleIntent,
                confirmationIntent,
                farewellIntent
        );
    }

    private LocalDate extractDate(String original, String normalized) {
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

        Matcher monthDateMatcher = MONTH_DATE_PATTERN.matcher(normalized);
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

        if (normalized.contains("pasado manana")) {
            return LocalDate.now().plusDays(2);
        }
        if (normalized.contains("manana")) {
            return LocalDate.now().plusDays(1);
        }
        if (normalized.contains("hoy")) {
            return LocalDate.now();
        }

        for (DayName dayName : DayName.values()) {
            if (normalized.contains(dayName.token())) {
                return nextOrSame(dayName.dayOfWeek());
            }
        }

        return null;
    }

    private LocalTime extractTime(String original, String normalized) {
        Matcher matcher = TIME_PATTERN.matcher(normalized);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            int matchEnd = matcher.end();
            String tail = normalized.substring(matchEnd, Math.min(normalized.length(), matchEnd + 18));
            if (hour < 8 && containsAny(tail, "tarde", "noche")) {
                hour += 12;
            }
            return LocalTime.of(hour, minute);
        }

        Matcher wordMatcher = WORD_TIME_PATTERN.matcher(normalized);
        if (!wordMatcher.find()) {
            return null;
        }

        int hour = hourNumber(wordMatcher.group(1));
        int minute = switch (wordMatcher.group(2) == null ? "" : wordMatcher.group(2)) {
            case "media", "treinta" -> 30;
            case "cuarto", "quince" -> 15;
            default -> 0;
        };
        String tail = wordMatcher.group(3) == null ? "" : wordMatcher.group(3);
        if (hour < 8 && containsAny(tail, "tarde", "noche")) {
            hour += 12;
        }
        return LocalTime.of(hour, minute);
    }

    private String extractPatientName(String message) {
        Matcher patientMatcher = PATIENT_NAME_PATTERN.matcher(message);
        if (patientMatcher.find()) {
            String candidate = cleanPatientName(patientMatcher.group(1));
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
        if (!StringUtils.hasText(candidate)
                || containsAny(normalized, "turno", "consulta", "cardiologo", "traumatologo", "control", "manana", "hoy", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")) {
            return "";
        }
        return firstGivenName(candidate);
    }

    private String extractBarePatientName(String message) {
        String candidate = cleanPatientName(message);
        return validPatientName(candidate) ? firstGivenName(candidate) : "";
    }

    private String cleanPatientName(String value) {
        return value
                .replaceAll("(?iu)^\\s*(?:el|la)\\s+paciente\\s+", "")
                .replaceAll("(?iu)\\s+(?:el|la|los|las|manana|maÃ±ana|mañana|hoy|pasado|lunes|martes|miercoles|miércoles|jueves|viernes|sabado|sábado|domingo|a|con|y|para|por|turno|consulta|horario)\\b.*$", "")
                .replaceAll("[^\\p{L}\\s'-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean validPatientName(String candidate) {
        String normalized = normalize(candidate);
        if (!StringUtils.hasText(candidate)) {
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
                "domingo",
                "confirmo",
                "confirma",
                "confirmar",
                "dale",
                "ok"
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
        String requested = normalize(requestedType);
        if ("traumatology".equals(requested) || "follow-up".equals(requested) || "cardiology".equals(requested)) {
            return requested;
        }

        String normalized = normalize(message);
        if (containsAny(normalized, "cardio", "corazon")) {
            return "cardiology";
        }
        if (containsAny(normalized, "control", "seguimiento")) {
            return "follow-up";
        }
        return "traumatology";
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
            String patientName,
            boolean availabilityIntent,
            boolean bookingIntent,
            boolean rescheduleIntent,
            boolean confirmationIntent,
            boolean farewellIntent
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
                    nextPatientName,
                    availabilityIntent,
                    nextBookingIntent,
                    nextRescheduleIntent,
                    confirmationIntent,
                    farewellIntent
            );
        }
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
