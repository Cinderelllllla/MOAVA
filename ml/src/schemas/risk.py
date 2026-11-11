from __future__ import annotations

import math
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator


class Subject(BaseModel):
    """평가 대상. persona_id(데이터셋 가상 인물 ID) 또는 features(신용 지표) 중 정확히 하나."""
    model_config = ConfigDict(extra="forbid")

    persona_id: Optional[str] = Field(None, description="데이터셋 가상 인물 ID. 예: SYN000000001", examples=["SYN000000001"])
    features: Optional[dict[str, float]] = Field(
        None, description="신용 지표 {컬럼명: 값}. 빠진 지표는 결측으로 처리된다. 컬럼명은 GET /risk/model 참고.",
    )

    @model_validator(mode="after")
    def _exactly_one(self):
        if (self.persona_id is None) == (self.features is None):
            raise ValueError("persona_id 와 features 중 정확히 하나만 보내야 합니다.")
        if self.features is not None:
            if not self.features:
                raise ValueError("features 가 비어 있습니다.")
            bad = [k for k, v in self.features.items() if not math.isfinite(v)]
            if bad:
                raise ValueError(f"features 값은 유한한 숫자여야 합니다: {bad[:5]}")
        return self


class RiskResult(BaseModel):
    default_probability: float = Field(description="향후 12개월 내 연체 1회 이상 확률 (0~1)")
    risk_level: str = Field(description="SAFE / CAUTION / DANGER")
    risk_label: str = Field(description="안정 / 주의 / 위험")
    safety_factor: float = Field(description="추천 납입액 계산에 쓰는 안전계수")
    source: str = Field(description="persona(사전 계산 점수) 또는 features(실시간 예측)")
    features_used: Optional[int] = Field(None, description="features 로 예측했을 때 모델이 인식한 지표 수")


class PredictBatchRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    persona_ids: list[str] = Field(min_length=1, max_length=1000)


class PredictBatchItem(RiskResult):
    persona_id: str


class PredictBatchResponse(BaseModel):
    results: list[PredictBatchItem]
    not_found: list[str] = Field(description="점수 테이블에 없는 ID")


class ContributionRequest(Subject):
    monthly_income: int = Field(ge=0, description="월소득(원)")
    monthly_expense: int = Field(ge=0, description="월 지출(원). 고정지출 + 변동지출 합계")
    monthly_saving: int = Field(0, ge=0, description="월 저축(원)")
    emergency_fund: int = Field(0, ge=0, description="월 비상금(원)")


class ContributionResponse(BaseModel):
    available_amount: int = Field(description="가용액 = 월소득 - 지출 - 저축 - 비상금 (원, 음수 가능)")
    recommended_amount: int = Field(description="추천 납입액 = min(가용액 x 안전계수, 월소득 x 상한비율), 절사 단위로 내림 (원, 최소 0)")
    capped: bool = Field(description="월소득 상한(기본 12%)에 걸려 추천액이 줄었는지")
    round_unit: int
    income_cap_ratio: float
    risk: RiskResult


class Member(Subject):
    member_id: str = Field(min_length=1, description="서비스 회원 식별자(응답에 그대로 돌려줌)")


class PayoutOrderRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    members: list[Member] = Field(min_length=2, max_length=100)

    @model_validator(mode="after")
    def _unique(self):
        ids = [m.member_id for m in self.members]
        if len(ids) != len(set(ids)):
            raise ValueError("member_id 가 중복됩니다.")
        return self


class PayoutOrderItem(BaseModel):
    order: int = Field(description="추천 수령 회차 (1부터)")
    member_id: str
    risk: RiskResult


class PayoutOrderResponse(BaseModel):
    order: list[PayoutOrderItem]
    rule: str
