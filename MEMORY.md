# MEMORY.md

## Estado actual del proyecto

Proyecto activo: SG AI Agent Portfolio.

Es un portfolio profesional orientado a mostrar capacidades full stack con IA aplicada, con demos interactivas de chat, voz, documentos y turnos médicos. La app combina un frontend React/Vite con un backend Java Spring Boot y prompts modulares para orquestar comportamientos del asistente.

## Decisiones clave

- La identidad profesional del asistente está definida principalmente en `backend/src/main/resources/prompts/core/system.md` y en los prompts del agente.
- La funcionalidad principal del chat se centra en `src/components/AgentConsole.tsx` y en el backend `agent/`.
- La demo de turnos usa endpoints y servicios dedicados, no la lógica del chat principal.
- La documentación de arquitectura y operación local está en `docs/architecture.md` y `docs/operacion-local-y-docker.md`.
- La configuración del entorno local y la infraestructura se administra con scripts en `scripts/` y `docker-compose.yml`.

## Convenciones operativas

- Mantener el idioma español en contenido visible al usuario y documentación del proyecto.
- Priorizar cambios pequeños, verificables y alineados con la base existente.
- Tratar los prompts como parte del comportamiento funcional del sistema y revisar su impacto antes de cerrar cambios.
- Cuando se cambian endpoints, rutas o despliegue, validar compatibilidad con el contrato actual.

## Archivos clave

- `README.md`: resumen general del proyecto y contexto de despliegue.
- `AGENTS.md`: guía de trabajo y convenciones del repositorio.
- `docs/architecture.md`: mapa de arquitectura y decisiones principales.
- `docs/operacion-local-y-docker.md`: comandos y despliegue local.
- `src/components/AgentConsole.tsx`: demo principal.
- `backend/src/main/java/dev/sg/portfolio/`: lógica de backend.
- `backend/src/main/resources/prompts/`: prompts modulares.

## Estado de trabajo

- Repositorio principal activo: `sgdev-porfolio`.
- Flujos de IA y prompts deben revisarse con cuidado porque alteran la experiencia del usuario y la identidad del proyecto.
- Siempre que haya cambios funcionales relevantes, conviene documentarlos y, si corresponde, materializarlos en una spec de SDD.

## Evaluación de la unidad SDD del panel

**7/10.** La especificación y la revisión temprana fijaron criterios claros para conservar los datos reales, proteger la sesión y verificar el despliegue. El flujo perdió tiempo cuando los implementadores heredaron un proveedor remoto y la sesión extensa tuvo que compactarse en CPU. Cada rol debe fijar el proveedor antes de delegar y retomar con un encargo breve y verificable cuando se interrumpe un turno.
