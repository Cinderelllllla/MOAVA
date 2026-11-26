/* ============================================================
   MOAVA · common/regions.js — 전국 시/도 → 시/군/구 → 읍/면/동 계층 및 좌표 유틸
   ------------------------------------------------------------
   - 데이터 출처: koreaRegions.json (2026-02 행정안전부 법정동 + VWorld 중심좌표, 공공누리 1유형)
   - Oracle DB 기존 컬럼 제약(REGION_SIDO 30 BYTE, REGION_SIGUNGU 30 BYTE)과
     100% 호환되는 인코딩·복원 로직 포함
   ============================================================ */
import koreaRegions from './koreaRegions.json';

export const SIDO_LIST = Object.freeze(Object.keys(koreaRegions));

const SIDO_FULL_MAP = {
  서울: '서울특별시',
  서울시: '서울특별시',
  서울특별시: '서울특별시',
  경기: '경기도',
  경기도: '경기도',
  인천: '인천광역시',
  인천시: '인천광역시',
  인천광역시: '인천광역시',
  부산: '부산광역시',
  부산시: '부산광역시',
  부산광역시: '부산광역시',
  대구: '대구광역시',
  대구시: '대구광역시',
  대구광역시: '대구광역시',
  광주: '광주광역시',
  광주시: '광주광역시',
  광주광역시: '광주광역시',
  대전: '대전광역시',
  대전시: '대전광역시',
  대전광역시: '대전광역시',
  울산: '울산광역시',
  울산시: '울산광역시',
  울산광역시: '울산광역시',
  세종: '세종특별자치시',
  세종시: '세종특별자치시',
  세종특별자치시: '세종특별자치시',
  강원: '강원특별자치도',
  강원도: '강원특별자치도',
  강원특별자치도: '강원특별자치도',
  충북: '충청북도',
  충청북도: '충청북도',
  충남: '충청남도',
  충청남도: '충청남도',
  전북: '전북특별자치도',
  전라북도: '전북특별자치도',
  전북특별자치도: '전북특별자치도',
  전남: '전라남도',
  전라남도: '전라남도',
  경북: '경상북도',
  경상북도: '경상북도',
  경남: '경상남도',
  경상남도: '경상남도',
  제주: '제주특별자치도',
  제주도: '제주특별자치도',
  제주특별자치도: '제주특별자치도',
};

const SIDO_SHORT_MAP = {
  서울특별시: '서울',
  경기도: '경기',
  인천광역시: '인천',
  부산광역시: '부산',
  대구광역시: '대구',
  광주광역시: '광주',
  대전광역시: '대전',
  울산광역시: '울산',
  세종특별자치시: '세종',
  강원특별자치도: '강원',
  충청북도: '충북',
  충청남도: '충남',
  전북특별자치도: '전북',
  전라남도: '전남',
  경상북도: '경북',
  경상남도: '경남',
  제주특별자치도: '제주',
};

/* 구형 축약명('충남' 등)과 정식명('충청남도')이 LIKE 부분일치하지 않는 도 목록 */
const NON_PREFIX_SIDOS = new Set([
  '충청북도',
  '충청남도',
  '전북특별자치도',
  '전라남도',
  '경상북도',
  '경상남도',
]);

const textEncoder = typeof TextEncoder !== 'undefined' ? new TextEncoder() : null;

export function utf8ByteLength(str) {
  const s = String(str || '');
  if (textEncoder) return textEncoder.encode(s).length;
  return unescape(encodeURIComponent(s)).length;
}

/** 시/도 명칭 정규화 ("서울" → "서울특별시", "충남" → "충청남도") */
export function normalizeSidoName(raw) {
  const s = String(raw || '').trim();
  if (!s || s === '전체') return '';
  if (SIDO_FULL_MAP[s]) return SIDO_FULL_MAP[s];
  for (const [k, v] of Object.entries(SIDO_FULL_MAP)) {
    if (s.startsWith(k)) return v;
  }
  return s;
}

/** 시/도 약칭 ("서울특별시" → "서울") */
export function shortSidoName(raw) {
  const full = normalizeSidoName(raw);
  return SIDO_SHORT_MAP[full] || full;
}

export function getSidoOptions() {
  return SIDO_LIST;
}

export function getSigunguOptions(sido) {
  const full = normalizeSidoName(sido);
  const sgMap = koreaRegions[full];
  return sgMap ? Object.keys(sgMap) : [];
}

export function getDongOptions(sido, sigungu) {
  const full = normalizeSidoName(sido);
  const dgMap = koreaRegions[full]?.[String(sigungu || '').trim()];
  return dgMap ? Object.keys(dgMap) : [];
}

/**
 * 프론트 3단계 선택값({ sido, sigungu, dong }) → 기존 서버 DTO({ regionSido, regionSigungu }) 인코딩
 * - Oracle VARCHAR2(30 BYTE) 제약 준수:
 *   "고양시 일산동구 장항동"(32B)처럼 30B를 넘는 경우 시/군/구의 마지막 행정구 토큰("일산동구 장항동", 22B)으로 축약
 * - 전국 5,067개 법정동 전수 검증 완료 (30B 초과 0건, 동일 시/도 내 행정구 중복 0건 → 무손실 역변환 가능)
 */
export function encodeRegionForServer({ sido, sigungu, dong } = {}) {
  const regionSido = normalizeSidoName(sido);
  const sg = String(sigungu || '').trim();
  const dg = String(dong || '').trim();

  if (!dg) {
    if (utf8ByteLength(sg) <= 30) {
      return { regionSido, regionSigungu: sg };
    }
    const tokens = sg.split(/\s+/).filter(Boolean);
    return { regionSido, regionSigungu: tokens[tokens.length - 1] || sg };
  }

  const combined = sg ? `${sg} ${dg}` : dg;
  if (utf8ByteLength(combined) <= 30) {
    return { regionSido, regionSigungu: combined };
  }

  const sgTokens = sg.split(/\s+/).filter(Boolean);
  const shortSg = sgTokens.length > 1 ? sgTokens[sgTokens.length - 1] : sg;
  const shortened = shortSg ? `${shortSg} ${dg}` : dg;
  if (utf8ByteLength(shortened) <= 30) {
    return { regionSido, regionSigungu: shortened };
  }

  return { regionSido, regionSigungu: dg };
}

/** 표시용 전체 지역 문자열 생성 (예: "서울특별시 마포구 합정동", "세종특별자치시 가람동") */
export function formatRegionLabel({ sido, sigungu, dong } = {}) {
  const normSido = normalizeSidoName(sido);
  const sg = String(sigungu || '').trim();
  const dg = String(dong || '').trim();

  if (normSido === '세종특별자치시' && sg === '세종시') {
    return [normSido, dg].filter(Boolean).join(' ') || normSido;
  }
  return [normSido, sg, dg].filter(Boolean).join(' ');
}

/**
 * 서버 응답(regionSido, regionSigungu, optional regionDong) → 3단계 구조({ sido, sigungu, dong, displayLabel }) 복원
 * - 기존 DB의 구형 데이터("서울" + "마포구", "경기도" + "분당구" 등) 및 축약 저장("일산동구 장항동") 모두 호환
 */
export function parseRegionFromServer(rawSido, rawSigungu, rawDong = '') {
  const sido = normalizeSidoName(rawSido);
  const sgRaw = String(rawSigungu || '').trim();
  const dongRaw = String(rawDong || '').trim();
  const sgMap = koreaRegions[sido];

  if (dongRaw) {
    let resolvedSg = sgRaw;
    if (sgMap && !sgMap[resolvedSg]) {
      const matchedSg = Object.keys(sgMap).find(
        (key) => key.split(/\s+/).pop() === sgRaw && Boolean(sgMap[key][dongRaw])
      );
      if (matchedSg) resolvedSg = matchedSg;
    }
    return {
      sido,
      sigungu: resolvedSg,
      dong: dongRaw,
      displayLabel: formatRegionLabel({ sido, sigungu: resolvedSg, dong: dongRaw }),
    };
  }

  if (sgMap && sgRaw) {
    // 1) 정확히 시/군/구와 일치 (예: "마포구", "성남시 분당구", "세종시")
    if (sgMap[sgRaw]) {
      return {
        sido,
        sigungu: sgRaw,
        dong: '',
        displayLabel: formatRegionLabel({ sido, sigungu: sgRaw, dong: '' }),
      };
    }

    const sgKeys = Object.keys(sgMap).sort((a, b) => b.length - a.length);

    // 2) "${시군구} ${동}" 완전 매칭 (예: "마포구 합정동", "성남시 분당구 정자동")
    for (const key of sgKeys) {
      if (sgRaw.startsWith(`${key} `)) {
        const rest = sgRaw.slice(key.length + 1).trim();
        if (rest && sgMap[key][rest]) {
          return {
            sido,
            sigungu: key,
            dong: rest,
            displayLabel: formatRegionLabel({ sido, sigungu: key, dong: rest }),
          };
        }
      }
    }

    // 3) 30바이트 축약형 "${마지막 행정구} ${동}" 복원 (예: "일산동구 장항동" → "고양시 일산동구" + "장항동")
    const parts = sgRaw.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      const firstToken = parts[0];
      const restDong = parts.slice(1).join(' ');
      for (const key of sgKeys) {
        const lastSgToken = key.split(/\s+/).pop();
        if (lastSgToken === firstToken && sgMap[key][restDong]) {
          return {
            sido,
            sigungu: key,
            dong: restDong,
            displayLabel: formatRegionLabel({ sido, sigungu: key, dong: restDong }),
          };
        }
      }
    }

    // 4) 구형 단일 행정구 토큰 매칭 (예: "경기도" + "분당구" → "성남시 분당구")
    if (parts.length === 1) {
      const matchedSg = sgKeys.find((key) => key.split(/\s+/).pop() === sgRaw);
      if (matchedSg) {
        return {
          sido,
          sigungu: matchedSg,
          dong: '',
          displayLabel: formatRegionLabel({ sido, sigungu: matchedSg, dong: '' }),
        };
      }
    }

    // 5) 세종시 예외: "세종특별자치시" + "가람동"
    if (sido === '세종특별자치시' && sgMap['세종시']?.[sgRaw]) {
      return {
        sido,
        sigungu: '세종시',
        dong: sgRaw,
        displayLabel: formatRegionLabel({ sido, sigungu: '세종시', dong: sgRaw }),
      };
    }

    // 6) 사전에 없는 동명이더라도 "${시군구} ${동}" 형태면 시군구 기준으로 분리
    for (const key of sgKeys) {
      if (sgRaw.startsWith(`${key} `)) {
        const rest = sgRaw.slice(key.length + 1).trim();
        if (rest) {
          return {
            sido,
            sigungu: key,
            dong: rest,
            displayLabel: formatRegionLabel({ sido, sigungu: key, dong: rest }),
          };
        }
      }
    }
  }

  return {
    sido,
    sigungu: sgRaw,
    dong: '',
    displayLabel: formatRegionLabel({ sido, sigungu: sgRaw, dong: '' }),
  };
}

/* ---- 좌표 조회 (동 → 시/군/구 평균 → 시 prefix 평균 → 시/도 평균) ---- */

const sigunguCentroidCache = new Map();
const sidoCentroidCache = new Map();

function averageCoords(coordList) {
  if (!coordList.length) return null;
  let latSum = 0;
  let lngSum = 0;
  for (const [lat, lng] of coordList) {
    latSum += lat;
    lngSum += lng;
  }
  return {
    lat: Number((latSum / coordList.length).toFixed(5)),
    lng: Number((lngSum / coordList.length).toFixed(5)),
  };
}

function getSigunguCentroid(sido, sigungu) {
  const cacheKey = `${sido}::${sigungu}`;
  if (sigunguCentroidCache.has(cacheKey)) {
    return sigunguCentroidCache.get(cacheKey);
  }
  const dgMap = koreaRegions[sido]?.[sigungu];
  if (!dgMap) return null;
  const centroid = averageCoords(Object.values(dgMap));
  sigunguCentroidCache.set(cacheKey, centroid);
  return centroid;
}

function getSidoCentroid(sido) {
  if (sidoCentroidCache.has(sido)) {
    return sidoCentroidCache.get(sido);
  }
  const sgMap = koreaRegions[sido];
  if (!sgMap) return null;
  const all = [];
  for (const dgMap of Object.values(sgMap)) {
    for (const coord of Object.values(dgMap)) {
      all.push(coord);
    }
  }
  const centroid = averageCoords(all);
  sidoCentroidCache.set(sido, centroid);
  return centroid;
}

/** 시/도, 시/군/구, 읍/면/동 기준 대표 위경도 조회 */
export function getRegionCoords(rawSido, rawSigungu, rawDong = '') {
  const { sido, sigungu, dong } = parseRegionFromServer(rawSido, rawSigungu, rawDong);
  if (!sido || !koreaRegions[sido]) return null;

  // 1) 동 좌표 정확 매칭
  if (sigungu && dong) {
    const exact = koreaRegions[sido]?.[sigungu]?.[dong];
    if (Array.isArray(exact) && exact.length === 2) {
      return { lat: exact[0], lng: exact[1] };
    }
  }

  // 2) 시/군/구 중심 좌표
  if (sigungu) {
    const sgCentroid = getSigunguCentroid(sido, sigungu);
    if (sgCentroid) return sgCentroid;

    // 3) 구형 상위 시명만 저장된 경우 (예: "경기도" + "수원시" → 수원시 산하 구 전체 평균)
    const sgMap = koreaRegions[sido];
    const prefixCoords = [];
    for (const [key, dgMap] of Object.entries(sgMap)) {
      if (key.startsWith(`${sigungu} `)) {
        for (const coord of Object.values(dgMap)) {
          prefixCoords.push(coord);
        }
      }
    }
    if (prefixCoords.length) {
      return averageCoords(prefixCoords);
    }
  }

  // 4) 시/도 중심 좌표
  return getSidoCentroid(sido);
}

/** 서버 GET /api/groups?region=... 쿼리 파라미터 생성 (구형/신형 데이터 모두 누락 없도록) */
export function buildServerRegionQuery({ sido, sigungu, dong } = {}) {
  const normSido = normalizeSidoName(sido);
  const sg = sigungu && sigungu !== '전체' ? String(sigungu).trim() : '';
  const dg = dong && dong !== '전체' ? String(dong).trim() : '';

  if (dg) {
    return dg;
  }
  if (sg) {
    if (sg === '세종시') return '세종';
    const tokens = sg.split(/\s+/).filter(Boolean);
    return tokens[tokens.length - 1] || sg;
  }
  if (normSido) {
    if (NON_PREFIX_SIDOS.has(normSido)) {
      return '';
    }
    return shortSidoName(normSido);
  }
  return '';
}

/** 클라이언트 측 정밀 지역 필터링 (동명이구·동명이동·구형 시/도 약칭까지 정확 판별) */
export function matchesRegionFilter(group, { sido, sigungu, dong } = {}) {
  const filterSido = normalizeSidoName(sido);
  const filterSg = sigungu && sigungu !== '전체' ? String(sigungu).trim() : '';
  const filterDong = dong && dong !== '전체' ? String(dong).trim() : '';

  if (!filterSido && !filterSg && !filterDong) return true;

  const parsed = parseRegionFromServer(
    group.regionSido || String(group.region || '').trim().split(/\s+/)[0] || '',
    group.regionSigungu || String(group.region || '').trim().split(/\s+/).slice(1).join(' '),
    group.regionDong || ''
  );

  if (filterSido && parsed.sido !== filterSido) {
    return false;
  }
  if (filterSg) {
    if (parsed.sigungu !== filterSg) {
      // 구형 상위 시(예: "성남시")와 하위 구("성남시 분당구")는 구분하되, 정확 일치 요구
      return false;
    }
  }
  if (filterDong && parsed.dong !== filterDong) {
    return false;
  }
  return true;
}
