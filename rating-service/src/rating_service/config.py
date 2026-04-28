from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


LogLevel = Literal["critical", "error", "warning", "info", "debug", "trace"]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="RATING_", env_file=".env", extra="ignore")

    shared_secret: str = Field(min_length=16)
    bind_host: str = "127.0.0.1"
    bind_port: int = Field(default=8088, ge=1, le=65535)
    log_level: LogLevel = "info"
    hmac_max_skew_seconds: int = Field(default=60, ge=1, le=300)
