import json
import asyncio
import time
from collections import OrderedDict
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
    num_ctx: int = 4096
    max_tokens: int = 420
    temperature: float = 0.15
    top_p: float = 0.85


class ChatRequest(BaseModel):
    message: str = Field(default="", max_length=12000)
    instructions: str | None = None
    model: str | None = None
    sessionId: str = Field(default="", max_length=200)


settings = Settings()
history: OrderedDict[str, tuple[float, list[dict[str, str]]]] = OrderedDict()
generation_lock = asyncio.Semaphore(1)
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
    model = settings.model
    user_message = request.message.strip()
    if user_message and model.startswith("qwen3") and "/think" not in user_message and "/no_think" not in user_message:
        user_message = f"{user_message} /no_think"

    messages = []
    if request.instructions:
        messages.append({"role": "system", "content": request.instructions})
    now = time.monotonic()
    for key in list(history):
        if now - history[key][0] > 1800:
            del history[key]
    if request.sessionId and request.sessionId in history:
        messages.extend(history[request.sessionId][1])
    messages.append({"role": "user", "content": user_message})

    payload = {
        "model": model,
        "messages": messages,
        "stream": True,
        "think": False,
        "keep_alive": "2m",
        "options": {
            "num_ctx": settings.num_ctx,
            "num_predict": settings.max_tokens,
            "temperature": settings.temperature,
            "top_p": settings.top_p,
        },
    }

    yield sse("trace", {"label": "Free model", "detail": f"Ollama local: {model}", "status": "connected"})

    try:
        try:
            await asyncio.wait_for(generation_lock.acquire(), timeout=30)
        except TimeoutError:
            yield sse("error", {"message": "El modelo local esta ocupado. Intenta nuevamente en unos segundos."})
            return
        timeout = httpx.Timeout(settings.request_timeout_seconds, connect=10.0)
        answer = ""
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
                        answer += text
                        yield sse("chunk", {"text": text})

                    if chunk.get("done"):
                        if request.sessionId and answer.strip():
                            conversation = [m for m in messages if m["role"] != "system"]
                            conversation.append({"role": "assistant", "content": answer})
                            history[request.sessionId] = (time.monotonic(), [{**m, "content": m["content"][:1200]} for m in conversation[-6:]])
                            history.move_to_end(request.sessionId)
                            while len(history) > 200:
                                history.popitem(last=False)
                        break
    except httpx.ConnectError:
        yield sse("error", {"message": "No se pudo conectar con Ollama. Revisa que el servicio este activo."})
    except httpx.TimeoutException:
        yield sse("error", {"message": "Ollama tardo demasiado en responder."})
    finally:
        if 'answer' in locals():
            generation_lock.release()


def sse(event: str, data: dict[str, object]) -> str:
    payload = {"type": event, **data}
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=True)}\n\n"
