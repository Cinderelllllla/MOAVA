"""위험 구간·안전계수 설정값. 기획서(08 주요기능 · AI) 기준이며 환경변수로 조정할 수 있다."""
from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _f(name: str, default: float) -> float:
    return float(os.getenv(name, default))


@dataclass(frozen=True)
class RiskLevel:
    code: str            # SAFE / CAUTION / DANGER
    label: str           # 안정 / 주의 / 위험
    min_probability: float  # 이 값 이상이면 해당 구간 (하한 포함)
    safety_factor: float


# 안정: 10% 미만 / 주의: 10% 이상 25% 미만 / 위험: 25% 이상
CAUTION_FROM = _f("MOAVA_CAUTION_FROM", 0.10)
DANGER_FROM = _f("MOAVA_DANGER_FROM", 0.25)
if not 0 < CAUTION_FROM < DANGER_FROM < 1:
    raise ValueError("0 < MOAVA_CAUTION_FROM < MOAVA_DANGER_FROM < 1 이어야 합니다.")

RISK_LEVELS: tuple[RiskLevel, ...] = (
    RiskLevel("SAFE", "안정", 0.0, _f("MOAVA_FACTOR_SAFE", 0.5)),
    RiskLevel("CAUTION", "주의", CAUTION_FROM, _f("MOAVA_FACTOR_CAUTION", 0.35)),
    RiskLevel("DANGER", "위험", DANGER_FROM, _f("MOAVA_FACTOR_DANGER", 0.2)),
)

# 추천 납입액 절사 단위(원). 기획서 예시: 52만 x 0.5 = 26만 -> 25만, 52만 x 0.35 = 18.2만 -> 15만
ROUND_UNIT = int(os.getenv("MOAVA_ROUND_UNIT", 50_000))
if ROUND_UNIT <= 0:
    raise ValueError("MOAVA_ROUND_UNIT 은 1 이상이어야 합니다.")

# 추천 납입액 상한: 월소득의 12% (프론트 finance.js 의 계산식과 동일)
INCOME_CAP_RATIO = _f("MOAVA_INCOME_CAP_RATIO", 0.12)
if not 0 < INCOME_CAP_RATIO <= 1:
    raise ValueError("0 < MOAVA_INCOME_CAP_RATIO <= 1 이어야 합니다.")

ML_DIR = Path(__file__).resolve().parents[2]          # MOAVA/ml
MODEL_DIR = Path(os.getenv("MOAVA_MODEL_DIR", ML_DIR / "models"))
DEFAULT_DATA = ML_DIR / "data" / "financial_credit_synthetic_155746x207.csv"


def risk_level_of(probability: float) -> RiskLevel:
    level = RISK_LEVELS[0]
    for lv in RISK_LEVELS:
        if probability >= lv.min_probability:
            level = lv
    return level
