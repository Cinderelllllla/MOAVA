"""학습 데이터 로딩과 입력 피처 선택."""
from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

ID_COL = "식별구분"
TARGET_COL = "결과값(연체회차)"
# 범주형 8개는 입력에서 제외한다.
#  - 지역·직업·직급·상품·채널·납입일은 넣어도 성능이 오르지 않았고(AUC 0.662 -> 0.660),
#  - 지역/직업으로 위험도를 매기는 것은 서비스 공정성 측면에서도 피하는 것이 맞다.
CATEGORICAL_COLS = ["GOODS_CD", "INA_CD", "AD_NO", "LIV_ADD", "RES_ADD", "ADD_YN", "RRC_CD", "HAC_CD"]


def load(path: Path) -> pd.DataFrame:
    if not Path(path).exists():
        raise SystemExit(f"데이터 파일이 없습니다: {path}\nCSV 를 ml/data/ 에 넣거나 --data 로 경로를 지정하세요.")
    dtype = {c: "string" for c in [ID_COL, *CATEGORICAL_COLS]}
    df = pd.read_csv(path, encoding="utf-8-sig", dtype=dtype, keep_default_na=False)
    missing = {ID_COL, TARGET_COL} - set(df.columns)
    if missing:
        raise SystemExit(f"필수 컬럼이 없습니다: {missing}")
    if df[ID_COL].duplicated().any():
        raise SystemExit("식별구분에 중복이 있습니다.")
    return df


def make_target(df: pd.DataFrame) -> np.ndarray:
    """향후 12개월 내 연체 1회 이상이면 1."""
    return (df[TARGET_COL] >= 1).astype(int).to_numpy()


def select_features(df: pd.DataFrame, train_idx: np.ndarray) -> list[str]:
    """ID·목표값·범주형을 뺀 수치형 컬럼 중, 앞선 컬럼과 값이 완전히 같은 별칭 컬럼을 제거."""
    candidates = [c for c in df.columns if c not in {ID_COL, TARGET_COL, *CATEGORICAL_COLS}]
    non_numeric = [c for c in candidates if not pd.api.types.is_numeric_dtype(df[c])]
    if non_numeric:
        raise SystemExit(f"수치형이 아닌 컬럼이 있습니다: {non_numeric}")
    train = df.iloc[train_idx]
    seen: dict[int, list[str]] = {}
    keep: list[str] = []
    for c in candidates:
        h = int(pd.util.hash_pandas_object(train[c], index=False).sum())
        if any(train[c].equals(train[o]) for o in seen.get(h, [])):
            continue
        seen.setdefault(h, []).append(c)
        keep.append(c)
    return keep
