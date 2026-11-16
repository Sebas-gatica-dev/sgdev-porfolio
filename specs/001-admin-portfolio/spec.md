# Especificación

**Unidad** `001-admin-portfolio`

**Pedido** Un administrador mínimo del portfolio, de uso privado, que permita iniciar sesión con un único usuario, gestionar proyectos con imágenes y textos, y mantener ArgentiCommerce como único proyecto visible en la sección pública, con los cambios desplegados y operativos en la VPS.

**Alcance autorizado** Frontend React, backend Spring Boot, configuración de entorno, pruebas, commit y push a `main`, despliegue vía SgInfra y verificación HTTPS en `https://sgdev.com.ar/portfolio/`. Queda fuera de alcance modificar demos existentes, prompts, contratos públicos de chat/turnos/documentos, agregar dependencias, borrar volúmenes de producción, vaciar datos existentes o alterar la URL de producción existente de ArgentiCommerce.

| ID | Condición | Resultado observable |
| --- | --- | --- |
| RF-01 | Dado el único administrador con `PORTFOLIO_ADMIN_PASSWORD` o `PORTFOLIO_ADMIN_PASSWORD_HASH` configurada en el entorno, cuando ingresa por `https://sgdev.com.ar/portfolio/admin/login`, envía usuario `sgdev-admin` y su contraseña | Recibe `200 {authenticated:true,username:"sgdev-admin"}` y una cookie de sesión `SESSION` (`HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` cuando la petición es HTTPS); accede a `/portfolio/admin`; `GET /portfolio/api/auth/session` devuelve `200` con el estado autenticado y, sin sesión, `200 {authenticated:false,username:null}`; `POST /portfolio/api/auth/logout` invalida la sesión. `/portfolio/login` ofrece el mismo ingreso como alias. |
| RF-02 | Dado un visitante sin sesión y sin un token válido —incluido un token admin vacío, no configurado o igual a un placeholder conocido—, cuando abre `/portfolio/admin`, envía un login incorrecto o invoca `/portfolio/api/admin/projects/**` | La UI redirige a `/portfolio/admin/login` sin mostrar datos de administración; el login responde `401 {authenticated:false,message}` con el mismo estado y mensaje para usuario inexistente, contraseña incorrecta o ausencia de contraseña configurada; el API admin de proyectos responde `401` JSON —nunca `503`— y no lista, crea, edita ni elimina; no se revela si el usuario existe ni si hay contraseña o token configurado. `/api/admin/usage/**` conserva su contrato y su token. |
| RF-03 | Dado un backend sin `PORTFOLIO_ADMIN_PASSWORD` ni `PORTFOLIO_ADMIN_PASSWORD_HASH`, cuando arranca y se intenta iniciar sesión | El backend arranca y atiende peticiones; todo login responde `401`; `PORTFOLIO_ADMIN_USERNAME` toma `sgdev-admin` por defecto; la contraseña o el hash reales no aparecen en el repositorio, logs, documentación, `.env.example` ni evidencia; `.env.example` no contiene un token admin público utilizable y deja `PORTFOLIO_USAGE_ADMIN_TOKEN=` vacío. |
| RF-04 | Dado el administrador autenticado, cuando lista, crea, edita o elimina proyectos desde `/portfolio/admin` | Ve todos los estados (`draft`, `published`, `archived`); se persisten título, slug, resumen, descripción markdown, URL de producción opcional, URL de repositorio opcional, stack, estado, destacado y orden; los cambios se reflejan en el API admin y en el sitio público según el estado; los errores de validación se muestran en la UI sin perder lo ingresado; eliminar exige confirmación y retira el proyecto del listado. |
| RF-05 | Dado el administrador autenticado en el editor de un proyecto descartable, cuando sube, reemplaza o elimina una imagen de portada o de galería en formato JPEG, PNG o WebP de hasta 5 MB | La imagen se guarda y se sirve por `/portfolio/api/project-media/{clave}`; reemplazar la portada deja una sola imagen de portada; eliminar quita el registro y el archivo físico del volumen; un archivo mayor a 5 MB o de tipo no permitido devuelve un error visible en la UI; las imágenes siguen disponibles tras reiniciar el backend. La portada y la galería reales de ArgentiCommerce no se reemplazan ni se borran. |
| RF-06 | Dado un visitante público, cuando abre `/portfolio/proyectos` o el detalle de ArgentiCommerce | Ve exactamente un proyecto publicado, ArgentiCommerce, con su resumen, descripción, stack, repositorio, portada y galería existentes en producción; conserva `liveUrl:"/argenticommerce/"`, muestra “Ver en producción” y el enlace relativo resuelve `200`; no se vacía ni se sobrescribe la URL ni se eliminan sus medios; las demos y los contratos de chat, turnos y documentos siguen funcionando sin cambios. |
| RF-07 | Dado un entorno cuya tabla `portfolio_projects` está vacía, cuando arranca el backend | Se inserta una sola vez ArgentiCommerce `published`, destacado, slug `argenticommerce`, `sortOrder` 10, `liveUrl:"/argenticommerce/"`, repositorio GitHub y stack Next.js/TypeScript/Spring Boot/PostgreSQL/Flyway/Docker/Nginx, sin imágenes porque no hay un asset versionado; si la tabla tiene cualquier fila —aunque no exista el slug— no inserta ni modifica nada; la inserción es atómica ante arranques concurrentes; reinicios y redeploys mantienen el resultado; no es una migración de datos ni altera la producción existente. |
| RF-08 | Dado el cambio integrado en la rama `main`, cuando CI y SgInfra completan el despliegue en la VPS | La verificación de sesión se hace contra `/portfolio/api/auth/session` y el POST de login, no contra el `200` del SPA; la sesión y el panel funcionan bajo `/portfolio`; `/portfolio/proyectos` y el API público devuelven exactamente ArgentiCommerce con su URL relativa e imágenes; una imagen de galería de prueba persiste tras reiniciar el contenedor y luego se limpia; el token admin y el token de usage siguen autorizando sus rutas cuando están configurados; las demos responden y un smoke SSE breve no falla; la contraseña solo existe en el entorno del VPS; no se borran volúmenes de producción. |

## Restricciones y decisiones del usuario

- Un único usuario administrador, `sgdev-admin`, sin registro público, recuperación de contraseña ni multiusuario.
- La contraseña ya fue entregada por el usuario y no se escribe en el repositorio, specs, testing, logs ni evidencia. Solo puede vivir en el `.env` local ignorado y en el entorno de la VPS.
- La autenticación es una sesión `WebSession` de WebFlux, no `HttpSession` ni `JSESSIONID`. El `AuthController` escribe, lee y borra el atributo de sesión; no se usa `AuthenticationWebFilter`, `SecurityContextRepository` ni form login.
- La cookie de sesión se llama `SESSION`, es `HttpOnly`, `SameSite=Lax` y `Path=/`; se marca `Secure` cuando el scheme es HTTPS. `server.forward-headers-strategy=framework` permite reconocer `X-Forwarded-Proto: https`.
- `SecurityConfig` se reduce a `permitAll`, CSRF deshabilitado y sin form login ni httpBasic, para que Spring Security no intercepte las rutas propias.
- Un `WebFilter` propio protege únicamente `/api/admin/projects/**` y acepta sesión válida o token `PORTFOLIO_ADMIN_TOKEN` con fallback `PORTFOLIO_USAGE_ADMIN_TOKEN`, por header o `Bearer`. La cadena de Spring Security no protege `/api/admin/**`.
- `/api/admin/usage/**` queda intacto y sigue usando su token de usage. Sin sesión ni token, el API de proyectos responde `401` JSON, nunca `503`.
- Un token vacío o igual a `change-me-admin-token` nunca autoriza por sí mismo. Si `PORTFOLIO_ADMIN_TOKEN` no está configurado, se evalúa el fallback `PORTFOLIO_USAGE_ADMIN_TOKEN`.
- Se preserva el prefijo público `/portfolio` y el contrato actual de los endpoints públicos de proyectos y medios.
- Producción ya tiene ArgentiCommerce publicado, destacado, `sortOrder` 10, `liveUrl:"/argenticommerce/"`, repositorio GitHub, stack, portada 1440×900 y galería. No se debe vaciar, sobrescribir ni considerar un error mostrar “Ver en producción”; el enlace relativo responde `200`.
- El seed solo aplica a una tabla vacía. Si existe cualquier fila, no inserta ni modifica aunque falte el slug; se acepta porque producción ya contiene ArgentiCommerce. No es una migración de datos.
- La base de autenticación (`spring-boot-starter-security`, `SecurityConfig`, `AuthController`, `LoginPage.tsx`, `authClient.ts`, `projectForm.ts`) ya está en el working tree pero no en `HEAD`; la unidad no agrega dependencias nuevas y esa base se integra en el commit de T-07.
- El panel admin debe permitir listar, crear, editar, eliminar y subir imágenes; no se amplía el alcance.
- Las llamadas admin son same-origin a través de Nginx o proxy local; no se introduce un API cross-origin ni se cambia CORS.
- No se modifican demos, prompts ni los contratos de chat, turnos médicos o resumen de documentos.
- El despliegue usa el repositorio `https://github.com/Sebas-gatica-dev/sgdev-porfolio.git`, rama `main`, CI existente y el dispatch `deploy-latest` de SgInfra.

## Suposiciones y cuestiones abiertas

- La sesión se mantiene en memoria del backend; un reinicio o redeploy cierra la sesión y exige volver a ingresar. Es aceptable para un único administrador y no requiere persistencia de sesión.
- La VPS permite fijar `PORTFOLIO_ADMIN_PASSWORD` en el entorno del backend sin versionarla. Si SgInfra regenera ese entorno en cada despliegue, la variable debe reponerse y verificarse después del deploy; es un riesgo residual documentado. Sin contraseña configurada el login siempre falla, aunque el backend arranca.
- La contraseña ya está disponible para la verificación y la credencial SSH existe en el equipo; T-08 puede ejecutarse. Ningún valor se registra en los documentos.
- Producción ya contiene exactamente ArgentiCommerce con sus imágenes y URL relativa; el seed no la modifica. La comprobación de “exactamente un proyecto” se hace sobre ese estado, sin borrar ni archivar datos.
- En una base vacía el seed no crea imágenes porque no hay un asset versionado; la portada y galería reales de producción se conservan y no se regeneran.
- El token admin puede o no estar configurado en la VPS; su ausencia no impide el login por sesión ni el arranque. El token de usage se prueba solo si está configurado.
- El `.env` local define `VITE_BASE_PATH=/`; para reproducir el subpath se debe exportar `VITE_BASE_PATH=/portfolio/` al construir Compose, sin depender del `.env`.
- No hay preguntas bloqueantes. Las decisiones de contrato y el estado real de producción están fijados; los bloqueantes B-01 a B-09 y las observaciones O-01 a O-08 del Reviewer quedan resueltos en `review.md`.

## Estado de cierre

La tabla la actualiza el Coordinator al cierre, con el reporte del Reviewer; el Reviewer no edita este archivo.

| ID | Estado | Evidencia |
| --- | --- | --- |
| RF-01 | Verificada | Pruebas locales y de producción. [Evidencia de pruebas](testing.md). |
| RF-02 | Verificada | Pruebas de la unidad y smoke de integración. [Evidencia de pruebas](testing.md). |
| RF-03 | Verificada | Pruebas de la unidad y smoke de integración. [Evidencia de pruebas](testing.md). |
| RF-04 | Verificada | Pruebas de la unidad y smoke de integración. [Evidencia de pruebas](testing.md). |
| RF-05 | Verificada | Pruebas locales y de producción. [Evidencia de pruebas](testing.md). |
| RF-06 | Verificada | Pruebas locales y de producción. [Evidencia de pruebas](testing.md). |
| RF-07 | Verificada | Pruebas de la unidad y smoke de integración. [Evidencia de pruebas](testing.md). |
| RF-08 | Verificada | Pruebas locales y de producción. [Evidencia de pruebas](testing.md). |
