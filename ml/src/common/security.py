"""내부 공유 키 확인. ML 서버는 Spring 백엔드만 부를 수 있다 (브라우저 직접 호출 차단).

- 환경변수 MOAVA_INTERNAL_KEY 와 요청 헤더 X-Internal-Key 가 같아야 /risk/* 를 쓸 수 있다.
- 키가 비어 있으면 서버를 켜지 않는다 (실수로 열린 채 켜지는 것 방지).
- 키 값은 로그·응답에 남기지 않는다.
"""
from __future__ import annotations

import os
import secrets

from fastapi import Header, HTTPException

KEY_ENV = "MOAVA_INTERNAL_KEY"


def _load_key() -> str:
    key = os.getenv(KEY_ENV, "").strip()
    if not key:
        raise SystemExit(
            f"[MOAVA ML] 환경변수 {KEY_ENV} 가 비어 있어 서버를 시작하지 않습니다.\n"
            f"  ml/.env 에 {KEY_ENV}=<공유 키> 를 적고 `uvicorn src.main:app --port 8000 --env-file .env` 로 실행하세요.\n"
            "  값은 backend 의 application-secret.properties 에 있는 moava.ml.internal-key 와 같아야 합니다."
        )
    return key


INTERNAL_KEY = _load_key()


def require_internal_key(x_internal_key: str | None = Header(default=None, include_in_schema=False)) -> None:
    """/risk/* 라우터 공통 검사. 헤더가 없거나 다르면 401."""
    given = (x_internal_key or "").encode("utf-8")
    if not secrets.compare_digest(given, INTERNAL_KEY.encode("utf-8")):
        raise HTTPException(status_code=401, detail="내부 인증 키가 올바르지 않습니다.")
