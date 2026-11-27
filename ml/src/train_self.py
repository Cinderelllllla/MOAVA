"""자가 응답 4문항 모델 학습.

실행 (ml 폴더에서):
    python -m src.train_self --data <CSV 경로>
    (기본 경로: ml/data/financial_credit_synthetic_155746x207.csv — CSV 는 git 에 올리지 않는다)

결과:
    models/self_model.joblib  {model, features, buckets, sklearn_version, trained_at, test_auc}
    models/self_metrics.json  테스트셋 ROC-AUC · PR-AUC · 등급 분포 · 등급별 실제 연체율

학습/테스트 분할은 models/persona_scores.csv 의 split 컬럼을 그대로 써서 기존 모델(189개 지표)과 비교할 수 있게 한다.
class_weight 는 쓰지 않는다 — 확률이 실제 연체율에 맞아야 10% / 25% 경계가 의미 있다.
"""
from __future__ import annotations

import argparse
import json
import platform
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.metrics import average_precision_score, roc_auc_score

from .common.config import DEFAULT_DATA, MODEL_DIR, risk_level_of
from .common.preprocess import ID_COL, load, make_target
from .common.self_report import FEATURES, bucketize, describe


def main() -> None:
    ap = argparse.ArgumentParser(description="자가 응답 4문항 모델 학습")
    ap.add_argument("--data", type=Path, default=DEFAULT_DATA, help="학습 CSV 경로")
    ap.add_argument("--out", type=Path, default=MODEL_DIR, help="모델 저장 폴더")
    args = ap.parse_args()

    df = load(args.data)
    split = pd.read_csv(args.out / "persona_scores.csv", usecols=["persona_id", "split"]).set_index("persona_id")["split"]
    s = df[ID_COL].map(split)
    if s.isna().any():
        raise SystemExit(f"persona_scores.csv 에 없는 ID 가 {int(s.isna().sum())}건 있습니다.")
    train, test = (s == "train").to_numpy(), (s == "test").to_numpy()

    X, y = bucketize(df), make_target(df)
    model = HistGradientBoostingClassifier(random_state=0, monotonic_cst=[1, 1, 1, 1])
    model.fit(X[train], y[train])

    p = model.predict_proba(X[test])[:, 1]
    yt = y[test]
    codes = np.array([risk_level_of(v).code for v in p])
    tiers = {}
    for lv in ("STABLE", "CAUTION", "RISK"):
        m = codes == lv
        tiers[lv] = {"share": round(float(m.mean()), 4), "count": int(m.sum()),
                     "actual_default_rate": round(float(yt[m].mean()), 4) if m.any() else None}
    metrics = {
        "roc_auc": round(float(roc_auc_score(yt, p)), 4),
        "pr_auc": round(float(average_precision_score(yt, p)), 4),
        "base_rate": round(float(yt.mean()), 4),
        "n_train": int(train.sum()), "n_test": int(test.sum()),
        "tiers": tiers,
    }

    args.out.mkdir(parents=True, exist_ok=True)
    joblib.dump({
        "model": model, "features": FEATURES, "buckets": describe(),
        "sklearn_version": sklearn.__version__, "python_version": platform.python_version(),
        "trained_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "test_auc": metrics["roc_auc"],
    }, args.out / "self_model.joblib")
    (args.out / "self_metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"테스트 ROC-AUC {metrics['roc_auc']}  PR-AUC {metrics['pr_auc']}  (기준 연체율 {metrics['base_rate']})")
    for lv, t in tiers.items():
        print(f"  {lv:8s} 비율 {t['share']:.1%}  실제 연체율 {t['actual_default_rate']}")
    print(f"저장: {args.out / 'self_model.joblib'}, {args.out / 'self_metrics.json'}")


if __name__ == "__main__":
    main()
