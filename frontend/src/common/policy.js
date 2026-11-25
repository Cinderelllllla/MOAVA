/* 서비스 정책 기본값.
   기준은 ML 서버(ml/src/common/config.py, 백엔드 GET /api/v1/finance/ai/policy 가 대신 받아 줌)이고, 화면은 finance.getPolicy() 로 그 값을 받아 쓴다.
   여기 값은 ML 서버에 연결하지 못했을 때만 쓰는 기본값이다. */

/** 위험 등급별 안전계수와 연체 확률 구간 */
export const RISK = {
  '안정': { coef: 0.5, prob: '연체 확률 10% 미만' },
  '주의': { coef: 0.35, prob: '연체 확률 10~25%' },
  '위험': { coef: 0.2, prob: '연체 확률 25% 이상' },
};

/** 추천 납입액 상한: 월수입의 12% */
export const INCOME_CAP_RATIO = 0.12;

/** 납입액 단위: 5만 원 (추천액은 이 단위로 내림) */
export const AMOUNT_UNIT_MANWON = 5;
