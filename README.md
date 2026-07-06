# SG AI Agent Portfolio

Portfolio de Sebastian Gatica para presentar trabajo freelance en aplicaciones Java Full Stack con IA aplicada, arquitectura multiagente, automatizacion de workflows y demos navegables.

## Que incluye

- Frontend React/Vite con una interfaz de portfolio ADK-first.
- Backend Spring Boot WebFlux con runtime ADK-aligned: coordinator, especialistas, prompt composer, contexto dinamico y trazas SSE.
- Consumo de GPT por OpenAI Responses API usando `WebClient`.
- Prompts versionados por agente y extensiones (`core`, `agents`, `extensions`, `realtime`).
- Modo voz OpenAI en el chat con WebRTC y transcripcion Realtime.
- Modo conversacion OpenAI con respuesta hablada usando Realtime.
- Fallback gratuito de voz: dictado del navegador + Qwen + voz local del navegador.
- Demo de reserva medica con herramientas, agenda viva y persistencia PostgreSQL.
- Limite de tokens por IP con persistencia JDBC: 200 tokens, 10 por interaccion y 5 minutos de voz.
- Modelo gratuito local con FastAPI + Ollama + Qwen3 0.6B para continuar cuando se agota el cupo por IP.
- Modo demo local si no existe `OPENAI_API_KEY`.
- Dockerfile listo para deployar la app completa.

## Desarrollo

```bash
npm install
npm run dev
```

La web corre en `http://localhost:5173` y el backend en `http://localhost:8787`.

## OpenAI

Configurar:

```bash
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-5-mini
OPENAI_VOICE_MODEL=gpt-4o-mini-transcribe
OPENAI_VOICE_LANGUAGE=es
OPENAI_CONVERSATION_MODEL=gpt-realtime-mini
OPENAI_CONVERSATION_VOICE=alloy
PORTFOLIO_CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*
PORTFOLIO_IP_PROMPT_LIMIT_ENABLED=true
PORTFOLIO_IP_TOKEN_LIMIT_MAX_TOKENS=200
PORTFOLIO_IP_TOKEN_LIMIT_CHAT_COST=10
PORTFOLIO_IP_TOKEN_LIMIT_VOICE_COST=10
PORTFOLIO_IP_PROMPT_LIMIT_VOICE_SESSION_SECONDS=60
PORTFOLIO_IP_TOKEN_LIMIT_MAX_VOICE_SECONDS=300
PORTFOLIO_USAGE_ADMIN_TOKEN=change-me-admin-token
PORTFOLIO_FREE_MODEL_ENABLED=true
PORTFOLIO_FREE_MODEL_BASE_URL=http://localhost:8795
PORTFOLIO_FREE_MODEL_NAME=qwen3:0.6b
PORTFOLIO_FREE_MODEL_NUM_CTX=2048
PORTFOLIO_FREE_MODEL_MAX_TOKENS=420
PORTFOLIO_DB_URL=jdbc:postgresql://localhost:5432/sg_medical_appointments_demo
PORTFOLIO_DB_DRIVER=org.postgresql.Driver
PORTFOLIO_DB_USER=postgres
PORTFOLIO_DB_PASSWORD=postgres
```

El backend usa Spring WebFlux para llamar `POST https://api.openai.com/v1/responses` con `stream: true`.
Para voz, el backend genera un `client_secret` efimero con `POST /v1/realtime/client_secrets`
y el navegador abre WebRTC contra la Realtime API sin exponer `OPENAI_API_KEY`.
Cada sesion de voz queda limitada a 60 segundos y consume tokens de demo por IP.
La UI solo habilita la variante OpenAI de `Dictar` y `Conversar` cuando `/api/portfolio/health`
devuelve `openaiVoiceAvailable=true`: Realtime configurado y tokens/minutos suficientes para el costo
de voz. Cuando OpenAI no esta configurado o no alcanza el saldo de voz, los botones pasan al modo
gratuito: Web Speech API en el navegador para dictar, Qwen para responder y `speechSynthesis`
para leer la respuesta. Cuando una IP agota sus tokens, el chat permite solicitar mas tokens por mail
y tambien seguir con el modelo gratuito local sin consumir OpenAI.

Si al activar voz aparece un 403, el backend ya esta funcionando pero OpenAI rechazo la sesion:
normalmente falta billing/permisos de Realtime en la API key o acceso al modelo configurado en
`OPENAI_VOICE_MODEL`.

## Modelo gratuito local

El modo gratuito esta pensado para VPS chicos. La app nueva vive en:

```text
free-model-api/
```

Es un servicio FastAPI que llama a Ollama y transmite chunks SSE al backend Spring.
El modelo default es `qwen3:0.6b`, porque su quantization Q4 ocupa alrededor de 523 MB
y mantiene mejor dialogo/instrucciones que opciones ultra pequenas como Gemma 3 270M.

En Docker, `docker-compose.yml` levanta:

- `ollama`: runtime local del modelo.
- `ollama-pull`: descarga `PORTFOLIO_FREE_MODEL_NAME`.
- `free-model`: FastAPI en `http://free-model:8795`.
- `backend`: usa `PORTFOLIO_FREE_MODEL_BASE_URL=http://free-model:8795`.

Uso local fuera de Docker:

```bash
ollama pull qwen3:0.6b
cd free-model-api
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8795
```

Luego correr el backend con:

```bash
PORTFOLIO_FREE_MODEL_ENABLED=true
PORTFOLIO_FREE_MODEL_BASE_URL=http://localhost:8795
PORTFOLIO_FREE_MODEL_NAME=qwen3:0.6b
```

Flujo en la UI:

1. El usuario consume sus tokens por IP con OpenAI.
2. El backend emite el evento SSE `free_model_offer`.
3. El chat muestra un tooltip para usar el modelo gratuito o solicitar mas tokens.
4. Si el usuario acepta, el mismo prompt se reintenta con `runtime: "free"`.
5. `Dictar gratis` usa Web Speech API del navegador y no consume tokens OpenAI.
6. `Conversar gratis` funciona por turnos: navegador transcribe, Qwen responde por streaming y
   la voz local del navegador lee la respuesta.

## Voz gratuita y comparativa

La demo queda con dos calidades visibles:

- OpenAI Realtime: WebRTC, baja latencia, transcripcion y respuesta hablada integradas, pero depende
  de API key, billing/permisos y tokens por IP.
- Gratis/Qwen: no consume OpenAI; usa el dictado y TTS del navegador cuando estan disponibles. Es
  mas simple y menos realtime, pero mantiene la demo conversable aun sin saldo OpenAI.

Opciones para evolucionar el modo gratuito:

- Web Speech API: MDN documenta reconocimiento y sintesis de voz desde el navegador, con soporte
  posible en dispositivo segun navegador y paquetes de idioma:
  https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API/Using_the_Web_Speech_API
- Qwen3-ASR: modelos Qwen de reconocimiento de voz para multiples idiomas/dialectos, con modo
  streaming/offline:
  https://huggingface.co/Qwen/Qwen3-ASR-1.7B
- Qwen3-TTS: familia open-source de TTS Qwen con generacion streaming y soporte multilingue:
  https://github.com/QwenLM/Qwen3-TTS
- Piper: TTS local rapido para una alternativa liviana en VPS si Qwen3-TTS resulta pesado:
  https://github.com/rhasspy/piper

## Runtime ADK-aligned y prompt modular

El chat usa una arquitectura inspirada en Google ADK sin abandonar el backend Spring actual. La idea practica es bajar las primitivas `Agent`, `Tool`, `Session/State`, `Workflow`, `Event/Trace` y `Human gate` a piezas concretas del portfolio.

El backend resuelve por turno:

1. `AgentRouter` elige coordinator o especialista.
2. `DynamicContextService` junta contexto del turno si se pide.
3. `PromptComposerService` compone core prompt, prompt del agente, extensiones y contexto.
4. `OpenAiResponsesClient` hace streaming con `store=false`.
5. `AgentController` emite `session`, `agent`, `trace`, `chunk` y `done` por SSE.

Los prompts viven externos por archivo:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/resources/prompts/agents/*.md
backend/src/main/resources/prompts/extensions/*.txt|*.md
backend/src/main/resources/prompts/realtime/**/*
```

### Como funciona por turno

1. El backend carga `prompts/core/system.md` (prompt central).
2. Agrega el prompt del agente elegido (o el que el router detecta).
3. Inyecta extensiones pedidas por request.
4. Inyecta contexto dinamico del turno (reloj, http_get, etc).
5. Envia instrucciones compuestas a OpenAI y stream SSE.

### Payload de chat con agente/extensiones/contexto dinamico

Endpoint:

```text
POST /api/agent/chat/stream
Content-Type: application/json
```

Body ejemplo:

```json
{
  "message": "Necesito un plan de migracion modular",
  "sessionId": "optional-session-id",
  "agentId": "repo-context",
  "extensions": ["business-context", "code-style"],
  "dynamicContext": [
    { "type": "time_now", "name": "clock" },
    { "type": "http_get", "name": "status", "url": "https://httpbin.org/json", "timeoutMs": 5000 }
  ]
}
```

Tipos soportados en `dynamicContext`:

- `time_now`
- `params_echo`
- `http_get`

## Backend

```bash
cd backend
mvnw.cmd spring-boot:run
```

Healthcheck:

```text
GET http://localhost:8787/api/portfolio/health
```

## Compatibilidad con Sgdev-infra

El deploy multiproyecto vive en:

```text
C:\Users\CFOTech\Documents\Sgdev-infra
```

El refactor interno por dominios Java no cambia el contrato HTTP externo. Para que el gateway y el
admin sigan funcionando, estas rutas deben mantenerse:

```text
GET  /portfolio/api/portfolio/health
GET  /portfolio/api/admin/usage/ips
POST /portfolio/api/admin/usage/grant
POST /portfolio/api/agent/chat/stream
POST /portfolio/api/agent/document/summary
```

Variables compartidas con infra:

```env
VITE_BASE_PATH=/portfolio/
PORTFOLIO_USAGE_ADMIN_TOKEN=<mismo valor que SGDEV_PORTFOLIO_USAGE_ADMIN_TOKEN>
```

En `Sgdev-infra`, el control API usa:

```env
SGDEV_PORTFOLIO_API_BASE_URL=https://sgdev.com.ar/portfolio/api
SGDEV_PORTFOLIO_USAGE_ADMIN_TOKEN=<mismo valor que PORTFOLIO_USAGE_ADMIN_TOKEN>
```

## Docker

```bash
docker build -t sg-ai-agent-portfolio .
docker run --rm -p 8787:8787 -e OPENAI_API_KEY=sk-... sg-ai-agent-portfolio
```

Luego abrir `http://localhost:8787`.

## Demo de turnos medicos

La demo de turnos medicos vive en:

```text
/demos/turnos
```

Usa una base PostgreSQL separada llamada `sg_medical_appointments_demo` sobre el servidor local ya levantado.
La agenda muestra 15 dias hacia adelante, siembra turnos ocupados de tres profesionales y expone tres
herramientas reales para el agente de voz:

- buscar disponibilidad;
- reservar un turno;
- reprogramar el turno activo dentro de la misma llamada.

## Mini documentacion interna del proyecto

Esta seccion explica donde vive cada decision importante de la app. La idea es poder volver
despues y entender por que la demo habla como habla, donde se define la navegacion, donde se
conecta OpenAI, donde se formatea la respuesta y que archivos habria que tocar para cambiar el
comportamiento.

### Mapa rapido de carpetas

```text
.
|-- src/
|   |-- App.tsx
|   |-- app/
|   |   |-- routing.ts
|   |   `-- theme.ts
|   |-- api/
|   |   `-- agentClient.ts
|   |-- components/
|   |   |-- AgentConsole.tsx
|   |   |-- MedicalAppointmentDemo.tsx
|   |   |-- DocumentSummaryDemo.tsx
|   |   |-- agent-console/
|   |   |-- appointments/
|   |   |-- demo/
|   |   `-- shared/
|   |-- pages/
|   |   |-- HomePage.tsx
|   |   |-- DemosPage.tsx
|   |   |-- DemoDetailPages.tsx
|   |   `-- ContactPage.tsx
|   |-- data/
|   |   |-- portfolio.ts
|   |   `-- siteContent.tsx
|   |-- main.tsx
|   `-- styles.css
|-- public/
|   |-- favicon.svg
|   `-- sgdev.jpg
|-- backend/
|   |-- src/main/java/dev/sg/portfolio/
|   |   |-- PortfolioApiApplication.java
|   |   |-- agent/
|   |   |-- appointment/
|   |   |-- contact/
|   |   |-- config/
|   |   |-- document/
|   |   |-- domain/
|   |   |-- portfolio/
|   |   |-- shared/web/
|   |   |-- usage/
|   |   `-- service/
|   `-- src/main/resources/
|       |-- application.properties
|       `-- prompts/
|-- vite.config.ts
|-- package.json
`-- Dockerfile
```

### Navegacion y paginas

La navegacion principal esta definida en:

```text
src/App.tsx
src/app/routing.ts
src/data/siteContent.tsx
```

`App.tsx` conserva el shell visual, tema, header/footer y render de la ruta activa. Las rutas
internas viven en `src/app/routing.ts` con el tipo `Route` y el array `routes`.

```ts
type Route =
  | '/'
  | '/demos'
  | '/demos/chat'
  | '/demos/turnos'
  | '/demos/documentos'
  | '/demo'
  | '/contacto'

const routes: Route[] = ['/', '/demos', '/demos/chat', '/demos/turnos', '/demos/documentos', '/demo', '/contacto']
```

No se esta usando React Router por ahora. La app tiene un router liviano hecho a mano con
`window.history.pushState`, `popstate` y estado React.

La funcion clave es:

```ts
function navigate(path: Route) {
  window.history.pushState({}, '', path)
  setRoute(path)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
```

El componente `PageLink` reemplaza a los links normales cuando queremos navegar dentro de la SPA
sin recargar toda la pagina.

```text
src/components/PageLink.tsx
```

El render condicional de paginas esta en `App.tsx`, pero las paginas viven en `src/pages/`:

```tsx
{route === '/' && <HomePage onNavigate={navigate} />}
{route === '/demos' && <DemosPage onNavigate={navigate} />}
{(route === '/demos/chat' || route === '/demo') && <AgentChatDemoPage title={pageTitle} />}
{route === '/demos/turnos' && <MedicalAppointmentDemoPage />}
{route === '/demos/documentos' && <DocumentDemoPage />}
{route === '/contacto' && <ContactPage />}
```

Por eso, si mas adelante se quiere agregar una pagina nueva, hay que tocar:

```text
src/app/routing.ts
src/App.tsx
src/data/siteContent.tsx
```

Puntos a modificar:

- Agregar el path al tipo `Route`.
- Agregar el path al array `routes`.
- Agregar un `PageLink` al navbar si corresponde.
- Agregar el render condicional de la pagina.
- Crear la funcion/componente de la pagina.

### Pagina Home

La home vive dentro de:

```text
src/pages/HomePage.tsx
```

Incluye:

- Hero principal.
- Mensaje comercial.
- CTA a `/demos`.
- CTA a `/contacto`.
- Panel visual de arquitectura con efecto Atropos.

El panel visual del home tiene Atropos porque es una zona hero/visual, no una card operativa.
Ahi si se dejaron algunos `data-atropos-offset` internos para generar profundidad:

```tsx
<div className="visual-header" data-atropos-offset="4">
  <img src="/favicon.svg" alt="SG AI mark" data-atropos-offset="8" />
</div>
```

Esto es intencional: en el home queda como una pieza visual. En las cards normales se quito el
desplazamiento interno del contenido para que no moleste.

### Pagina Demos

La pagina mas importante del portfolio vive en:

```text
src/pages/DemosPage.tsx
```

Ruta:

```text
/demos
```

Esta pagina funciona como hub de experiencias. Hoy tiene tres cards:

- `Portfolio assistant`: redirige a `/demos/chat`, la demo de chat y voz integrada al portfolio.
- `Medical appointment workflow`: redirige a `/demos/turnos`, la demo de toma de turnos.
- `Document intelligence workflow`: redirige a `/demos/documentos`, la demo de resumen de documentos.

El array que define esas cards esta en:

```text
src/data/siteContent.tsx -> demoCards
```

La ruta antigua:

```text
/demo
```

se mantiene como compatibilidad y renderiza la misma experiencia que `/demos/chat`.

### Demo principal de chat

La demo actual vive en:

```text
src/pages/DemoDetailPages.tsx -> AgentChatDemoPage
src/components/AgentConsole.tsx
```

Ruta nueva:

```text
/demos/chat
```

Esta demo es la base donde despues se integrarian:

- Contexto de repositorio.
- Workflows y aprobaciones.
- Voz a texto.
- Texto a voz.
- Historial y sesiones.
- Human-in-the-loop.

### Demos de turnos y documentos

Las otras demos activas estan en:

```text
src/pages/DemoDetailPages.tsx -> MedicalAppointmentDemoPage
src/pages/DemoDetailPages.tsx -> DocumentDemoPage
src/components/MedicalAppointmentDemo.tsx
src/components/DocumentSummaryDemo.tsx
```

Rutas:

```text
/demos/turnos
/demos/documentos
```

Son integraciones reales del portfolio: turnos con agenda viva y documentos con tratamiento efimero.

### Contenido estructural y demos activas

El contenido estructural de navegacion, cards de demos, enlaces sociales y resumen profesional vive en:

```text
src/data/siteContent.tsx
```

El contenido tecnico mas amplio del portfolio sigue en:

```text
src/data/portfolio.ts
```

La app activa hoy expone estas paginas:

- `src/pages/HomePage.tsx`: inicio, hero y perfil profesional.
- `src/pages/DemosPage.tsx`: hub de demos.
- `src/pages/DemoDetailPages.tsx`: wrappers de chat, turnos y documentos.
- `src/pages/ContactPage.tsx`: formulario y enlaces sociales.

Las demos disponibles son:

- `Portfolio assistant`: `/demos/chat` y compatibilidad `/demo`.
- `Medical appointment workflow`: `/demos/turnos`.
- `Document intelligence workflow`: `/demos/documentos`.

Para agregar una pagina nueva, primero se agrega la ruta en `src/app/routing.ts`, luego se crea la
pagina en `src/pages/` y finalmente se enlaza desde `src/data/siteContent.tsx` si debe aparecer en
navegacion, home o cards.

### Pagina Contacto

La pagina de contacto vive en:

```text
src/pages/ContactPage.tsx
```

La foto esta en:

```text
public/sgdev.jpg
```

El navegador la consume como:

```text
/sgdev.jpg
```

La card de foto tiene Atropos, pero sin highlight ni sombra interna. El nombre esta puesto como
overlay sobre la imagen, no como bloque blanco separado.

Estilos relevantes:

```text
src/styles.css
```

Clases:

- `.contact-page`
- `.contact-copy`
- `.contact-actions`
- `.contact-facts`
- `.contact-photo-tilt`
- `.contact-photo-card`

Links sociales actuales:

```tsx
<a href="https://www.linkedin.com/" target="_blank" rel="noreferrer">
<a href="https://github.com/" target="_blank" rel="noreferrer">
<a href="mailto:contacto@sg-dev.local">
```

Cuando se tengan URLs reales, se cambian en:

```text
src/data/siteContent.tsx -> profileLinks
src/pages/ContactPage.tsx -> texto y formulario
```

### Atropos

Atropos esta instalado como dependencia npm:

```text
package.json -> dependencies -> atropos
```

Se importa el CSS global en:

```text
src/main.tsx
```

```ts
import 'atropos/css'
```

Y el componente React se importa en:

```text
src/App.tsx
```

```ts
import Atropos from 'atropos/react'
```

Se usa en zonas estrategicas:

- Panel visual del home.
- Cards de productos.
- Cards de arquitectura.
- Packs comerciales.
- Foto de contacto.

Configuracion general elegida:

- `highlight={false}` para evitar brillo raro.
- `shadow={false}` para evitar sombras artificiales internas.
- `rotateXMax` y `rotateYMax` bajos para que se sienta premium, no exagerado.
- `rotateTouch="scroll-y"` para no romper scroll en mobile.

CSS relevante:

```text
src/styles.css -> .tilt-wrap
```

Se fuerza a ocultar highlight/sombra de Atropos:

```css
.tilt-wrap .atropos-highlight,
.tilt-wrap .atropos-shadow {
  display: none;
}
```

Decision de UX:

- En cards comunes, se mueve solo la card completa.
- En el panel hero del home, algunos elementos internos tienen parallax.
- En contacto, la foto se mueve como una pieza completa, no el nombre separado.

### Demo del agente

La demo visual vive en:

```text
src/components/AgentConsole.tsx
```

Este componente maneja:

- Caja de texto.
- Boton enviar.
- Lista de mensajes.
- Estado de streaming.
- Trazas del flujo.
- Boton copiar respuesta.
- Prompts sugeridos.
- Formateo visual de la respuesta del agente.

Prompts sugeridos:

```text
src/components/AgentConsole.tsx -> starterPrompts
```

Importante: esos prompts no se envian solos. Solo rellenan el textarea cuando el usuario hace click.
La caja arranca vacia.

El submit vive en:

```text
src/components/AgentConsole.tsx -> handleSubmit
```

Ese metodo:

1. Toma el texto del usuario.
2. Agrega el mensaje del usuario al chat.
3. Crea un mensaje de asistente vacio.
4. Abre el stream contra el backend.
5. Va agregando chunks a medida que llegan.
6. Marca si la respuesta fue live OpenAI o fallback demo.

El boton copiar vive en:

```text
src/components/AgentConsole.tsx -> copyMessage
```

Usa:

```ts
navigator.clipboard.writeText(message.content)
```

### Formato visual de respuestas

El renderer de respuestas vive en:

```text
src/components/agent-console/messageFormatting.tsx -> FormattedMessage
src/components/agent-console/messageFormatting.tsx -> buildBlocks
src/components/agent-console/messageFormatting.tsx -> normalizeAssistantText
```

No es markdown completo. Es un parser simple para que la demo se vea mejor:

- Detecta titulos `###`.
- Detecta bullets `-`.
- Detecta listas numeradas `1.`
- Separa algunos textos compactos cuando vienen pegados.

Por eso, si GPT devuelve una respuesta larga, la UI intenta transformarla en bloques mas legibles.

Si mas adelante se quiere soportar markdown real, una alternativa seria instalar algo como:

```text
react-markdown
```

Pero por ahora se dejo liviano y controlado.

### Cliente frontend del stream SSE

El cliente que llama al backend vive en:

```text
src/api/agentClient.ts
```

Funcion principal:

```ts
streamAgentResponse(payload, handlers)
```

Hace:

```ts
fetch('/api/agent/chat/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(payload),
})
```

Lee el body como stream:

```ts
const reader = response.body.getReader()
```

Y parsea Server-Sent Events con:

```text
dispatchSseEvent
```

Eventos que espera:

- `session`
- `agent`
- `trace`
- `chunk`
- `done`

La UI no llama directo a OpenAI. La UI llama al backend propio. El backend decide si usa OpenAI o
fallback local.

### Proxy de Vite

El proxy local esta en:

```text
vite.config.ts
```

Config importante:

```ts
server: {
  proxy: {
    '/api': {
      target: env.VITE_PROXY_TARGET || 'http://localhost:8787',
      changeOrigin: true,
    },
  },
}
```

Eso permite que el frontend llame:

```text
/api/agent/chat/stream
```

Y Vite lo mande al backend:

```text
http://localhost:8787/api/agent/chat/stream
```

En desarrollo:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8787`

### Backend Spring WebFlux

El backend vive en:

```text
backend/src/main/java/dev/sg/portfolio
```

La app principal:

```text
backend/src/main/java/dev/sg/portfolio/PortfolioApiApplication.java
```

Controladores por dominio:

```text
backend/src/main/java/dev/sg/portfolio/agent/AgentController.java
backend/src/main/java/dev/sg/portfolio/appointment/AppointmentController.java
backend/src/main/java/dev/sg/portfolio/contact/ContactController.java
backend/src/main/java/dev/sg/portfolio/document/DocumentController.java
backend/src/main/java/dev/sg/portfolio/portfolio/PortfolioController.java
backend/src/main/java/dev/sg/portfolio/usage/PortfolioUsageController.java
backend/src/main/java/dev/sg/portfolio/usage/UsageAdminController.java
```

Servicios de dominio e infraestructura:

```text
backend/src/main/java/dev/sg/portfolio/agent/AgentRouter.java
backend/src/main/java/dev/sg/portfolio/agent/PromptComposerService.java
backend/src/main/java/dev/sg/portfolio/appointment/AppointmentDemoService.java
backend/src/main/java/dev/sg/portfolio/document/PdfSummaryService.java
backend/src/main/java/dev/sg/portfolio/usage/IpPromptLimitService.java
backend/src/main/java/dev/sg/portfolio/service/OpenAiResponsesClient.java
backend/src/main/java/dev/sg/portfolio/service/OpenAiRealtimeClient.java
backend/src/main/java/dev/sg/portfolio/service/FreeModelClient.java
```

Modelos/eventos:

```text
backend/src/main/java/dev/sg/portfolio/domain
```

### Endpoints backend

Los endpoints estan distribuidos por dominio:

```text
backend/src/main/java/dev/sg/portfolio/portfolio/PortfolioController.java
backend/src/main/java/dev/sg/portfolio/agent/AgentController.java
backend/src/main/java/dev/sg/portfolio/appointment/AppointmentController.java
backend/src/main/java/dev/sg/portfolio/document/DocumentController.java
backend/src/main/java/dev/sg/portfolio/usage/PortfolioUsageController.java
backend/src/main/java/dev/sg/portfolio/usage/UsageAdminController.java
```

Health:

```text
GET /api/portfolio/health
```

Devuelve si OpenAI esta configurado:

```json
{
  "ok": true,
  "mode": "openai-webflux-ready",
  "openaiConfigured": true
}
```

Blueprint:

```text
GET /api/portfolio/blueprint
```

Sirve para exponer una descripcion simple de arquitectura.

Chat streaming:

```text
POST /api/agent/chat/stream
```

Produce:

```text
text/event-stream
```

Este endpoint es el corazon de la demo.

### Flujo backend del chat

Archivo:

```text
backend/src/main/java/dev/sg/portfolio/agent/AgentController.java
```

Metodo:

```java
public Flux<ServerSentEvent<Object>> stream(@RequestBody ChatStreamRequest request)
```

Flujo:

1. Lee el mensaje.
2. Crea o reutiliza `sessionId`.
3. Llama a `AgentRouter` para elegir ruta/agente.
4. Emite eventos iniciales:
   - `session`
   - `trace`
   - `agent`
5. Si OpenAI esta configurado, llama a `openAiBody`.
6. Si OpenAI no esta configurado, usa `LocalAgentSimulator`.
7. Al final emite `done`.

### Router de agente

Archivo:

```text
backend/src/main/java/dev/sg/portfolio/agent/AgentRouter.java
```

Este servicio decide la "ruta activa" segun palabras clave del mensaje.

Ejemplos conceptuales:

- Si habla de voz, venta, telefono: ruta de agente de voz comercial.
- Si habla de repo, GitHub, codigo: ruta de contexto de repositorio.
- Si habla de workflows, procesos, aprobaciones: ruta de automatizacion.
- Si no detecta nada especial: ruta general multiagente.

Esto no es un sistema multiagente completo todavia. Es una capa de routing para mostrar la idea
de como se orquestaria un sistema con agentes especializados.

### Cliente OpenAI con WebFlux

Archivo:

```text
backend/src/main/java/dev/sg/portfolio/service/OpenAiResponsesClient.java
```

Metodo principal:

```java
public Flux<String> streamText(String message, String instructions)
```

Hace un POST a:

```text
/responses
```

Con payload:

```java
payload.put("model", properties.model());
payload.put("instructions", instructions);
payload.put("input", List.of(Map.of(
    "role", "user",
    "content", message == null ? "" : message
)));
payload.put("stream", true);
```

La llamada usa:

```java
WebClient
```

Y devuelve:

```java
Flux<String>
```

Eso es importante porque el backend no espera toda la respuesta para devolverla. Va leyendo chunks
de OpenAI y reenviandolos a la UI por SSE.

### Donde esta el prompt del asistente

El flujo principal ya no concentra el prompt en un unico metodo. La composicion vive en:

```text
backend/src/main/java/dev/sg/portfolio/agent/PromptComposerService.java
backend/src/main/java/dev/sg/portfolio/agent/PromptLibraryService.java
backend/src/main/resources/prompts/
```

El orden de carga es:

1. `prompts/core/system.md`
2. prompt del agente en `prompts/agents/*.md`
3. extensiones en `prompts/extensions/*.txt|*.md`
4. contexto dinamico recolectado por el backend

`OpenAiResponsesClient` recibe el string final y se ocupa de transportarlo por WebFlux a OpenAI.
Mantiene instrucciones internas solo como fallback de compatibilidad para llamadas antiguas.

Actualmente los prompts le dicen cosas como:

- Responder en espanol rioplatense.
- Responder claro, breve y profesional.
- No inventar una consigna interna.
- No afirmar conexion a sistemas privados.
- Explicar arquitectura multiagente, WebFlux, streaming SSE, OpenAI, human-in-the-loop y trabajo por hitos.
- Usar markdown simple.
- Usar maximo 5 secciones.
- No cerrar preguntando que hacer ahora.

Este es el lugar exacto que explica por que la demo habla de WebFlux, arquitectura, hitos y forma
de abordaje.

### Por que el agente responde como consultor y no solo como generador de codigo

La demo actualmente esta pensada como portfolio comercial/tecnico. El objetivo no es solo que tire
codigo, sino que muestre criterio profesional.

La respuesta sale de la combinacion de estas piezas:

1. `AgentRouter`

   Decide una ruta conceptual: repo, voz, workflow, general.

2. Prompts + `PromptComposerService`

   Combinan sistema, agente, extensiones y contexto dinamico para mostrar criterio de producto,
   arquitectura, WebFlux, streaming, hitos y human-in-the-loop.

3. `AgentConsole`

   Muestra la respuesta como demo navegable con trazas y ruta activa.

4. `portfolio.ts`

   Toda la narrativa del sitio esta orientada a vender IA aplicada, no solo snippets aislados.

Por eso, cuando se le pide algo como "genera codigo" o "disena una solucion", el modelo tiende a
responder:

- Como lo abordaria.
- Que arquitectura usaria.
- Que endpoints o modulos tendria.
- Que hito haria primero.
- Como lo venderia o implementaria.

Eso es intencional para la version actual: modo portfolio/consultor.

Si mas adelante se quiere que responda como generador de codigo puro, el cambio deberia hacerse en:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/resources/prompts/agents/*.md
backend/src/main/java/dev/sg/portfolio/agent/AgentRouter.java
src/components/AgentConsole.tsx
```

### Posibles modos futuros del agente

Todavia no estan implementados, pero el diseno podria evolucionar a modos:

```text
consultor
tecnico
codigo
portfolio
```

#### Modo consultor

Responderia con:

- Problema.
- Solucion propuesta.
- Arquitectura.
- Hitos.
- Riesgos.
- Valor de negocio.

#### Modo tecnico

Responderia con:

- Clases.
- Endpoints.
- DTOs.
- Servicios.
- Flujo WebFlux.
- Tests.

#### Modo codigo

Responderia con:

- Snippets concretos.
- Archivos sugeridos.
- Pseudodiff.
- Implementacion paso a paso.

#### Modo portfolio

Responderia corto, visual y facil de leer para:

- Recruiters.
- Clientes.
- Gente que prueba la demo rapido.

### Donde cambiar el tono del agente

Archivo:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/resources/prompts/agents/coordinator.md
```

Ejemplo de cambio futuro si se quisiera mas codigo:

```text
Cuando el usuario pida codigo, responde primero con una estructura de archivos,
luego clases Java/TypeScript concretas y evita explicaciones comerciales largas.
```

Ejemplo de cambio futuro si se quisiera mas comercial:

```text
Responde como propuesta freelance: problema, solucion, entregables, plazo, costo estimado y siguiente paso.
```

Ejemplo de cambio futuro si se quisiera mas demo/portfolio:

```text
Responde en maximo 8 bullets, sin secciones largas, priorizando claridad para una persona no tecnica.
```

### Fallback local

Archivo:

```text
backend/src/main/java/dev/sg/portfolio/agent/LocalAgentSimulator.java
```

Se usa cuando:

- No hay `OPENAI_API_KEY`.
- OpenAI devuelve error.
- Hay algun problema de streaming live.

El fallback genera eventos SSE igual que OpenAI, pero con respuestas locales.

Esto permite que la demo no quede rota si:

- Faltan tokens.
- La key no funciona.
- OpenAI corta la respuesta.
- Hay rate limit.

### Eventos SSE del backend

Eventos principales:

```text
session
trace
agent
chunk
done
```

`session`

Identifica la sesion del chat.

`trace`

Muestra pasos del flujo, por ejemplo:

- Spring WebFlux backend.
- OpenAI Responses API.
- Local simulator.

`agent`

Muestra la ruta activa elegida por `AgentRouter`.

`chunk`

Texto parcial de respuesta.

`done`

Marca que termino el stream y si fue live OpenAI o fallback demo.

### Configuracion OpenAI

Archivo:

```text
backend/src/main/resources/application.properties
```

Variables relevantes:

```text
server.port
openai.api-key
openai.base-url
openai.model
```

Tambien existe:

```text
.env.example
```

Con:

```text
OPENAI_API_KEY=
OPENAI_MODEL=gpt-5-mini
OPENAI_VOICE_MODEL=gpt-4o-mini-transcribe
OPENAI_VOICE_LANGUAGE=es
OPENAI_VOICE_PROMPT=portfolio, agentes, workflows, WebFlux, OpenAI, Sebastian Gatica
OPENAI_CONVERSATION_MODEL=gpt-realtime-mini
OPENAI_CONVERSATION_VOICE=alloy
OPENAI_CONVERSATION_INSTRUCTIONS=Sos el agente de voz del portfolio de Sebastian Gatica. Responde en espanol rioplatense, breve, claro y profesional.
OPENAI_REALTIME_WEBRTC_URL=https://api.openai.com/v1/realtime/calls
PORTFOLIO_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
PORTFOLIO_CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*
PORTFOLIO_IP_PROMPT_LIMIT_ENABLED=true
PORTFOLIO_IP_TOKEN_LIMIT_MAX_TOKENS=200
PORTFOLIO_IP_TOKEN_LIMIT_CHAT_COST=10
PORTFOLIO_IP_TOKEN_LIMIT_VOICE_COST=10
PORTFOLIO_IP_PROMPT_LIMIT_VOICE_SESSION_SECONDS=60
PORTFOLIO_IP_TOKEN_LIMIT_MAX_VOICE_SECONDS=300
PORTFOLIO_USAGE_ADMIN_TOKEN=change-me-admin-token
PORTFOLIO_DB_URL=jdbc:h2:file:./data/portfolio-limits
```

Nota importante de seguridad:

```text
No conviene hardcodear API keys reales en application.properties si el repo se va a subir a GitHub.
Lo ideal es usar variables de entorno, secretos del entorno de deploy o un archivo local ignorado por Git.
```

El modelo default es:

```text
gpt-5-mini
```

Eso esta pensado para que la demo sea economica.

### Configuracion WebClient

Archivos:

```text
backend/src/main/java/dev/sg/portfolio/config/OpenAiProperties.java
backend/src/main/java/dev/sg/portfolio/config/WebClientConfig.java
```

`OpenAiProperties` carga:

- API key.
- Base URL.
- Modelo.

`WebClientConfig` arma el cliente HTTP para OpenAI.

### CORS

Archivo:

```text
backend/src/main/java/dev/sg/portfolio/config/CorsConfig.java
```

Permite que el frontend local pueda hablar con el backend en desarrollo.

### Dominio de eventos

Carpeta:

```text
backend/src/main/java/dev/sg/portfolio/domain
```

Clases relevantes:

- `ChatStreamRequest`: request de entrada del chat.
- `AgentRoute`: ruta/agente elegido.
- `AgentTrace`: item de trazabilidad.
- `TextChunk`: chunk de texto.
- `SessionEvent`: evento de sesion.
- `DoneEvent`: fin del stream.

Estos records son los objetos que el backend serializa como data en SSE.

### Relacion frontend-backend

Flujo completo:

```text
Usuario escribe en AgentConsole
        ↓
src/api/agentClient.ts hace POST /api/agent/chat/stream
        ↓
vite.config.ts proxya /api a localhost:8787
        ↓
AgentController recibe el mensaje
        ↓
AgentRouter elige ruta conceptual
        ↓
OpenAiResponsesClient llama OpenAI con WebClient
        ↓
OpenAI devuelve stream
        ↓
Backend transforma chunks en SSE
        ↓
Frontend actualiza el mensaje del asistente en vivo
```

Si OpenAI falla:

```text
OpenAI error
        ↓
AgentController activa onErrorResume
        ↓
LocalAgentSimulator genera respuesta local
        ↓
Frontend recibe respuesta demo
```

### Como queda implementado ADK en este portfolio

Este proyecto sigue siendo standalone: no llama a QAura ni a servicios internos del trabajo. La
implementacion adopta los patrones operativos de Google ADK dentro del stack actual:

```text
React UI
  -> AgentController
  -> AgentRouter
  -> DynamicContextService
  -> PromptComposerService
  -> OpenAiResponsesClient / OpenAiRealtimeClient
  -> SSE traces + chunks
```

Equivalencias practicas:

- `Agent`: coordinator, repo-context, workflow-automation, medical-appointment y document-summary.
- `Tool`: agenda medica, resumen PDF, sesiones Realtime, contexto dinamico y futuras APIs externas.
- `Session/State`: `sessionId`, tokens por IP, estado de llamada, turno activo y actividad de base.
- `Workflow`: routing, disponibilidad -> reserva -> reprogramacion, PDF -> resumen -> descarga.
- `Event trace`: eventos SSE `agent` y `trace` visibles en la consola.
- `Human gate`: las acciones con impacto se modelan como tools y deben poder confirmar antes de persistir.

No es una dependencia Python de ADK dentro del proceso Java. Es una implementacion alineada con su
arquitectura para que el portfolio se pueda deployar hoy y migrar a un sidecar ADK Python si mas
adelante conviene.

### Donde aparece WebFlux en la narrativa

WebFlux aparece en varios lugares:

```text
src/App.tsx
src/components/AgentConsole.tsx
backend/src/main/java/dev/sg/portfolio/agent/AgentController.java
backend/src/main/java/dev/sg/portfolio/service/OpenAiResponsesClient.java
README.md
```

Motivo:

El portfolio intenta vender una capacidad concreta:

```text
Puedo construir backends reactivos que consumen LLMs por streaming y exponen una demo usable.
```

Por eso GPT tambien recibe instrucciones que mencionan WebFlux. La demo quiere mostrar no solo el
resultado, sino el criterio tecnico detras.

### Donde se define el diseño visual

Archivo:

```text
src/styles.css
```

Clases principales:

- `.nav`
- `.hero-band`
- `.hero-visual`
- `.statement-band`
- `.module-grid`
- `.module-card`
- `.architecture-grid`
- `.architecture-node`
- `.console-shell`
- `.console-grid`
- `.message-list`
- `.assistant-content`
- `.contact-page`
- `.contact-photo-card`
- `.pack-card`
- `.tilt-wrap`

Paleta:

```css
--ink
--muted
--line
--paper
--soft
--navy
--cyan
--green
--coral
--blue
```

Decision visual actual:

- Minimalista.
- Cards con radio de 8px.
- Fondo de grilla suave.
- Colores sobrios.
- Atropos sutil.
- Nada demasiado "landing page de humo".

### Donde tocar si algo se ve mal

Si se ve mal la foto:

```text
src/styles.css -> .contact-photo-card
src/styles.css -> .contact-photo-card img
src/pages/ContactPage.tsx
```

Si se ve mal el tilt:

```text
src/pages/HomePage.tsx -> props de Atropos
src/styles.css -> .tilt-wrap
```

Si se ve mal el chat:

```text
src/components/AgentConsole.tsx
src/styles.css -> .message-*, .assistant-content, .console-*
```

Si se ve mal una card de demo:

```text
src/data/siteContent.tsx -> demoCards
src/components/demo/DemoCardGrid.tsx
src/styles.css -> .demo-card
```

Si se ve mal la respuesta de GPT:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/resources/prompts/agents/coordinator.md
src/components/agent-console/messageFormatting.tsx -> FormattedMessage/buildBlocks
```

### Donde tocar si el agente responde demasiado largo

Archivo:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/resources/prompts/agents/coordinator.md
backend/src/main/java/dev/sg/portfolio/agent/PromptComposerService.java
```

Cambiar reglas como:

```text
Maximo 5 secciones.
Usa bullets breves.
No cierres preguntando que queres hacer ahora.
```

Tambien se podria agregar:

```text
Maximo 350 palabras.
No incluyas planes por hito salvo que el usuario los pida.
Si el usuario pide codigo, prioriza codigo.
```

### Donde tocar si el agente debe generar codigo real

Puntos probables:

```text
backend/src/main/resources/prompts/core/system.md
backend/src/main/java/dev/sg/portfolio/agent/AgentRouter.java
backend/src/main/java/dev/sg/portfolio/agent/PromptComposerService.java
src/components/AgentConsole.tsx
src/api/agentClient.ts
```

Idea futura:

Agregar un selector en la UI:

```text
Modo: Consultor | Tecnico | Codigo | Portfolio
```

Ese modo podria enviarse en el request:

```json
{
  "message": "...",
  "sessionId": "...",
  "mode": "codigo"
}
```

Entonces el backend podria cambiar instrucciones segun modo.

### Donde tocar si se quiere GitHub real

Todavia no esta implementada una integracion real con GitHub.

Hoy la demo habla de "repo context" como concepto, no clona ni lee repositorios.

Para hacerlo real habria que agregar:

- OAuth o token GitHub.
- Endpoint para registrar repo.
- Servicio backend para leer archivos.
- Filtro de archivos.
- Resumen/indexado.
- Posible vector store.
- Tools para buscar contexto.
- UI para conectar/desconectar repo.

Archivos nuevos probables:

```text
backend/src/main/java/dev/sg/portfolio/repo/GitHubRepositoryClient.java
backend/src/main/java/dev/sg/portfolio/repo/RepoContextService.java
backend/src/main/java/dev/sg/portfolio/repo/RepoController.java
src/components/RepoConnector.tsx
```

### Donde tocar voz real

El chat ya tiene modo voz por WebRTC: el usuario activa el microfono, OpenAI Realtime
devuelve deltas de transcripcion y la UI los muestra en vivo en el chat mientras completa
el textarea.

Tambien tiene modo conversacion: el navegador envia audio por WebRTC, `gpt-realtime-mini`
responde con audio, y los transcripts de usuario/asistente quedan visibles en el chat.
El modo dictado y el modo conversacion son excluyentes: al activar uno se detiene el otro.

Piezas actuales:

```text
backend/src/main/java/dev/sg/portfolio/service/OpenAiRealtimeClient.java
backend/src/main/java/dev/sg/portfolio/agent/AgentController.java
src/components/AgentConsole.tsx
src/api/agentClient.ts
```

Lo siguiente para expandir voz seria sumar mas tools por dominio, confirmaciones explicitas
y handoff humano cuando el caso lo requiera.

### Donde tocar si se quiere deploy real

Archivos actuales:

```text
Dockerfile
package.json
backend/pom.xml
```

Posibles futuros:

```text
docker-compose.yml
.github/workflows/deploy.yml
terraform/
```

Deploy posible:

- Cloud Run.
- Render.
- Railway.
- VPS con Docker.

### Comandos utiles

Comandos operativos completos:

```bash
npm run ops -- help
```

Detalle por Windows/Linux:

```text
scripts/README.md
```

Frontend + backend:

```bash
npm run local:up
```

Build frontend:

```bash
npm run build
```

Tests backend:

```bash
cd backend
mvnw.cmd test
```

Backend solo:

```bash
cd backend
mvnw.cmd spring-boot:run
```

Health:

```bash
curl http://localhost:8787/api/portfolio/health
```

### Resumen mental del proyecto

Este portfolio no es solamente una pagina personal.

Es una demo de posicionamiento:

```text
Sebastian Gatica puede construir apps full-stack con IA aplicada,
backend propio, streaming real, GPT, arquitectura multiagente conceptual,
UX usable y criterio para venderlo por hitos.
```

El agente responde como consultor porque la demo intenta mostrar:

- Criterio tecnico.
- Criterio de producto.
- Criterio comercial.
- Capacidad de bajar una idea a arquitectura.
- Capacidad de pensar MVPs vendibles.

Si despues se quiere convertir en generador de codigo, ya hay un camino claro:

```text
agregar modos -> ajustar prompts -> ampliar request -> renderizar codigo mejor
```
