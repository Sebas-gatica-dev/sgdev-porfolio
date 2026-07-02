# Operacion local y Docker

## Comandos multiplataforma

Los comandos operativos viven en `scripts/`:

- Windows: `scripts/portfolio.ps1`
- Linux/macOS: `scripts/portfolio.sh`
- Wrapper npm multiplataforma: `scripts/portfolio.mjs`

Guia completa: `scripts/README.md`.

Los ejemplos de este documento usan `npm run` porque funcionan igual en Windows y Linux.

## Variables

El backend carga variables desde `.env` cuando se ejecuta desde la raiz o desde `backend`.

Revisar estado sin mostrar secretos:

```powershell
npm run env:check
```

Variables clave:

```env
OPENAI_API_KEY=
MAILTRAP_API_TOKEN=
CONTACT_MAIL_TO_ADDRESS=
CONTACT_MAIL_FROM_ADDRESS=contacto@sgdev.com.ar
```

## Desarrollo local sin Docker

Levantar frontend y backend:

```powershell
npm run local:up
```

Frenar todo:

```powershell
npm run local:down
```

Reiniciar todo:

```powershell
npm run local:restart
```

Reiniciar solo backend:

```powershell
npm run local:restart:api
```

Reiniciar solo frontend:

```powershell
npm run local:restart:web
```

Ver puertos:

```powershell
npm run status
```

URLs:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8787/api/portfolio/health
```

Logs locales:

```powershell
npm run logs
npm run logs:api
npm run logs:web
```

## Docker Compose local

Levantar stack completo con imagen separada para frontend, backend, db y nginx:

```powershell
npm run docker:up
```

Frenar stack:

```powershell
npm run docker:down
```

Reiniciar contenedores:

```powershell
npm run docker:restart
```

Logs:

```powershell
npm run docker:logs
npm run docker:logs:api
npm run docker:logs:web
npm run docker:logs:db
```

URLs Docker:

```text
Aplicacion via nginx: http://localhost:8080
Postgres host local: localhost:5433
```

Para publicar detras de un gateway bajo `/portfolio`, compilar el frontend con:

```bash
VITE_BASE_PATH=/portfolio/ docker compose up -d --build
```

En PowerShell:

```powershell
$env:VITE_BASE_PATH = "/portfolio/"
docker compose up -d --build
```

## Base de datos

Levantar solo Postgres:

```powershell
npm run db:up
```

Reiniciar Postgres:

```powershell
npm run db:restart
```

Compose usa un volumen persistente:

```text
portfolio_postgres_data
```

No borrar el volumen salvo que se quiera perder datos locales.

## VPS

Para deploy remoto conviene:

1. Crear usuario deploy o usar root solo para bootstrap.
2. Configurar SSH key.
3. Instalar Docker y Docker Compose plugin.
4. Copiar repo y `.env` remoto.
5. Ejecutar `docker compose up -d --build`.
6. Apuntar nginx/puerto 80 al servicio `nginx`.
7. Agregar TLS con Certbot o proxy administrado.

No guardar passwords de VPS en archivos del repo.
