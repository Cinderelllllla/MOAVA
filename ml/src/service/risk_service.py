"""모델·점수 테이블 로딩과 예측/추천 로직."""
from __future__ import annotations

import csv
import json
from decimal import Decimal
from pathlib import Path

import joblib
import pandas as pd

from ..common.config import INCOME_CAP_RATIO, ROUND_UNIT, risk_level_of
from ..schemas.risk import RiskResult, Subject


class UnknownPersona(Exception):
    def __init__(self, persona_id: str):
        self.persona_id = persona_id


class NoKnownFeature(Exception):
    pass


class RiskService:
    def __init__(self, model_dir: Path):
        model_path = model_dir / "model.joblib"
        scores_path = model_dir / "persona_scores.csv"
        for p in (model_path, scores_path):
            if not p.exists():
                raise RuntimeError(f"{p} 가 없습니다. 먼저 ml 폴더에서 `python -m src.train` 을 실행하세요.")
        bundle = joblib.load(model_path)
        self.model = bundle["model"]
        self.features: list[str] = bundle["features"]
        self.meta = {k: v for k, v in bundle.items() if k not in ("model", "features")}
        self._feature_set = set(self.features)
        with scores_path.open(encoding="utf-8", newline="") as f:
            self.scores: dict[str, float] = {r["persona_id"]: float(r["default_probability"]) for r in csv.DictReader(f)}
                    # 시연용 계정 연결표 {회원 번호: 가상 인물 ID}. 여기 없는 회원은 persona_of() 의 계산식을 따른다.
        self.demo: dict[int, str] = {}
        demo_path = model_dir / "demo_members.json"
        if demo_path.exists():
            self.demo = {int(k): v for k, v in json.loads(demo_path.read_text(encoding="utf-8")).items()}
            unknown = [v for v in self.demo.values() if v not in self.scores]
            if unknown:
                raise RuntimeError(f"{demo_path} 에 없는 가상 인물 ID 가 있습니다: {unknown}")

    @staticmethod
    def _result(probability: float, source: str, features_used: int | None = None) -> RiskResult:
        lv = risk_level_of(probability)
        return RiskResult(
            default_probability=round(probability, 6), risk_level=lv.code, risk_label=lv.label,
            safety_factor=lv.safety_factor, source=source, features_used=features_used,
        )

    def by_persona(self, persona_id: str) -> RiskResult:
        key = persona_id.strip()
        if key not in self.scores:
            raise UnknownPersona(persona_id)
        return self._result(self.scores[key], "persona")
    
    def persona_of(self, member_id: int) -> str:
        """회원 번호 -> 가상 인물 ID. 시연용 연결표가 우선이고, 없으면 회원 번호로 계산한다."""
        if member_id in self.demo:
            return self.demo[member_id]
        return f"SYN{(member_id - 1) % len(self.scores) + 1:09d}"

    def by_member(self, member_id: int) -> RiskResult:
        return self.by_persona(self.persona_of(member_id))

    def by_features(self, features: dict[str, float]) -> RiskResult:
        known = {k: v for k, v in features.items() if k in self._feature_set}
        if not known:
            raise NoKnownFeature()
        row = pd.DataFrame([known], columns=self.features, dtype="float64")  # 빠진 지표는 NaN
        p = float(self.model.predict_proba(row)[0, 1])
        return self._result(p, "features", len(known))

    def assess(self, subject: Subject) -> RiskResult:
        if subject.member_id is not None:
            return self.by_member(subject.member_id)
        if subject.persona_id is not None:
            return self.by_persona(subject.persona_id)
        return self.by_features(subject.features)


def recommend_amount(income: int, expense: int, saving: int, emergency: int, safety_factor: float) -> tuple[int, int, bool]:
    """(가용액, 추천 납입액, 상한 적용 여부).

    추천액 = min(가용액 x 안전계수, 월소득 x INCOME_CAP_RATIO) 를 ROUND_UNIT 단위로 내림. 0 미만이면 0.
    프론트 finance.js 의 affordability() 와 같은 식이다.
    """
    available = income - expense - saving - emergency
    if available <= 0:
        return available, 0, False
    # 부동소수 오차로 경계값이 한 단위 내려가지 않도록 Decimal 로 계산한다.
    by_risk = Decimal(available) * Decimal(str(safety_factor))
    cap = Decimal(income) * Decimal(str(INCOME_CAP_RATIO))
    capped = by_risk > cap
    raw = min(by_risk, cap)
    return available, int(raw // ROUND_UNIT) * ROUND_UNIT, capped
