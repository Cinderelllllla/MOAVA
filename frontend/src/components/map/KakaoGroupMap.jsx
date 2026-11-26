/* F13 지역 계모임 지도 — Kakao Maps 인터랙티브 지도 + 시/군/구 구름 핀 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { loadKakaoMaps } from './loadKakaoMaps';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 }; // 서울 시청
const DEFAULT_LEVEL = 7;

/* 좌표(LAT/LNG)가 없는 기존 DB 행용 시/군/구 폴백 좌표 */
const REGION_FALLBACK_COORDS = {
  // 서울특별시 25개 자치구
  강남구: { lat: 37.5172, lng: 127.0473 },
  강동구: { lat: 37.5301, lng: 127.1238 },
  강북구: { lat: 37.6396, lng: 127.0257 },
  강서구: { lat: 37.5509, lng: 126.8495 },
  관악구: { lat: 37.4784, lng: 126.9516 },
  광진구: { lat: 37.5385, lng: 127.0823 },
  구로구: { lat: 37.4954, lng: 126.8874 },
  금천구: { lat: 37.4569, lng: 126.8955 },
  노원구: { lat: 37.6542, lng: 127.0568 },
  도봉구: { lat: 37.6688, lng: 127.0471 },
  동대문구: { lat: 37.5744, lng: 127.0396 },
  동작구: { lat: 37.5124, lng: 126.9393 },
  마포구: { lat: 37.5663, lng: 126.9018 },
  서대문구: { lat: 37.5791, lng: 126.9368 },
  서초구: { lat: 37.4837, lng: 127.0324 },
  성동구: { lat: 37.5634, lng: 127.0369 },
  성북구: { lat: 37.5894, lng: 127.0167 },
  송파구: { lat: 37.5145, lng: 127.1059 },
  양천구: { lat: 37.517, lng: 126.8664 },
  영등포구: { lat: 37.5264, lng: 126.8963 },
  용산구: { lat: 37.5326, lng: 126.9905 },
  은평구: { lat: 37.6027, lng: 126.9291 },
  종로구: { lat: 37.573, lng: 126.9794 },
  중구: { lat: 37.5641, lng: 126.9979 },
  중랑구: { lat: 37.6063, lng: 127.0925 },
  // 경기·인천 주요 시/구
  수원시: { lat: 37.2636, lng: 127.0286 },
  성남시: { lat: 37.4201, lng: 127.1262 },
  분당구: { lat: 37.3827, lng: 127.1189 },
  용인시: { lat: 37.2411, lng: 127.1776 },
  고양시: { lat: 37.6584, lng: 126.832 },
  부천시: { lat: 37.5034, lng: 126.766 },
  안양시: { lat: 37.3943, lng: 126.9568 },
  화성시: { lat: 37.1995, lng: 126.8312 },
  남양주시: { lat: 37.636, lng: 127.2165 },
  안산시: { lat: 37.3219, lng: 126.8309 },
  평택시: { lat: 36.9921, lng: 127.1129 },
  연수구: { lat: 37.4103, lng: 126.6783 },
  남동구: { lat: 37.4472, lng: 126.7316 },
  부평구: { lat: 37.5074, lng: 126.7218 },
  // 광역시·도 주요 지역
  해운대구: { lat: 35.1631, lng: 129.1636 },
  부산진구: { lat: 35.163, lng: 129.0533 },
  수영구: { lat: 35.1456, lng: 129.1132 },
  수성구: { lat: 35.8581, lng: 128.6306 },
  유성구: { lat: 36.3623, lng: 127.3561 },
  제주시: { lat: 33.4996, lng: 126.5312 },
  서귀포시: { lat: 33.2541, lng: 126.5601 },
};

/* 시/군/구 매칭이 없을 때 사용하는 시/도 중심 폴백 좌표 */
const SIDO_FALLBACK_COORDS = {
  서울: { lat: 37.5665, lng: 126.978 },
  경기: { lat: 37.2752, lng: 127.0095 },
  인천: { lat: 37.4563, lng: 126.7052 },
  부산: { lat: 35.1796, lng: 129.0756 },
  대구: { lat: 35.8714, lng: 128.6014 },
  대전: { lat: 36.3504, lng: 127.3845 },
  광주: { lat: 35.1595, lng: 126.8526 },
  울산: { lat: 35.5384, lng: 129.3114 },
  세종: { lat: 36.48, lng: 127.289 },
  강원: { lat: 37.8854, lng: 127.7298 },
  충북: { lat: 36.6357, lng: 127.4912 },
  충남: { lat: 36.6588, lng: 126.6728 },
  전북: { lat: 35.8203, lng: 127.1088 },
  전남: { lat: 34.8161, lng: 126.4629 },
  경북: { lat: 36.576, lng: 128.5056 },
  경남: { lat: 35.2383, lng: 128.6924 },
  제주: { lat: 33.4996, lng: 126.5312 },
};

export function normalizeSido(sido) {
  const s = String(sido || '').trim();
  if (!s) return '';
  if (s.startsWith('서울')) return '서울';
  if (s.startsWith('부산')) return '부산';
  if (s.startsWith('대구')) return '대구';
  if (s.startsWith('인천')) return '인천';
  if (s.startsWith('광주')) return '광주';
  if (s.startsWith('대전')) return '대전';
  if (s.startsWith('울산')) return '울산';
  if (s.startsWith('세종')) return '세종';
  if (s.startsWith('경기')) return '경기';
  if (s.startsWith('강원')) return '강원';
  if (s.startsWith('충청북') || s.startsWith('충북')) return '충북';
  if (s.startsWith('충청남') || s.startsWith('충남')) return '충남';
  if (s.startsWith('전라북') || s.startsWith('전북')) return '전북';
  if (s.startsWith('전라남') || s.startsWith('전남')) return '전남';
  if (s.startsWith('경상북') || s.startsWith('경북')) return '경북';
  if (s.startsWith('경상남') || s.startsWith('경남')) return '경남';
  if (s.startsWith('제주')) return '제주';
  return s;
}

export function resolvePinMeta(group) {
  const sidoRaw =
    group.regionSido?.trim() ||
    String(group.region || '').trim().split(/\s+/)[0] ||
    '';
  const sigungu =
    group.regionSigungu?.trim() ||
    (() => {
      const tokens = String(group.region || '').trim().split(/\s+/).filter(Boolean);
      return tokens.length ? tokens[tokens.length - 1] : '기타';
    })();
  const sido = normalizeSido(sidoRaw);
  const key = sido ? `${sido}::${sigungu}` : sigungu;
  return { key, sido, label: sigungu };
}

function hasValidCoords(g) {
  if (g.lat == null || g.lng == null || g.lat === '' || g.lng === '') {
    return false;
  }
  const lat = Number(g.lat);
  const lng = Number(g.lng);
  return Number.isFinite(lat) && Number.isFinite(lng) && !(lat === 0 && lng === 0);
}

function resolvePinCoords(label, sido, regionGroups) {
  const valid = regionGroups.filter(hasValidCoords);
  if (valid.length > 0) {
    const lat = valid.reduce((sum, g) => sum + Number(g.lat), 0) / valid.length;
    const lng = valid.reduce((sum, g) => sum + Number(g.lng), 0) / valid.length;
    return { lat, lng };
  }
  // 동일 구명(중구·동구·서구 등)은 시/도가 서울 외이면 시/도 대표 좌표를 우선 사용
  if (REGION_FALLBACK_COORDS[label] && (!sido || sido === '서울' || !['중구', '동구', '서구', '남구', '북구', '강서구'].includes(label))) {
    return REGION_FALLBACK_COORDS[label];
  }
  // 시/군/구 첫 토큰(예: "성남시 분당구" → "분당구" 또는 "성남시") 매칭
  const tokens = String(label || '').trim().split(/\s+/).filter(Boolean);
  for (let i = tokens.length - 1; i >= 0; i -= 1) {
    if (REGION_FALLBACK_COORDS[tokens[i]]) {
      return REGION_FALLBACK_COORDS[tokens[i]];
    }
  }
  return SIDO_FALLBACK_COORDS[sido] || null;
}

function createPinElement(pin, active, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = `kakao-map-pin${active ? ' active' : ''}`;
  btn.setAttribute('aria-label', `${pin.region} 지역 모임 ${pin.groups.length}개 목록 열기`);

  const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  icon.setAttribute('viewBox', '0 0 24 24');
  icon.setAttribute('width', '15');
  icon.setAttribute('height', '15');
  icon.setAttribute('aria-hidden', 'true');
  icon.setAttribute('class', 'kakao-pin-cloud');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute(
    'd',
    'M7 18h10a4 4 0 0 0 .8-7.92A5.5 5.5 0 0 0 7.1 8.5 3.8 3.8 0 0 0 7 18Z'
  );
  path.setAttribute('fill', 'currentColor');
  icon.appendChild(path);

  const label = document.createElement('span');
  label.className = 'kakao-pin-label';
  label.textContent = pin.region;

  const badge = document.createElement('span');
  badge.className = 'pin-count';
  badge.textContent = String(pin.groups.length);

  btn.append(icon, label, badge);
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    onClick(pin.region, pin.groups, pin.key);
  });

  return btn;
}

export default function KakaoGroupMap({ groups = [], focusId = null, onSelectRegion }) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const overlaysRef = useRef([]);
  const onSelectRegionRef = useRef(onSelectRegion);
  onSelectRegionRef.current = onSelectRegion;

  const [status, setStatus] = useState('loading'); // loading | ready | error
  const [errorMsg, setErrorMsg] = useState('');

  // 동일 시/군/구 모임을 하나의 핀으로 집계 (핀 수 = 지역 수, 배지 수 = 모임 수)
  const regionPins = useMemo(() => {
    const grouped = new Map();
    groups.forEach((g) => {
      const meta = resolvePinMeta(g);
      if (!grouped.has(meta.key)) {
        grouped.set(meta.key, { ...meta, groups: [] });
      }
      grouped.get(meta.key).groups.push(g);
    });

    const pins = [];
    grouped.forEach((entry) => {
      const coords = resolvePinCoords(entry.label, entry.sido, entry.groups);
      if (!coords) return;
      pins.push({
        key: entry.key,
        region: entry.label,
        sido: entry.sido,
        groups: entry.groups,
        ...coords,
      });
    });
    return pins;
  }, [groups]);

  // 1) Kakao Maps SDK 로드 및 지도 인스턴스 1회 생성
  useEffect(() => {
    let cancelled = false;
    let resizeObserver;

    setStatus('loading');
    setErrorMsg('');

    loadKakaoMaps()
      .then((kakao) => {
        if (cancelled || !containerRef.current) return;

        const center = new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
        const map = new kakao.maps.Map(containerRef.current, {
          center,
          level: DEFAULT_LEVEL,
        });

        const zoomControl = new kakao.maps.ZoomControl();
        map.addControl(zoomControl, kakao.maps.ControlPosition.RIGHT);

        mapRef.current = map;
        setStatus('ready');

        if (typeof ResizeObserver !== 'undefined') {
          resizeObserver = new ResizeObserver(() => {
            map.relayout();
          });
          resizeObserver.observe(containerRef.current);
        }
      })
      .catch((err) => {
        if (cancelled) return;
        setStatus('error');
        setErrorMsg(err?.message || '지도를 불러오지 못했어요.');
      });

    return () => {
      cancelled = true;
      resizeObserver?.disconnect();
      overlaysRef.current.forEach((ov) => ov.setMap(null));
      overlaysRef.current = [];
      mapRef.current = null;
    };
  }, []);

  // 2) 지역 핀 오버레이 동기화
  useEffect(() => {
    const map = mapRef.current;
    const kakao = window.kakao;
    if (status !== 'ready' || !map || !kakao?.maps) return;

    overlaysRef.current.forEach((ov) => ov.setMap(null));
    overlaysRef.current = [];

    if (regionPins.length === 0) {
      map.setCenter(new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng));
      map.setLevel(DEFAULT_LEVEL);
      return;
    }

    const bounds = new kakao.maps.LatLngBounds();

    regionPins.forEach((pin) => {
      const pos = new kakao.maps.LatLng(pin.lat, pin.lng);
      bounds.extend(pos);

      const active = pin.groups.some((g) => g.id === focusId);
      const content = createPinElement(pin, active, (region, regionGroups, pinKey) => {
        onSelectRegionRef.current?.(region, regionGroups, pinKey);
      });

      const overlay = new kakao.maps.CustomOverlay({
        position: pos,
        content,
        yAnchor: 1,
        zIndex: active ? 10 : 5,
      });
      overlay.setMap(map);
      overlaysRef.current.push(overlay);
    });

    if (regionPins.length === 1) {
      map.setCenter(new kakao.maps.LatLng(regionPins[0].lat, regionPins[0].lng));
      map.setLevel(6);
    } else {
      map.setBounds(bounds, 56, 56, 72, 56);
    }
  }, [status, regionPins, focusId]);

  return (
    <div className="map kakao-map-wrap">
      <div
        ref={containerRef}
        className="kakao-map-canvas"
        aria-label="지역 계모임 Kakao 지도"
      />

      {status === 'loading' && (
        <div className="kakao-map-status" role="status">
          지도를 불러오는 중이에요...
        </div>
      )}

      {status === 'error' && (
        <div className="kakao-map-status error" role="alert">
          <strong>지도를 불러오지 못했어요.</strong>
          <p>{errorMsg}</p>
          <p className="muted mini">
            지도가 보이지 않아도 위 검색 필터와 <b>목록 보기</b> 버튼으로 모임을 찾을 수 있어요.
          </p>
        </div>
      )}

      <div className="map-hint">
        같은 지역은 하나의 핀으로 표시됩니다. 핀을 누르면 해당 지역의 모임 목록이 열립니다.
      </div>
    </div>
  );
}
