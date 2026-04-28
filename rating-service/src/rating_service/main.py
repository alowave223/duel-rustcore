import logging

from fastapi import FastAPI

from rating_service.config import Settings
from rating_service.routes import build_router


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level.upper())

    app = FastAPI(
        title="rating-service",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    app.include_router(build_router(settings))
    return app


def main() -> None:
    import uvicorn

    settings = Settings()
    uvicorn.run(
        "rating_service.main:create_app",
        host=settings.bind_host,
        port=settings.bind_port,
        log_level=settings.log_level,
        factory=True,
    )


if __name__ == "__main__":
    main()
