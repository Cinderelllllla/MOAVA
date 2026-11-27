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
uvicorn src.main:app --port 8000 --env-file .env
```

- 문서·테스트 화면: http://localhost:8000/docs
- 두 번째부터는 `activate` 와 `uvicorn` 두 줄만 실행하면 됩니다.

### 내부 공유 키 설정 (필수)

ML 서버는 **Spring 백엔드만** 부를 수 있습니다. 브라우저가 직접 부르면 남의 회원 번호로 위험 등급을 볼 수 있기 때문입니다.
Spring 은 모든 요청에 `X-Internal-Key` 헤더로 공유 키를 붙이고, ML 은 그 값이 환경변수 `MOAVA_INTERNAL_KEY` 와 다르면 **401** 을 돌려줍니다.

1. `ml/.env.example` 을 복사해 `ml/.env` 를 만들고 `MOAVA_INTERNAL_KEY=` 뒤에 키를 적습니다. (`.env` 는 git 에 올라가지 않습니다)
   - 또는 환경변수로 직접 설정: PowerShell `$env:MOAVA_INTERNAL_KEY = "<키>"`, Mac/Linux `export MOAVA_INTERNAL_KEY=<키>`
     (이 경우 `--env-file .env` 없이 `uvicorn src.main:app --port 8000`)
2. 같은 값을 `backend/src/main/resources/application-secret.properties` 의 `moava.ml.internal-key` 에 넣습니다.
3. 키 값은 팀 채널에서 따로 받으세요. **이 문서·코드·커밋에는 절대 적지 않습니다.**

- 키가 비어 있으면 서버가 시작되지 않고 안내 메시지를 출력합니다 (실수로 열린 채 켜지지 않게).
- `/health` 는 키 없이 열려 있습니다. `/risk/*` 는 모두 키가 필요합니다.
- `/docs` 화면에서 직접 시험할 때는 헤더를 붙일 수 없어 401 이 나옵니다. 시험은 Spring API(`/api/v1/finance/ai/...`)로 하세요.

## API

`/risk/*` 는 모두 `X-Internal-Key` 헤더가 필요합니다 (위 "내부 공유 키 설정"). 브라우저는 Spring 의
`POST /api/v1/finance/ai/contribution`, `GET /api/v1/finance/ai/policy`, `POST /api/v1/groups/{groupId}/ai/payout-order` 를 부릅니다.

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/health` | 상태 확인 (키 불필요) |
| GET | `/risk/model` | 입력 지표 목록, 위험 구간, 안전계수, 자가 응답 4문항·구간·test_auc |
| POST | `/risk/score` | 연체 확률 + 위험 배지 + 안전계수 |
| POST | `/risk/contribution` | 추천 납입액 |
| POST | `/risk/payout-order` | 곗돈 수령 순서 추천 |

평가 대상은 네 방식 중 **하나**로 보냅니다.
맞춤 추천(F09, `/risk/contribution`)은 `self_report`, 수령 순서 추천(`/risk/payout-order`)은 아직 `member_id` 를 씁니다.

- `self_report`: 자가 응답 4문항 선택지 코드 (아래 "자가 응답 모델"). 응답 `source` 는 `"self_report"`
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
{ "self_report": { "late_count_12m": "0", "max_late_period": "NONE", "loan_count": "2", "card_usage": "UNDER_30" },
  "monthly_income": 3200000, "monthly_expense": 2080000,
  "monthly_saving": 350000, "emergency_fund": 250000 }
// 응답
{ "available_amount": 520000, "recommended_amount": 150000, "capped": false,
  "round_unit": 50000, "income_cap_ratio": 0.12, "risk": { ... } }

// POST /risk/payout-order   — 모임원 2~100명
{ "member_ids": [1, 2, 3] }
// 응답: 연체 확률이 높은 회원부터
{ "order": [ { "order": 1, "member_id": 3, "risk": { ... } }, ... ], "rule": "..." }
```

오류: 없는 `persona_id` 는 404, 형식이 잘못된 요청(필수값 누락, 음수 금액, 0 이하 회원 번호, 목록에 없는 자가 응답 선택지 등)은 422.

## 자가 응답 모델 (F09 맞춤 추천 위험 등급, MOA-98)

회원이 직접 답할 수 있는 4문항으로 따로 학습한 작은 모델입니다. 위 189개 지표 모델은 `CB`(교육용 위험등급) 컬럼에
약 70% 의존하는데, `CB` 는 사용자에게 물어볼 수 없고 일부 지표만 넣으면 전원이 '안정'으로 나와 재사용할 수 없었습니다.

| 문항 (`self_report` 키) | 학습 컬럼 | 선택지 코드 → 모델 입력값 |
| --- | --- | --- |
| 최근 1년간 30일 이상 연체 횟수 (`late_count_12m`) | `DQ1200001` | `0`→0, `1`→1, `2`→2, `3_PLUS`→3 (원본 clip 3) |
| 최근 6개월 가장 길게 연체한 기간 (`max_late_period`) | `KC5000017` | `NONE`→0, `UNDER_1M`→15, `M1_2`→45, `M2_3`→75, `OVER_3M`→120 (원본 일수 (-1,0],(0,29],(29,59],(59,89],(89,∞)) |
| 갚고 있는 대출 수 (`loan_count`) | `L00000001` | `0`~`4`→그대로, `5_PLUS`→5 (원본 clip 5) |
| 카드 한도 중 쓰는 비율 (`card_usage`) | `CL0631905` | `NO_CARD`→0, `UNDER_30`→15, `P30_50`→40, `P50_70`→60, `P70_90`→80, `OVER_90`→100 (원본 0 = 카드 없음, 0 초과는 (0,30],(30,50],(50,70],(70,90],(90,∞)) |

- 선택지 정의는 `src/common/self_report.py` 한 곳에 있고 학습·서버가 같이 씁니다. 화면(`frontend/src/api/finance.js`)과 Spring(`SelfReportRequest`)도 같은 코드값을 씁니다.
- 알고리즘: `HistGradientBoostingClassifier(random_state=0, monotonic_cst=[1,1,1,1])` — 답이 나빠질수록 확률이 내려가지 않게 단조 제약.
  `class_weight` 는 쓰지 않습니다(확률이 실제 연체율에 맞아야 10% / 25% 경계가 의미 있음).
- 학습/테스트 분할은 `models/persona_scores.csv` 의 `split` 을 그대로 써서 기존 모델과 같은 사람들로 비교합니다.
- 등급 경계·안전계수는 위 "규칙" 표와 같습니다.
- 답변은 Spring·DB 어디에도 저장하지 않습니다(저장은 `RISK_SCORE` 의 확률·등급·안전계수만).

학습: `python -m src.train_self --data <CSV 경로>` → `models/self_model.joblib`, `models/self_metrics.json`

### 성능 (테스트 31,150명, `models/self_metrics.json`)

| | 자가 응답 4문항 | 기존 189개 지표 |
| --- | --- | --- |
| ROC-AUC | 0.622 | 0.664 |
| PR-AUC (기준선 0.099) | 0.187 | 0.229 |

| 위험도 | 비중 | 실제 연체율 |
| --- | --- | --- |
| 안정 | 74.3% | 7.4% |
| 주의 | 22.5% | 14.4% |
| 위험 | 3.2% | 35.1% |

예시 응답 → 등급: 아주 좋음(0회·없음·대출 0·30% 미만) 6.5% 안정 / 보통(0회·없음·대출 3·50~70%) 7.7% 안정 /
나쁨(1회·1개월 미만·대출 4·70~90%) 19.1% 주의 / 아주 나쁨(3회 이상·3개월 이상·5개 이상·90% 이상) 54.2% 위험

### 회원과 가상 인물 연결

DB 에 저장하지 않고 회원 번호로 정합니다.

1. `models/demo_members.json` 에 있는 회원은 거기 적힌 가상 인물 (시연용 계정)
2. 그 외 회원은 `SYN` + `(회원 번호 - 1) % 155746 + 1` (9자리, 0 채움). 예: 37번 → `SYN000000037`

합성 데이터의 가상 인물을 회원에 배정한 시연용 구조이며, 회원의 실제 정보와는 무관합니다.

## 규칙

| 위험도 | 연체 확률 | 안전계수 |
| --- | --- | --- |
| 안정 `STABLE` | 10% 미만 | 0.5 |
| 주의 `CAUTION` | 10% 이상 25% 미만 | 0.35 |
| 위험 `RISK` | 25% 이상 | 0.2 |

- 등급 코드는 DB `RISK_SCORE.TIER` 허용값(`STABLE`/`CAUTION`/`RISK`)과 같다. 응답의 `risk_level` 을 변환 없이 저장하면 된다.
- `models/persona_scores.csv` 의 `risk_level` 열은 학습 당시 기록용(옛 코드 `SAFE`/`DANGER`)이며 서버는 읽지 않는다.
  서버는 `default_probability` 만 읽고 등급은 위 표 기준으로 다시 계산한다.

- 추천 납입액 = min((월소득 − 지출 − 저축 − 비상금) × 안전계수, 월소득 × 12%), **5만 원 단위 내림**, 가용액이 0 이하면 0원.
  `frontend/src/api/finance.js` 의 `affordability()` 와 같은 식입니다. `monthly_expense` 는 고정지출 + 변동지출 합계.
- 수령 순서 = 연체 확률이 높은 회원부터 앞 회차. 동률이면 요청 순서 유지.
- 설정값은 환경변수로 변경 가능: `MOAVA_CAUTION_FROM`, `MOAVA_DANGER_FROM`, `MOAVA_FACTOR_SAFE`,
  `MOAVA_FACTOR_CAUTION`, `MOAVA_FACTOR_DANGER`, `MOAVA_ROUND_UNIT`, `MOAVA_INCOME_CAP_RATIO`.
  (환경변수 이름은 예전 코드 이름을 그대로 쓴다. `SAFE` = 안정, `DANGER` = 위험 구간 설정)

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
src/common/security.py        내부 공유 키(X-Internal-Key) 확인
src/common/preprocess.py      데이터 로딩, 입력 지표 선택
src/common/self_report.py     자가 응답 4문항 선택지·구간 (학습·서버 공용)
src/train.py                  학습 스크립트 (189개 지표 모델)
src/train_self.py             자가 응답 4문항 모델 학습 스크립트
models/                       model.joblib, metrics.json, persona_scores.csv, demo_members.json,
                              self_model.joblib, self_metrics.json
data/                         학습용 CSV 를 두는 곳 (git 제외)
```

`models/persona_scores.csv` 는 가상 인물 155,746명의 점수입니다.
시연용 계정은 `models/demo_members.json` 에서 지정하며, `split` 이 `test` 인 사람(모델이 학습 때 보지 않은 사람) 중에서 고르세요.
