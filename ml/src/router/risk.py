"""/risk 엔드포인트 — 연체 확률, 위험 배지, 추천 납입액, 곗돈 수령 순서 추천."""
from __future__ import annotations

from fastapi import APIRouter, Request

from ..common.config import INCOME_CAP_RATIO, RISK_LEVELS, ROUND_UNIT
from ..schemas import risk as s
from ..service.risk_service import RiskService, UnknownPersona, recommend_amount

router = APIRouter(prefix="/risk", tags=["risk"])


def svc(request: Request) -> RiskService:
    return request.app.state.svc


@router.get("/model", summary="모델 정보·입력 지표 목록·위험 구간 설정")
def model_info(request: Request):
    sv = svc(request)
    return {
        **sv.meta,
        "n_features": len(sv.features), "features": sv.features,
        "risk_levels": [
            {"code": lv.code, "label": lv.label, "min_probability": lv.min_probability, "safety_factor": lv.safety_factor}
            for lv in RISK_LEVELS
        ],
        "round_unit": ROUND_UNIT, "income_cap_ratio": INCOME_CAP_RATIO,
    }


@router.post("/score", response_model=s.RiskResult, summary="연체 확률·위험 배지")
def score(body: s.Subject, request: Request):
    return svc(request).assess(body)


@router.post("/score/batch", response_model=s.PredictBatchResponse, summary="여러 가상 인물의 연체 확률")
def score_batch(body: s.PredictBatchRequest, request: Request):
    sv, results, not_found = svc(request), [], []
    for pid in dict.fromkeys(body.persona_ids):  # 중복 제거, 순서 유지
        try:
            results.append(s.PredictBatchItem(persona_id=pid, **sv.by_persona(pid).model_dump()))
        except UnknownPersona:
            not_found.append(pid)
    return s.PredictBatchResponse(results=results, not_found=not_found)


@router.post("/contribution", response_model=s.ContributionResponse, summary="추천 납입액")
def contribution(body: s.ContributionRequest, request: Request):
    risk = svc(request).assess(body)
    available, amount, capped = recommend_amount(
        body.monthly_income, body.monthly_expense, body.monthly_saving, body.emergency_fund, risk.safety_factor)
    return s.ContributionResponse(
        available_amount=available, recommended_amount=amount, capped=capped,
        round_unit=ROUND_UNIT, income_cap_ratio=INCOME_CAP_RATIO, risk=risk)


@router.post("/payout-order", response_model=s.PayoutOrderResponse, summary="곗돈 수령 순서 추천")
def payout_order(body: s.PayoutOrderRequest, request: Request):
    sv = svc(request)
    scored = [(m.member_id, sv.assess(m)) for m in body.members]
    # 연체 확률이 높은 회원을 앞 회차에. 동률이면 요청 순서 유지(sort 는 안정 정렬).
    scored.sort(key=lambda t: -t[1].default_probability)
    return s.PayoutOrderResponse(
        order=[s.PayoutOrderItem(order=i, member_id=mid, risk=r) for i, (mid, r) in enumerate(scored, 1)],
        rule="연체 확률이 높은 회원을 앞 회차에 배치. AI는 제안만 하며 확정은 모임장 + 전원 동의.",
    )
