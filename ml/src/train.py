"""MOAVA 연체 확률 모델 학습 스크립트 (MOA-34).

KH 제공 합성 신용 데이터(155,746행 x 207열)로 '향후 12개월 내 연체 1회 이상' 확률을
예측하는 모델을 학습하고, FastAPI 서비스가 사용할 산출물을 ml/models/ 에 저장한다.

실행 (ml 폴더에서):
    python -m src.train                    # ml/data/ 의 CSV 사용
    python -m src.train --data <CSV 경로>

산출물(ml/models/):
    model.joblib         학습된 모델 + 입력 피처 목록 + 학습 환경 버전
    metrics.json         검증 성능, 위험 구간별 실제 연체율, 피처 중요도
    persona_scores.csv   데이터셋 가상 인물 전체의 연체 확률(시연용, DB 적재 가능)
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
from sklearn.inspection import permutation_importance
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score
from sklearn.model_selection import train_test_split

from .common.config import DEFAULT_DATA, MODEL_DIR, RISK_LEVELS, risk_level_of
from .common.preprocess import ID_COL, load, make_target, select_features

SEED = 42
TEST_SIZE = 0.2


def evaluate(y: np.ndarray, p: np.ndarray) -> dict:
    out = {
        "rows": int(len(y)),
        "positive_rate": float(y.mean()),
        "roc_auc": float(roc_auc_score(y, p)),
        "average_precision": float(average_precision_score(y, p)),
        "brier": float(brier_score_loss(y, p)),
        "brier_baseline": float(brier_score_loss(y, np.full(len(y), y.mean()))),
        "risk_levels": [],
        "calibration_deciles": [],
    }
    levels = np.array([risk_level_of(v).code for v in p])
    for lv in RISK_LEVELS:
        m = levels == lv.code
        out["risk_levels"].append({
            "code": lv.code, "label": lv.label, "share": float(m.mean()), "rows": int(m.sum()),
            "mean_predicted": float(p[m].mean()) if m.any() else None,
            "actual_default_rate": float(y[m].mean()) if m.any() else None,
        })
    order = np.argsort(p)
    for chunk in np.array_split(order, 10):
        out["calibration_deciles"].append({
            "mean_predicted": float(p[chunk].mean()), "actual_default_rate": float(y[chunk].mean()),
        })
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=DEFAULT_DATA, type=Path)
    ap.add_argument("--out", default=MODEL_DIR, type=Path)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    df = load(args.data)
    y = make_target(df)
    idx = np.arange(len(df))
    train_idx, test_idx = train_test_split(idx, test_size=TEST_SIZE, stratify=y, random_state=SEED)
    features = select_features(df, train_idx)
    X = df[features].astype("float64")
    print(f"행 {len(df):,} / 입력 피처 {len(features)}개 / 양성비율 {y.mean():.4f}")

    # class_weight 를 쓰지 않는다: 확률값 자체(10%, 25% 경계)를 쓰므로 보정(calibration)이 유지돼야 한다.
    model = HistGradientBoostingClassifier(
        learning_rate=0.03, max_iter=1500, max_leaf_nodes=15, min_samples_leaf=100,
        l2_regularization=1.0, early_stopping=True, validation_fraction=0.15,
        n_iter_no_change=30, random_state=SEED,
    )
    model.fit(X.iloc[train_idx], y[train_idx])
    p_all = model.predict_proba(X)[:, 1]

    cb_rate = pd.Series(y[train_idx]).groupby(df["CB"].to_numpy()[train_idx]).mean()
    p_cb = df["CB"].iloc[test_idx].map(cb_rate).to_numpy()

    rng = np.random.RandomState(SEED)
    sub = rng.choice(test_idx, size=min(10000, len(test_idx)), replace=False)
    pi = permutation_importance(model, X.iloc[sub], y[sub], scoring="roc_auc", n_repeats=5, random_state=SEED)
    top = np.argsort(-pi.importances_mean)[:15]

    metrics = {
        "trained_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "target": "향후 12개월 내 연체 1회 이상 (결과값(연체회차) >= 1)",
        "model": "HistGradientBoostingClassifier",
        "n_iter": int(model.n_iter_),
        "n_features": len(features),
        "split": {"test_size": TEST_SIZE, "seed": SEED, "train_rows": int(len(train_idx)), "test_rows": int(len(test_idx))},
        "test": evaluate(y[test_idx], p_all[test_idx]),
        "train": {k: v for k, v in evaluate(y[train_idx], p_all[train_idx]).items() if k in ("roc_auc", "average_precision", "brier")},
        "baseline_cb_only_test_auc": float(roc_auc_score(y[test_idx], p_cb)),
        "top_features_permutation_auc_drop": [
            {"feature": features[i], "auc_drop": float(pi.importances_mean[i]), "std": float(pi.importances_std[i])} for i in top
        ],
        "note": "합성 데이터의 생성 규칙을 학습한 결과이며 실제 신용평가 성능이 아님.",
    }
    (args.out / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    joblib.dump({
        "model": model, "features": features,
        "sklearn_version": sklearn.__version__, "python_version": platform.python_version(),
        "trained_at": metrics["trained_at"], "test_auc": metrics["test"]["roc_auc"],
    }, args.out / "model.joblib", compress=3)

    split = np.where(np.isin(idx, test_idx), "test", "train")
    scores = pd.DataFrame({
        "persona_id": df[ID_COL], "default_probability": np.round(p_all, 6),
        "risk_level": [risk_level_of(v).code for v in p_all], "split": split,
    })
    scores.to_csv(args.out / "persona_scores.csv", index=False, encoding="utf-8")

    t = metrics["test"]
    print(f"테스트 AUC {t['roc_auc']:.4f} (CB 등급만: {metrics['baseline_cb_only_test_auc']:.4f}) / AP {t['average_precision']:.4f} / Brier {t['brier']:.5f}")
    for r in t["risk_levels"]:
        print(f"  {r['label']}: 비중 {r['share']:.1%}, 예측 평균 {r['mean_predicted']:.1%}, 실제 연체율 {r['actual_default_rate']:.1%}")
    print("저장 완료:", args.out)


if __name__ == "__main__":
    main()
