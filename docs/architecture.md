# Arquitectura del portfolio de Sebastian Gatica

Este proyecto es el portfolio interactivo de Sebastian Gatica. No es Egregor IA.

El objetivo principal es mostrar, mediante demos navegables, que Sebastian puede construir aplicaciones full-stack con IA aplicada: frontend React, backend Spring Boot WebFlux, integracion con OpenAI, voz realtime, herramientas de dominio, documentos y flujos con datos persistidos.

## Que es y que no es

- Es un portfolio profesional demo-first.
- Es una instancia de OpenAI integrada al portfolio de Sebastian Gatica.
- El asistente principal responde sobre el perfil profesional de Sebastian, sus habilidades, demos y enfoque tecnico.
- La demo de chat incluye texto, dictado y modo conversacion por voz.
- Las demos de turnos medicos y documentos son casos aparte para mostrar capacidades tecnicas.
- No es Egregor IA. Egregor IA es un proyecto separado.

## Estructura general

```text
src/
  App.tsx                         UI principal, rutas y paginas.
  components/
    AgentConsole.tsx              Demo principal: chat, dictado y conversacion.
    MedicalAppointmentDemo.tsx    Demo de turnos con voz y agenda viva.
    DocumentSummaryDemo.tsx       Demo de resumen de PDF.
  api/
    agentClient.ts                Cliente HTTP hacia /api.
  data/
    portfolio.ts                  Textos, stack y metadata visual.

backend/
  src/main/java/dev/sg/portfolio/
    controller/AgentController.java        Endpoints HTTP y SSE.
    service/PromptLibraryService.java      Carga prompts desde resources.
    service/PromptComposerService.java     Compone prompt core + agente + extensiones + contexto.
    service/OpenAiResponsesClient.java     Llamadas a OpenAI Responses API.
    service/OpenAiRealtimeClient.java      Sesiones OpenAI Realtime para voz.
    service/AppointmentDemoService.java    Agenda demo, disponibilidad, reservas y reprogramacion.
    service/PdfSummaryService.java         Validacion y resumen de PDF.
  src/main/resources/
    application.properties                 Configuracion por entorno.
    prompts/                               Prompts editables por archivo.
```

## Rutas del frontend

- `/`: inicio del portfolio.
- `/demos`: listado de demos.
- `/demos/chat` o `/demo`: asistente principal del portfolio.
- `/demos/turnos`: demo de agenda medica.
- `/demos/documentos`: demo de resumen de PDF.
- `/contacto`: contacto.

## Demo principal: chat, dictado y conversacion

Archivo frontend:

```text
src/components/AgentConsole.tsx
```

Responsabilidades:

- Muestra el chat.
- Envia mensajes al backend por streaming SSE.
- Permite dictar audio a texto.
- Permite conversar por voz con OpenAI Realtime.
- Permite activar extensiones de prompt como `business-context`.
- Muestra estado, trazas y agente activo.

Endpoint usado por chat:

```text
POST /api/agent/chat/stream
```

Endpoint usado por dictado:

```text
POST /api/agent/voice/session
```

Endpoint usado por conversacion:

```text
POST /api/agent/conversation/session
```

## Donde se retoca la identidad del asistente

Estos son los archivos mas importantes:

```text
backend/src/main/resources/prompts/core/system.md
```

Prompt base del chat. Aca se define que el asistente es una instancia de OpenAI integrada al portfolio de Sebastian Gatica. Este es el primer lugar que tenes que editar si queres cambiar la personalidad, limites o resumen profesional general.

```text
backend/src/main/resources/prompts/agents/coordinator.md
```

Prompt del agente por defecto del chat. Aca se ajusta el comportamiento del asistente principal del portfolio.

```text
backend/src/main/resources/prompts/extensions/business-context.txt
```

Contexto de negocio opcional. Esta extension esta activada por defecto en `AgentConsole.tsx`. Antes contenia la referencia a Egregor IA; ahora debe hablar del portfolio de Sebastian.

```text
backend/src/main/resources/prompts/realtime/conversation/base.md
```

Prompt base para el modo conversacion por voz de la demo principal. Si queres cambiar como se presenta cuando hablan por microfono, se edita aca.

Importante: si `.env` define `OPENAI_CONVERSATION_INSTRUCTIONS`, ese valor pisa `prompts/realtime/conversation/base.md` para la conversacion por voz.

## Como se compone el prompt de chat

El backend usa:

```text
PromptComposerService
```

Orden de composicion:

1. `prompts/core/system.md`
2. prompt del agente seleccionado en `prompts/agents/*.md`
3. extensiones seleccionadas en `prompts/extensions/*.txt|*.md`
4. contexto dinamico del turno, por ejemplo la hora actual

El archivo que carga los prompts es:

```text
backend/src/main/java/dev/sg/portfolio/service/PromptLibraryService.java
```

## Demo de turnos medicos

Frontend:

```text
src/components/MedicalAppointmentDemo.tsx
```

Backend:

```text
backend/src/main/java/dev/sg/portfolio/service/AppointmentDemoService.java
backend/src/main/java/dev/sg/portfolio/service/OpenAiRealtimeClient.java
```

Prompts:

```text
backend/src/main/resources/prompts/realtime/appointments/traumatology.md
backend/src/main/resources/prompts/realtime/appointments/follow-up.md
backend/src/main/resources/prompts/realtime/appointments/cardiology.md
```

Endpoints:

```text
GET  /api/appointments/demo/schedule
GET  /api/appointments/demo/activity
POST /api/appointments/demo/tools/availability
POST /api/appointments/demo/tools/book
POST /api/appointments/demo/tools/reschedule
POST /api/agent/appointment/session
```

La agenda usa una base local H2 por defecto, o PostgreSQL si configuras `PORTFOLIO_DB_URL`. La demo siembra turnos ocupados automaticamente para que el agente pueda negociar disponibilidad.

## Demo de documentos

Frontend:

```text
src/components/DocumentSummaryDemo.tsx
```

Backend:

```text
backend/src/main/java/dev/sg/portfolio/service/PdfSummaryService.java
backend/src/main/java/dev/sg/portfolio/service/OpenAiResponsesClient.java
```

Prompts:

```text
backend/src/main/resources/prompts/agents/document-summary.md
backend/src/main/resources/prompts/agents/document-summary-task.md
```

Endpoint:

```text
POST /api/agent/document/summary
```

El PDF se valida en memoria, se envia a OpenAI con `store=false` y no se conserva como archivo permanente.

## Configuracion

Archivo local:

```text
.env
```

Variables importantes:

```text
OPENAI_API_KEY=
OPENAI_MODEL=
OPENAI_DOCUMENT_MODEL=
OPENAI_VOICE_MODEL=
OPENAI_CONVERSATION_MODEL=
OPENAI_CONVERSATION_INSTRUCTIONS=
PORTFOLIO_DB_URL=
PORTFOLIO_CORS_ALLOWED_ORIGINS=
```

Archivo versionable:

```text
backend/src/main/resources/application.properties
```

Este archivo debe tener defaults seguros y no debe contener secrets reales.

## Donde habia salido Egregor IA

La referencia venia de estos archivos:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/resources/prompts/extensions/business-context.txt
```

Tambien podia persistir en respuestas si el backend estaba corriendo con una version vieja. Despues de cambiar prompts, hay que reiniciar el backend para que Spring vuelva a cargar los recursos.

## Comandos utiles

```bash
npm run dev
npm run build
npm run build:api
```

Healthcheck:

```text
GET http://localhost:8787/api/portfolio/health
```

Agenda:

```text
GET http://localhost:8787/api/appointments/demo/schedule?sessionId=debug&days=15
```

## Regla de mantenimiento

Cuando el portfolio hable de Sebastian, editar primero:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/resources/prompts/agents/coordinator.md
backend/src/main/resources/prompts/extensions/business-context.txt
backend/src/main/resources/prompts/realtime/conversation/base.md
```

Cuando una demo hable de su caso especifico, editar solo el prompt de esa demo.
