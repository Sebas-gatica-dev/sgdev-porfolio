package dev.sg.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentRouterTest {

    private final AgentRouter router = new AgentRouter();

    @Test
    void doesNotRouteRemovedVoiceDemo() {
        assertThat(router.select("Necesito una llamada por voz").id()).isEqualTo("coordinator");
    }

    @Test
    void routesWorkflowRequestsToWorkflowAutomationAgent() {
        assertThat(router.select("Necesito automatizar aprobaciones desde el CRM").id()).isEqualTo("workflow-automation");
    }

    @Test
    void routesCodeRequestsToRepoContextAgent() {
        assertThat(router.select("Analiza este codigo Java").id()).isEqualTo("repo-context");
    }

    @Test
    void routesAppointmentRequestsToMedicalWorkflowAgent() {
        assertThat(router.select("Necesito reprogramar un turno medico").id()).isEqualTo("medical-appointment");
    }

    @Test
    void routesDocumentRequestsToDocumentAgent() {
        assertThat(router.select("Resumi este PDF").id()).isEqualTo("document-summary");
    }

    @Test
    void honorsExplicitAgentSelection() {
        assertThat(router.resolve("mensaje general", "repo-context").id()).isEqualTo("repo-context");
    }
}
