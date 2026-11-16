# Revisión

**Reviewer** Cierre de la implementación en local y VPS
**Veredicto de implementación** APROBADO para e33698f.

Contrasta [spec.md](spec.md) con [testing.md](testing.md) y el resultado integrado en el repositorio, la VPS y los artefactos de CI. No se emite aprobación hasta que cada RF tenga evidencia reproducible y el despliegue haya sido verificado.

| Hallazgo | Requisito y ubicación | Reproducción e impacto | Corrección y comprobación |
| --- | --- | --- | --- |
| Variantes de la ruta eludían el filtro por cadena. | RF-02, filtro de proyectos | Segmentos codificados o con parámetros de matriz. | El filtro usa PathPattern. Pasaron las regresiones y el smoke HTTP. |
| Token de respaldo rechazado con el principal vacío. | RF-02, autorizador | El entorno traía el principal definido como cadena vacía. | La selección se hace en Java. Se verificaron principal, fallback, placeholder y tokens efectivos en la VPS. |
| El proxy interno perdía el esquema HTTPS. | RF-01, gateway Nginx | La aplicación recibía HTTP del proxy interno. | Se conserva HTTPS del proxy exterior. Chrome comprobó Secure en la VPS. |

## Resolución de bloqueantes de spec/plan

**Base corregida:** `spec.md`, `plan.md`, `tasks.md`, `testing.md` y este `review.md` incorporan el estado real de producción, la arquitectura definitiva de sesión, la autorización dual, la base de auth no versionada, la disponibilidad de la VPS y las pruebas de aceptación faltantes.

| ID | Resolución | Ubicación y comprobación prevista |
| --- | --- | --- |
| B-01 | **Resuelto.** RF-06 ya no exige ausencia de URL ni de imágenes. Producción conserva ArgentiCommerce publicado, destacado, `sortOrder` 10, `liveUrl:"/argenticommerce/"`, portada 1440×900 y galería; “Ver en producción” es correcto y el enlace relativo responde `200`. El seed de base vacía usa la misma URL y datos coherentes, sin imágenes. | `spec.md` RF-06/RF-07 y restricciones; `plan.md` decisiones de producción y seed; `testing.md` RF-06 verifica un único proyecto, URL relativa, imágenes y enlace `200`. |
| B-02 | **Resuelto.** La regla es única: insertar solo si `portfolio_projects` está vacía. Si hay cualquier fila —aunque falte el slug— no se inserta ni modifica. Se acepta porque producción ya contiene ArgentiCommerce y se declara explícitamente que no es una migración de datos. La prueba cubre tabla vacía, otra fila sin el slug, slug en `draft` y doble arranque. | `spec.md` RF-07 y supuestos; `plan.md` decisión “Seed no es migración”; `tasks.md` T-03; `testing.md` RF-07. |
| B-03 | **Resuelto.** La arquitectura es `WebSession` de WebFlux, no `HttpSession`/`JSESSIONID`. `AuthController` escribe, lee y borra el atributo; cookie `SESSION`, `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` bajo HTTPS. No se usa `AuthenticationWebFilter`, `SecurityContextRepository` ni form login. Se agrega `server.forward-headers-strategy=framework` para reconocer `X-Forwarded-Proto`. | `spec.md` RF-01 y restricciones; `plan.md` contratos de sesión/cookie; `testing.md` RF-01 valida `Set-Cookie`, sesión anónima/autenticada, logout y HTTPS. |
| B-04 | **Resuelto.** La cadena de Spring Security no protege `/api/admin/**`; se reduce a `permitAll`, CSRF deshabilitado, sin form login/httpBasic. Un `WebFilter` propio cubre solo `/api/admin/projects/**` y acepta sesión o token `PORTFOLIO_ADMIN_TOKEN` con fallback `PORTFOLIO_USAGE_ADMIN_TOKEN`; sin ambos responde `401` JSON, nunca `503`. `/api/admin/usage/**` queda intacto con su token. | `spec.md` RF-02 y restricciones; `plan.md` filtro propio; `tasks.md` T-02; `testing.md` RF-02/RF-08 incluyen projects con token y usage. |
| B-05 | **Resuelto.** `spring-boot-starter-security`, `SecurityConfig`, `AuthController`, `LoginPage.tsx`, `authClient.ts` y `projectForm.ts` están en el working tree pero no en `HEAD`. La unidad no agrega dependencias nuevas; T-07 commitea esa base junto con lo nuevo. Se eliminó la afirmación de “contrato existente en `main`”. | `spec.md` restricciones; `plan.md` decisión “Base de autenticación no versionada”; `tasks.md` T-07. |
| B-06 | **Resuelto.** La contraseña ya fue entregada por el usuario y no se documenta. La credencial SSH está disponible en `%LOCALAPPDATA%\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\SGDev\Secrets\personal-vps-ssh.clixml`, localizable con `Get-ChildItem -Recurse $env:LOCALAPPDATA -Filter personal-vps-ssh.clixml`. T-08 puede ejecutarse; el riesgo de que SgInfra regenere el entorno queda como residual. | `spec.md` supuestos; `plan.md` dependencias operativas y riesgos; `tasks.md` T-08; `testing.md` RF-08. |
| B-07 | **Resuelto.** `.env.example` deja `PORTFOLIO_USAGE_ADMIN_TOKEN=` vacío y sin placeholder público. El filtro rechaza un valor vacío o `change-me-admin-token`, usa el fallback de usage cuando el token admin no está configurado y, sin sesión ni token válido, responde `401`, nunca `503`; se prueba el fallback de usage. | `spec.md` RF-02/RF-03 y restricciones; `plan.md` contrato del filtro; `testing.md` RF-02/RF-03/RF-08. |
| B-08 | **Resuelto.** Se prueba el mismo status y cuerpo para usuario inexistente, contraseña incorrecta y ausencia de contraseña; se verifica el archivo físico tras el borrado de imagen, no solo el `404` de media; la autenticación se valida con `/api/auth/session` y el POST de login, no con el `200` del SPA. | `spec.md` RF-02/RF-05/RF-08; `testing.md` RF-02 (uniformidad), RF-05 (archivo físico), RF-08 (sesión vía API). |
| B-09 | **Resuelto.** El entorno local usa `VITE_BASE_PATH=/portfolio/ docker compose up -d --build` (o el equivalente PowerShell), sin confiar en el `.env` que define `/`. La subida de prueba usa `kind:"gallery"` en un proyecto descartable y se limpia; nunca se reemplaza la portada real. | `spec.md` supuestos; `plan.md` entornos; `testing.md` RF-01/RF-05 y sección “Repetición”. |

## Resolución de observaciones no bloqueantes

| ID | Resolución | Ubicación y comprobación prevista |
| --- | --- | --- |
| O-01 | **Resuelto.** Toda prueba de imagen usa un proyecto descartable y `kind:"gallery"`, con limpieza; no se toca la portada ni la galería de ArgentiCommerce. | `plan.md` entornos/riesgos; `testing.md` RF-05. |
| O-02 | **Resuelto.** El seed es atómico con `INSERT ... ON CONFLICT` o captura de duplicado dentro de una transacción; se prueba el doble arranque concurrente. | `spec.md` RF-07; `plan.md` seed; `tasks.md` T-03; `testing.md` RF-07. |
| O-03 | **Resuelto.** Se prueba `/api/admin/usage/**` con `PORTFOLIO_USAGE_ADMIN_TOKEN` si está configurado, además del token admin y su fallback. | `plan.md` filtro; `tasks.md` T-08; `testing.md` RF-08. |
| O-04 | **Resuelto.** El smoke de demos incluye las cuatro rutas, health/blueprint del backend y un POST SSE breve con timeout; no se limita a abrir páginas. | `plan.md` entornos; `testing.md` RF-06/RF-08. |
| O-05 | **Resuelto como restricción.** El admin se mantiene same-origin a través de Nginx o proxy local; no se introduce un API cross-origin ni se modifica CORS (`allowCredentials=false`). | `spec.md` restricciones; `plan.md` compatibilidad/CORS. |
| O-06 | **Resuelto.** T-09 (Reviewer) solo edita `testing.md` y `review.md`; T-10 (Coordinator) actualiza la tabla “Estado de cierre” de `spec.md`. | `tasks.md` T-09/T-10; `spec.md` nota de la tabla de cierre. |
| O-07 | **Resuelto.** Se mantiene `package-lock.json` versionado; T-07 lo incluye como criterio y no se agrega a `.gitignore`. | `tasks.md` T-07; verificación del diff. |
| O-08 | **Resuelto.** `GET /api/auth/session` devuelve siempre `200`; anónimo responde `{authenticated:false,username:null}`. | `spec.md` RF-01; `plan.md` contrato; `testing.md` RF-01/RF-08. |

## Criterios de revisión de implementación

- [x] RF-01 a RF-08 tienen fila en `testing.md` con resultado distinto de `Pendiente` y evidencia sin secretos.
- [x] `npm run build`, `npm run build:api` y `node scripts/mvnw.mjs test` (o `./mvnw test`) pasan en la revisión final.
- [x] CI de `main` en verde y SHA desplegado identificado en la VPS.
- [x] `GET /portfolio/api/auth/session` responde `200` anónimo con `{authenticated:false,username:null}`; el POST de login autentica y la cookie es `SESSION`, `HttpOnly`, `SameSite=Lax`, `Path=/` y `Secure` bajo HTTPS.
- [x] `/portfolio/admin` y `/portfolio/api/admin/projects/**` rechazan sin sesión y aceptan con sesión; el token admin y el fallback de usage funcionan si están configurados; token vacío o `change-me-admin-token` no autoriza.
- [x] `/api/admin/usage/**` conserva su token y no se ve afectado por el filtro de proyectos.
- [x] El backend arranca sin `PORTFOLIO_ADMIN_PASSWORD` ni `PORTFOLIO_ADMIN_PASSWORD_HASH`, y en ese caso el login falla con `401` uniforme.
- [x] No hay contraseña, hash real, token ni `.env` en el diff, commits, specs, logs ni evidencia.
- [x] El API público y `/portfolio/proyectos` devuelven exactamente ArgentiCommerce con `liveUrl:"/argenticommerce/"`, portada y galería; el enlace “Ver en producción” resuelve `200` y no se modificó el proyecto.
- [x] El seed solo inserta con tabla vacía, no duplica ni sobrescribe, es atómico y no se presenta como migración.
- [x] El borrado de imagen elimina el registro y el archivo físico; la persistencia se verifica con una galería descartable y sin tocar la portada real.
- [x] Las cuatro demos, el health/blueprint y un POST SSE breve no presentan regresiones.
- [x] La tabla “Estado de cierre” de `spec.md` la actualiza el Coordinator con el reporte del Reviewer, no por anticipado.

## Cierre

- Bloqueantes de especificación: B-01 a B-09 resueltos; no quedan bloqueantes de especificación.
- Observaciones O-01 a O-08: resueltas o convertidas en restricciones verificables.
- Qwen3.5 9B revisó la corrección de autorización y la evidencia de cierre. Los resultados locales y de producción están en [testing.md](testing.md).
- El hash BCrypt quedó en la configuración privada persistente de SgInfra. El backend lo recibió en el despliegue y el login HTTPS pasó.
