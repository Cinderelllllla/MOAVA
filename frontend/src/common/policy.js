/* 서비스 정책 상수 — 한 곳에서만 바꾼다. (ML 서버 ml/src/common/config.py 의 값과 같아야 한다) */

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
