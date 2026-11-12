# MOAVA ML (FastAPI) — MOA-34 AI 금융 분석 기능

KH 제공 합성 신용 데이터로 **향후 12개월 내 연체 확률**을 예측하고, 그 결과로
연체 위험 배지 · 추천 납입액 · 곗돈 수령 순서를 돌려주는 서버입니다.
Spring Boot 가 `moava.ml.base-url`(기본 `http://localhost:8000`)로 호출합니다.

> 전부 가상(합성) 데이터로 학습한 교육용 결과이며 실제 신용평가가 아닙니다.

## 서버 켜기 (학습 불필요 — 모델 파일이 저장소에 들어 있음)

`ml` 폴더에서 실행합니다. Python 3.11 이상.

```bash
python -m venv .venv
.venv\Scripts\activate            # Mac: source .venv/bin/activate
pip install -r requirements.txt
uvicorn src.main:app --port 8000
```

- 문서·테스트 화면: http://localhost:8000/docs
- 두 번째부터는 `activate` 와 `uvicorn` 두 줄만 실행하면 됩니다.

## API

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/health` | 상태 확인 |
| GET | `/risk/model` | 입력 지표 목록, 위험 구간, 안전계수 |
| POST | `/risk/score` | 연체 확률 + 위험 배지 + 안전계수 |
| POST | `/risk/contribution` | 추천 납입액 |
| POST | `/risk/payout-order` | 곗돈 수령 순서 추천 |

평가 대상은 세 방식 중 **하나**로 보냅니다. 백엔드는 `member_id` 만 쓰면 됩니다.

- `member_id`: 서비스 회원 번호(숫자). 가상 인물로 자동 연결됩니다.
- `persona_id`: 데이터셋 가상 인물 ID (`SYN000000001` 형식)
- `features`: 신용 지표 `{컬럼명: 값}`. 빠진 지표는 결측으로 처리

```jsonc
// POST /risk/score
{ "member_id": 2 }
// 응답
{ "default_probability": 0.153545, "risk_level": "CAUTION", "risk_label": "주의",
  "safety_factor": 0.35, "source": "persona", "features_used": null }

// POST /risk/contribution   — 금액 단위는 "원" (프론트 화면은 만원 단위이므로 변환 필요)
{ "member_id": 2, "monthly_income": 3200000, "monthly_expense": 2080000,
  "monthly_saving": 350000, "emergency_fund": 250000 }
// 응답
{ "available_amount": 520000, "recommended_amount": 150000, "capped": false,
  "round_unit": 50000, "income_cap_ratio": 0.12, "risk": { ... } }

// POST /risk/payout-order   — 모임원 2~100명
{ "member_ids": [1, 2, 3] }
// 응답: 연체 확률이 높은 회원부터
{ "order": [ { "order": 1, "member_id": 3, "risk": { ... } }, ... ], "rule": "..." }
```

오류: 없는 `persona_id` 는 404, 형식이 잘못된 요청(필수값 누락, 음수 금액, 0 이하 회원 번호 등)은 422.

### 회원과 가상 인물 연결

DB 에 저장하지 않고 회원 번호로 정합니다.

1. `models/demo_members.json` 에 있는 회원은 거기 적힌 가상 인물 (시연용 계정)
2. 그 외 회원은 `SYN` + `(회원 번호 - 1) % 155746 + 1` (9자리, 0 채움). 예: 37번 → `SYN000000037`

합성 데이터의 가상 인물을 회원에 배정한 시연용 구조이며, 회원의 실제 정보와는 무관합니다.

## 규칙

| 위험도 | 연체 확률 | 안전계수 |
| --- | --- | --- |
| 안정 `SAFE` | 10% 미만 | 0.5 |
| 주의 `CAUTION` | 10% 이상 25% 미만 | 0.35 |
| 위험 `DANGER` | 25% 이상 | 0.2 |

- 추천 납입액 = min((월소득 − 지출 − 저축 − 비상금) × 안전계수, 월소득 × 12%), **5만 원 단위 내림**, 가용액이 0 이하면 0원.
  `frontend/src/api/finance.js` 의 `affordability()` 와 같은 식입니다. `monthly_expense` 는 고정지출 + 변동지출 합계.
- 수령 순서 = 연체 확률이 높은 회원부터 앞 회차. 동률이면 요청 순서 유지.
- 설정값은 환경변수로 변경 가능: `MOAVA_CAUTION_FROM`, `MOAVA_DANGER_FROM`, `MOAVA_FACTOR_SAFE`,
  `MOAVA_FACTOR_CAUTION`, `MOAVA_FACTOR_DANGER`, `MOAVA_ROUND_UNIT`, `MOAVA_INCOME_CAP_RATIO`.

## 다시 학습하기 (데이터나 학습 코드가 바뀌었을 때만)

1. `financial_credit_synthetic_155746x207.csv` 를 `ml/data/` 에 넣는다 (git 에는 올라가지 않음).
2. `python -m src.train` (약 1분, 시드 고정이라 결과 재현됨)
3. 바뀐 `models/` 파일을 커밋한다.

## 모델 성능 (테스트 31,150명, `models/metrics.json`)

- 입력: 수치형 신용 지표 189개 (ID·목표값·범주형 8개·중복 컬럼 8개 제외)
- 알고리즘: scikit-learn `HistGradientBoostingClassifier`, 학습 80% / 테스트 20%
- ROC-AUC 0.664 (CB 등급만 쓰면 0.654), Average Precision 0.229 (기준선 0.099)

| 위험도 | 비중 | 예측 평균 | 실제 연체율 |
| --- | --- | --- | --- |
| 안정 | 77.1% | 6.7% | 6.9% |
| 주의 | 17.4% | 15.4% | 15.1% |
| 위험 | 5.5% | 35.5% | 34.8% |

AUC 가 낮은 것은 데이터 특성입니다(제공 측 점검값 0.671, 연체 여부가 난수로 생성됨).
대신 예측 확률과 실제 연체율이 구간별로 잘 맞아 배지 구분에는 쓸 수 있습니다.

## 폴더

```
src/main.py                   FastAPI 앱
src/router/risk.py            엔드포인트
src/schemas/risk.py           요청/응답 형식
src/service/risk_service.py   모델 로딩, 예측·추천 로직
src/common/config.py          위험 구간·안전계수 설정
src/common/preprocess.py      데이터 로딩, 입력 지표 선택
src/train.py                  학습 스크립트
models/                       model.joblib, metrics.json, persona_scores.csv, demo_members.json
data/                         학습용 CSV 를 두는 곳 (git 제외)
```

`models/persona_scores.csv` 는 가상 인물 155,746명의 점수입니다.
시연용 계정은 `models/demo_members.json` 에서 지정하며, `split` 이 `test` 인 사람(모델이 학습 때 보지 않은 사람) 중에서 고르세요.
