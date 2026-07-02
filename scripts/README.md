# Comandos de operacion

Este directorio contiene los comandos para operar el portfolio desde terminal en Windows y Linux.

## Archivos

- `portfolio.ps1`: comandos para Windows PowerShell.
- `portfolio.sh`: comandos para Linux/macOS con Bash.
- `portfolio.mjs`: wrapper usado por `npm run`; elige automaticamente PowerShell o Bash segun el sistema operativo.

## Uso recomendado

Los aliases de `npm` funcionan igual en Windows y Linux:

```bash
npm run status
npm run local:up
npm run logs
npm run local:restart
npm run local:down
```

Tambien se puede llamar cada script directamente.

Windows PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio.ps1 up
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/portfolio.ps1 logs-api
```

Linux/macOS:

```bash
bash scripts/portfolio.sh up
bash scripts/portfolio.sh logs-api
```

## Acciones locales

| Accion | Alias npm | Descripcion |
| --- | --- | --- |
| `status` | `npm run status` | Muestra puertos relevantes y procesos que escuchan. |
| `env` | `npm run env:check` | Lista variables de `.env` sin mostrar secretos. |
| `up` | `npm run local:up` | Levanta backend Spring Boot y frontend Vite. |
| `down` | `npm run local:down` | Detiene backend y frontend locales. |
| `restart` | `npm run local:restart` | Reinicia backend y frontend. |
| `restart-api` | `npm run local:restart:api` | Reinicia solo backend. |
| `restart-web` | `npm run local:restart:web` | Reinicia solo frontend. |
| `logs` | `npm run logs` | Muestra las ultimas lineas de logs locales. |
| `logs-api` | `npm run logs:api` | Sigue logs del backend local. |
| `logs-web` | `npm run logs:web` | Sigue logs del frontend local. |

URLs locales:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8787/api/portfolio/health
```

Los logs locales se escriben en:

```text
.logs/api.out.log
.logs/api.err.log
.logs/web.out.log
.logs/web.err.log
```

Los PIDs se guardan en:

```text
.run/api.pid
.run/web.pid
```

## Acciones Docker

| Accion | Alias npm | Descripcion |
| --- | --- | --- |
| `db-up` | `npm run db:up` | Levanta solo Postgres con Docker Compose. |
| `db-restart` | `npm run db:restart` | Reinicia solo Postgres. |
| `docker-up` | `npm run docker:up` | Construye y levanta frontend, backend, db y nginx. |
| `docker-down` | `npm run docker:down` | Detiene el stack Docker. |
| `docker-restart` | `npm run docker:restart` | Reinicia los contenedores. |
| `docker-logs` | `npm run docker:logs` | Sigue logs de todo el stack Docker. |
| `docker-logs-api` | `npm run docker:logs:api` | Sigue logs del backend Docker. |
| `docker-logs-web` | `npm run docker:logs:web` | Sigue logs del frontend Docker. |
| `docker-logs-db` | `npm run docker:logs:db` | Sigue logs de Postgres. |

URL Docker:

```text
Aplicacion via nginx: http://localhost:8080
Postgres host local: localhost:5433
```

## Ejemplos rapidos

Levantar todo local:

```bash
npm run local:up
```

Reiniciar solo el backend despues de cambiar prompts o variables:

```bash
npm run local:restart:api
```

Ver logs del backend:

```bash
npm run logs:api
```

Levantar todo con Docker:

```bash
npm run docker:up
```

Ver ayuda del script:

```bash
npm run ops -- help
```
