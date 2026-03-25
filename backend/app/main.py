import logging
import os
import time
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.trustedhost import TrustedHostMiddleware

from app.routers import auth, chat, community, community_activation, friend_qr, messages, notifications, security, services

app = FastAPI(title="BarkWise API", version="0.1.0")
logger = logging.getLogger(__name__)


def _parse_csv_env(name: str, default: str) -> list[str]:
    raw = os.getenv(name, default)
    return [item.strip() for item in raw.split(",") if item.strip()]


cors_origins = _parse_csv_env("CORS_ORIGINS", "*")
allow_any_origin = len(cors_origins) == 1 and cors_origins[0] == "*"

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    # Browsers reject wildcard CORS with credentials enabled.
    allow_credentials=not allow_any_origin,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(GZipMiddleware, minimum_size=1024)

trusted_hosts = _parse_csv_env("TRUSTED_HOSTS", "*")
if not (len(trusted_hosts) == 1 and trusted_hosts[0] == "*"):
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=trusted_hosts)


@app.middleware("http")
async def log_request_metrics(request, call_next):
    started_at = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception:
        duration_ms = (time.perf_counter() - started_at) * 1000.0
        logger.exception(
            "request method=%s path=%s failed duration_ms=%.1f",
            request.method,
            request.url.path,
            duration_ms,
        )
        raise
    duration_ms = (time.perf_counter() - started_at) * 1000.0
    response_size = response.headers.get("content-length", "unknown")
    response.headers["X-Process-Time-Ms"] = f"{duration_ms:.1f}"
    logger.info(
        "request method=%s path=%s status=%s duration_ms=%.1f response_bytes=%s",
        request.method,
        request.url.path,
        response.status_code,
        duration_ms,
        response_size,
    )
    return response

app.include_router(services.router, prefix="/services")
app.include_router(services.router, prefix="/listings")
app.include_router(chat.router)
app.include_router(community.router)
app.include_router(community_activation.router)
app.include_router(auth.router)
app.include_router(friend_qr.router)
app.include_router(messages.router)
app.include_router(notifications.router)
app.include_router(security.router)

web_dir = Path(__file__).parent / "web"
if web_dir.exists():
    app.mount("/web", StaticFiles(directory=web_dir, html=True), name="web")
install_dir = web_dir / "install"
if install_dir.exists():
    app.mount("/install", StaticFiles(directory=install_dir, html=True), name="install")


@app.get("/web")
def web_root_redirect():
    return RedirectResponse(url="/web/")


@app.get("/install")
def install_root_redirect():
    return RedirectResponse(url="/install/")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ready")
def ready():
    llm_configured = bool(chat.orchestrator.llm_available)
    return {
        "status": "ready",
        "llm_configured": llm_configured,
        "llm_mode": "openai" if llm_configured else "fallback",
    }
