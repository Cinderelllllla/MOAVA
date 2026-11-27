"""자가 응답 4문항 — 화면 선택지 코드 ↔ 모델 입력값 ↔ 학습 데이터 구간화.

학습(train_self.py)과 서버(risk_service.py)가 이 파일 하나를 같이 써서,
화면에서 고른 선택지가 학습 때와 똑같은 값으로 모델에 들어가게 한다.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd


@dataclass(frozen=True)
class Question:
    key: str                       # API 필드 이름 (self_report.<key>)
    column: str                    # 학습 데이터 컬럼
    text: str                      # 화면 질문
    choices: tuple[tuple[str, str, float], ...]  # (선택지 코드, 화면 라벨, 모델 입력값)

    @property
    def codes(self) -> tuple[str, ...]:
        return tuple(c for c, _, _ in self.choices)

    def value_of(self, code: str) -> float:
        for c, _, v in self.choices:
            if c == code:
                return v
        raise KeyError(code)


QUESTIONS: tuple[Question, ...] = (
    Question("late_count_12m", "DQ1200001", "최근 1년간 30일 이상 연체한 횟수", (
        ("0", "0회", 0), ("1", "1회", 1), ("2", "2회", 2), ("3_PLUS", "3회 이상", 3),
    )),
    Question("max_late_period", "KC5000017", "최근 6개월 동안 가장 길게 연체한 기간", (
        ("NONE", "없음", 0), ("UNDER_1M", "1개월 미만", 15), ("M1_2", "1~2개월", 45),
        ("M2_3", "2~3개월", 75), ("OVER_3M", "3개월 이상", 120),
    )),
    Question("loan_count", "L00000001", "지금 갚고 있는 대출 수", (
        ("0", "0개", 0), ("1", "1개", 1), ("2", "2개", 2), ("3", "3개", 3), ("4", "4개", 4), ("5_PLUS", "5개 이상", 5),
    )),
    Question("card_usage", "CL0631905", "신용카드 한도 중 쓰고 있는 비율", (
        ("NO_CARD", "카드 없음", 0), ("UNDER_30", "30% 미만", 15), ("P30_50", "30~50%", 40),
        ("P50_70", "50~70%", 60), ("P70_90", "70~90%", 80), ("OVER_90", "90% 이상", 100),
    )),
)
FEATURES: list[str] = [q.column for q in QUESTIONS]


def to_features(answers: dict[str, str]) -> dict[str, float]:
    """{문항 key: 선택지 코드} → {학습 컬럼: 모델 입력값}. 코드 검사는 스키마(422)에서 먼저 한다."""
    return {q.column: float(q.value_of(answers[q.key])) for q in QUESTIONS}


def answer_label(key: str, code: str) -> str:
    """문항 key · 선택지 코드 → 화면 라벨 (예: card_usage, P70_90 → "70~90%")"""
    for q in QUESTIONS:
        if q.key == key:
            for c, label, _ in q.choices:
                if c == code:
                    return label
    raise KeyError((key, code))


def bucketize(df: pd.DataFrame) -> pd.DataFrame:
    """학습 데이터 원본 값을 화면 선택지와 같은 값으로 바꾼다."""
    out = pd.DataFrame(index=df.index)
    out["DQ1200001"] = df["DQ1200001"].clip(upper=3).astype(float)
    out["KC5000017"] = pd.cut(df["KC5000017"], [-1, 0, 29, 59, 89, np.inf],
                              labels=[0, 15, 45, 75, 120]).astype(float)
    out["L00000001"] = df["L00000001"].clip(upper=5).astype(float)
    # 원본에서 카드가 없는 사람은 0 → "카드 없음"(0). 0 초과는 (0,30],(30,50],(50,70],(70,90],(90,∞)
    usage = pd.cut(df["CL0631905"], [-1, 30, 50, 70, 90, np.inf], labels=[15, 40, 60, 80, 100]).astype(float)
    out["CL0631905"] = usage.where(df["CL0631905"] > 0, 0.0)
    return out[FEATURES]


def describe() -> list[dict]:
    """GET /risk/model 에 넣을 문항·선택지 설명."""
    return [{"key": q.key, "column": q.column, "text": q.text,
             "choices": [{"code": c, "label": l, "value": v} for c, l, v in q.choices]} for q in QUESTIONS]
