# SG Portfolio Free Model API

FastAPI bridge for the portfolio's free local model mode.

It keeps the Spring backend independent from Ollama details and streams small
SSE chunks back to the portfolio UI.

## Default model

`qwen3:0.6b` is the default because it is small enough for a 4 GB RAM VPS and
still useful for Spanish portfolio dialogue.

Before using it with Docker, pull the model once:

```bash
docker compose exec ollama ollama pull qwen3:0.6b
```

## Local run

```bash
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8795
```

Required env only if Ollama is not on `localhost:11434`:

```bash
FREE_MODEL_OLLAMA_URL=http://localhost:11434
FREE_MODEL_MODEL=qwen3:0.6b
```
