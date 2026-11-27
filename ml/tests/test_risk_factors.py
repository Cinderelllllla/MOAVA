"""MOA-101 위험 등급 요인(factors) — 모델 파일(models/)만 있으면 돌아간다. 실행: ml 폴더에서 python -m pytest"""
import pytest

from src.common.config import MODEL_DIR
from src.schemas.risk import Subject
from src.service.risk_service import FACTOR_MAX_COUNT, FACTOR_MIN_IMPACT, RiskService

BEST = dict(late_count_12m="0", max_late_period="NONE", loan_count="0", card_usage="UNDER_30")
WORST = dict(late_count_12m="3_PLUS", max_late_period="OVER_3M", loan_count="5_PLUS", card_usage="OVER_90")


@pytest.fixture(scope="module")
def sv():
    return RiskService(MODEL_DIR)


def test_가장_좋은_답이면_요인이_없다(sv):
    r = sv.by_self_report(BEST)
    assert r.risk_level == "STABLE"
    assert r.factors == []


def test_연체_3회_이상이면_그_문항이_요인에_들어간다(sv):
    r = sv.by_self_report({**BEST, "late_count_12m": "3_PLUS"})
    keys = [f.key for f in r.factors]
    assert "late_count_12m" in keys
    f = r.factors[keys.index("late_count_12m")]
    assert f.impact > 0
    assert f.answer_label == "3회 이상"
    assert f.question == "최근 1년간 30일 이상 연체한 횟수"


def test_요인은_최대_2개이고_영향이_큰_순서(sv):
    r = sv.by_self_report(WORST)
    assert 1 <= len(r.factors) <= FACTOR_MAX_COUNT
    impacts = [f.impact for f in r.factors]
    assert impacts == sorted(impacts, reverse=True)
    assert all(i >= FACTOR_MIN_IMPACT for i in impacts)


def test_impact_는_그_문항만_가장_좋은_답으로_바꿨을_때_줄어드는_확률(sv):
    answers = {**BEST, "late_count_12m": "3_PLUS"}
    r = sv.by_self_report(answers)
    f = next(f for f in r.factors if f.key == "late_count_12m")
    better = sv.by_self_report({**answers, "late_count_12m": "0"})   # 이 문항만 가장 좋은 답
    assert f.impact == pytest.approx(r.default_probability - better.default_probability, abs=1e-3)


def test_self_report_가_아니면_factors_는_None(sv):
    assert sv.by_member(1).factors is None
    assert sv.assess(Subject(member_id=1)).factors is None


def test_assess_로_self_report_를_보내도_factors_가_채워진다(sv):
    r = sv.assess(Subject(self_report=BEST))
    assert r.source == "self_report"
    assert r.factors == []
