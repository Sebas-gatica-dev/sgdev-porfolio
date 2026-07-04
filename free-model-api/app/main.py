import json
from collections.abc import AsyncIterator

import httpx
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="FREE_MODEL_")

    ollama_url: str = "http://localhost:11434"
    model: str = "qwen3:0.6b"
    request_timeout_seconds: float = 120.0
    num_ctx: int = 2048
    max_tokens: int = 420
    temperature: float = 0.45
    top_p: float = 0.85


class ChatRequest(BaseModel):
    message: str = Field(default="")
    instructions: str | None = None
    model: str | None = None


settings = Settings()
app = FastAPI(
    title="SG Portfolio Free Model API",
    version="0.1.0",
    description="Small FastAPI bridge between the portfolio backend and a local Ollama model.",
)


@app.get("/health")
async def health() -> dict[str, object]:
    return {
        "ok": True,
        "runtime": "ollama",
        "model": settings.model,
        "ollamaUrl": settings.ollama_url,
        "numCtx": settings.num_ctx,
        "maxTokens": settings.max_tokens,
    }


@app.post("/chat/stream")
async def chat_stream(request: ChatRequest) -> StreamingResponse:
    return StreamingResponse(
        stream_ollama_chat(request),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


async def stream_ollama_chat(request: ChatRequest) -> AsyncIterator[str]:
    model = request.model or settings.model
    user_message = request.message.strip()
    if user_message and model.startswith("qwen3") and "/think" not in user_message and "/no_think" not in user_message:
        user_message = f"{user_message} /no_think"

    messages = []
    if request.instructions:
        messages.append({"role": "system", "content": request.instructions})
    messages.append({"role": "user", "content": user_message})

    payload = {
        "model": model,
        "messages": messages,
        "stream": True,
        "options": {
            "num_ctx": settings.num_ctx,
            "num_predict": settings.max_tokens,
            "temperature": settings.temperature,
            "top_p": settings.top_p,
        },
    }

    yield sse("trace", {"label": "Free model", "detail": f"Ollama local: {model}", "status": "connected"})

    try:
        timeout = httpx.Timeout(settings.request_timeout_seconds, connect=10.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream(
                "POST",
                f"{settings.ollama_url.rstrip('/')}/api/chat",
                json=payload,
            ) as response:
                if response.status_code >= 400:
                    detail = (await response.aread()).decode("utf-8", errors="replace")[:500]
                    yield sse("error", {"message": f"Ollama HTTP {response.status_code}: {detail}"})
                    return

                async for line in response.aiter_lines():
                    if not line.strip():
                        continue
                    try:
                        chunk = json.loads(line)
                    except json.JSONDecodeError:
                        continue

                    text = chunk.get("message", {}).get("content", "")
                    if text:
                        yield sse("chunk", {"text": text})

                    if chunk.get("done"):
                        break
    except httpx.ConnectError:
        yield sse("error", {"message": "No se pudo conectar con Ollama. Revisa que el servicio este activo."})
    except httpx.TimeoutException:
        yield sse("error", {"message": "Ollama tardo demasiado en responder."})


def sse(event: str, data: dict[str, object]) -> str:
    payload = {"type": event, **data}
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=True)}\n\n"
