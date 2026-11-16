"""MOAVA AI 서버 (FastAPI).

실행 (ml 폴더에서): uvicorn src.main:app --port 8000
문서: http://localhost:8000/docs
"""
from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from .common.config import MODEL_DIR
from .router import risk
from .service.risk_service import NoKnownFeature, RiskService, UnknownPersona


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.svc = RiskService(MODEL_DIR)  # 서버가 켜질 때 모델을 한 번만 불러온다
    yield


app = FastAPI(title="MOAVA AI", version="1.0.0", lifespan=lifespan,
              description="KH 제공 합성 신용 데이터로 학습한 연체 확률 모델. 실제 신용평가가 아닌 교육용 결과입니다.")
app.include_router(risk.router)


@app.exception_handler(UnknownPersona)
async def _unknown_persona(_: Request, exc: UnknownPersona):
    return JSONResponse(status_code=404, content={"detail": f"persona_id '{exc.persona_id}' 를 찾을 수 없습니다."})


@app.exception_handler(NoKnownFeature)
async def _no_feature(_: Request, __: NoKnownFeature):
    return JSONResponse(status_code=422, content={"detail": "features 에 모델이 아는 지표가 하나도 없습니다. GET /risk/model 의 features 를 확인하세요."})


@app.get("/health", tags=["health"])
def health(request: Request):
    return {"status": "ok", "personas": len(request.app.state.svc.scores)}
