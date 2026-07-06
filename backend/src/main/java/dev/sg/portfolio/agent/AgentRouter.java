package dev.sg.portfolio.agent;

import dev.sg.portfolio.domain.AgentRoute;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentRouter {

    private static final Pattern WORKFLOW = Pattern.compile(
            "workflow|automatizacion|automatizar|proceso|operaciones|backoffice|soporte|crm|ticket|notificacion|aprobacion|formulario|integracion",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern APPOINTMENTS = Pattern.compile(
            "turno|turnos|agenda|medic|medico|medica|doctor|doctora|consulta|cardio|trauma|reprogramar|reserva",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOCUMENTS = Pattern.compile(
            "pdf|documento|documentos|resumen|resumir|informe|archivo|extraccion|extraer",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern REPO = Pattern.compile(
            "repo|github|gitlab|codigo|code|spring|java|react|next|arquitectura|review",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<String, AgentRoute> AGENT_BY_ID = Map.of(
            "workflow-automation", new AgentRoute(
                    "workflow-automation",
                    "Workflow automation agent",
                    "Agente enfocado en procesos, integraciones y operaciones de negocio."
            ),
            "medical-appointment", new AgentRoute(
                    "medical-appointment",
                    "Medical appointment workflow agent",
                    "Agente especializado en agenda, disponibilidad, reservas y reprogramaciones."
            ),
            "document-summary", new AgentRoute(
                    "document-summary",
                    "Document intelligence agent",
                    "Agente especializado en resumen, extraccion y lectura responsable de documentos."
            ),
            "repo-context", new AgentRoute(
                    "repo-context",
                    "Repo context agent",
                    "Agente con foco en repositorios, arquitectura y analisis tecnico."
            ),
            "coordinator", new AgentRoute(
                    "coordinator",
                    "Multi-agent coordinator",
                    "Coordinador general para entradas abiertas o ambiguas."
            )
    );

    public AgentRoute resolve(String message, String requestedAgentId) {
        if (StringUtils.hasText(requestedAgentId)) {
            AgentRoute explicit = AGENT_BY_ID.get(requestedAgentId.trim().toLowerCase());
            if (explicit != null) {
                return new AgentRoute(
                        explicit.id(),
                        explicit.name(),
                        "Seleccion manual del agente desde el cliente."
                );
            }
        }
        return select(message);
    }

    public AgentRoute select(String message) {
        String value = message == null ? "" : message;

        if (WORKFLOW.matcher(value).find()) {
            return withReason("workflow-automation",
                    "El mensaje parece relacionado con automatizacion de procesos o integraciones de negocio.");
        }

        if (APPOINTMENTS.matcher(value).find()) {
            return withReason("medical-appointment",
                    "El mensaje parece tratar sobre turnos, agenda viva o acciones de reserva.");
        }

        if (DOCUMENTS.matcher(value).find()) {
            return withReason("document-summary",
                    "El mensaje pide trabajo sobre documentos, resumen o extraccion de informacion.");
        }

        if (REPO.matcher(value).find()) {
            return withReason("repo-context",
                    "El mensaje necesita contexto tecnico, repositorios o analisis de codigo.");
        }

        return withReason("coordinator",
                "Entrada general: el coordinador decide el flujo y arma una respuesta accionable.");
    }

    private AgentRoute withReason(String id, String reason) {
        AgentRoute base = AGENT_BY_ID.get(id);
        if (base == null) {
            return new AgentRoute("coordinator", "Multi-agent coordinator", reason);
        }
        return new AgentRoute(base.id(), base.name(), reason);
    }
}
