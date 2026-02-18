import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.trustedhost import TrustedHostMiddleware

from app.routers import auth, chat, community, notifications, services

app = FastAPI(title="BarkWise API", version="0.1.0")


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

trusted_hosts = _parse_csv_env("TRUSTED_HOSTS", "*")
if not (len(trusted_hosts) == 1 and trusted_hosts[0] == "*"):
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=trusted_hosts)

app.include_router(services.router)
app.include_router(chat.router)
app.include_router(community.router)
app.include_router(auth.router)
app.include_router(notifications.router)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ready")
def ready():
    return {"status": "ready"}
