/* F13 지역 계모임 지도 — Kakao Maps 인터랙티브 지도 + 읍/면/동(또는 시/군/구) 구름 핀 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { loadKakaoMaps } from './loadKakaoMaps';
import {
  formatRegionLabel,
  getRegionCoords,
  parseRegionFromServer,
  shortSidoName,
} from '../../common/regions';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 }; // 서울 시청
const DEFAULT_LEVEL = 7;

const METROPOLITAN_SIDOS = new Set([
  '서울특별시',
  '부산광역시',
  '대구광역시',
  '인천광역시',
  '광주광역시',
  '대전광역시',
  '울산광역시',
  '세종특별자치시',
]);

const regionGeocodeCache = new Map();

function cleanFilterPart(value) {
  const s = String(value || '').trim();
  return !s || s === '전체' ? '' : s;
}

function resolveRegionZoomLevel(sido, sigungu, dong) {
  if (dong) return 5;
  if (sigungu) return 7;
  if (sido) return METROPOLITAN_SIDOS.has(sido) ? 8 : 10;
  return DEFAULT_LEVEL;
}

function resolveRegionCenter(kakao, sido, sigungu, dong) {
  const fallback = getRegionCoords(sido, sigungu, dong) || DEFAULT_CENTER;
  const query = formatRegionLabel({ sido, sigungu, dong });
  if (!query) return Promise.resolve(DEFAULT_CENTER);

  if (regionGeocodeCache.has(query)) {
    return Promise.resolve(regionGeocodeCache.get(query));
  }

  const Geocoder = kakao?.maps?.services?.Geocoder;
  const okStatus = kakao?.maps?.services?.Status?.OK;
  if (!Geocoder || !okStatus) {
    return Promise.resolve(fallback);
  }

  return new Promise((resolve) => {
    try {
      const geocoder = new Geocoder();
      geocoder.addressSearch(query, (result, status) => {
        if (status === okStatus && Array.isArray(result) && result.length > 0) {
          const lat = Number(result[0].y);
          const lng = Number(result[0].x);
          if (Number.isFinite(lat) && Number.isFinite(lng)) {
            const coords = { lat, lng };
            regionGeocodeCache.set(query, coords);
            resolve(coords);
            return;
          }
        }
        resolve(fallback);
      });
    } catch {
      resolve(fallback);
    }
  });
}

export function normalizeSido(sido) {
  return shortSidoName(sido);
}

/**
 * 모임을 지도 핀 단위로 분류
 * - 읍/면/동 정보가 있으면 동 단위 핀 (key: "시도::시군구::동", label: "합정동", title: "마포구 합정동")
 * - 구형 데이터처럼 동 정보가 없으면 시/군/구 단위 핀 (key: "시도::시군구", label: "마포구", title: "마포구")
 */
export function resolvePinMeta(group) {
  const sidoRaw =
    group.regionSido?.trim() ||
    String(group.region || '').trim().split(/\s+/)[0] ||
    '';
  const sigunguRaw =
    group.regionSigungu?.trim() ||
    String(group.region || '').trim().split(/\s+/).slice(1).join(' ');
  const parsed = parseRegionFromServer(sidoRaw, sigunguRaw, group.regionDong || '');

  const sido = parsed.sido || sidoRaw;
  const sigungu = parsed.sigungu || sigunguRaw || '기타';
  const dong = parsed.dong || '';
  const sidoShort = shortSidoName(sido);

  if (dong) {
    const key = `${sido}::${sigungu}::${dong}`;
    const title =
      sigungu && sigungu !== '세종시'
        ? `${sigungu} ${dong}`
        : `${sidoShort} ${dong}`.trim();
    return { key, sido, sigungu, dong, label: dong, title, level: 'dong' };
  }

  const key = sido ? `${sido}::${sigungu}` : sigungu;
  const label = sigungu || sidoShort || '기타';
  return { key, sido, sigungu, dong: '', label, title: label, level: 'sigungu' };
}

function hasValidCoords(g) {
  if (g.lat == null || g.lng == null || g.lat === '' || g.lng === '') {
    return false;
  }
  const lat = Number(g.lat);
  const lng = Number(g.lng);
  return Number.isFinite(lat) && Number.isFinite(lng) && !(lat === 0 && lng === 0);
}

function resolvePinCoords(meta, regionGroups) {
  const valid = regionGroups.filter(hasValidCoords);
  if (valid.length > 0) {
    const lat = valid.reduce((sum, g) => sum + Number(g.lat), 0) / valid.length;
    const lng = valid.reduce((sum, g) => sum + Number(g.lng), 0) / valid.length;
    return { lat, lng };
  }
  return getRegionCoords(meta.sido, meta.sigungu, meta.dong);
}

function createPinElement(pin, active, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = `kakao-map-pin${active ? ' active' : ''}`;
  btn.setAttribute('aria-label', `${pin.title} 지역 모임 ${pin.groups.length}개 목록 열기`);
  btn.title = `${pin.title} (${pin.groups.length}개)`;

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
  label.textContent = pin.label;

  const badge = document.createElement('span');
  badge.className = 'pin-count';
  badge.textContent = String(pin.groups.length);

  btn.append(icon, label, badge);
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    onClick(pin.title, pin.groups, pin.key);
  });

  return btn;
}

export default function KakaoGroupMap({
  groups = [],
  focusId = null,
  regionFilter = null,
  onSelectRegion,
}) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const overlaysRef = useRef([]); // Array<{ overlay, button, pin }>
  const focusIdRef = useRef(focusId);
  focusIdRef.current = focusId;
  const onSelectRegionRef = useRef(onSelectRegion);
  onSelectRegionRef.current = onSelectRegion;

  const [status, setStatus] = useState('loading'); // loading | ready | error
  const [errorMsg, setErrorMsg] = useState('');

  const selectedSido = cleanFilterPart(regionFilter?.sido);
  const selectedSigungu = cleanFilterPart(regionFilter?.sigungu);
  const selectedDong = cleanFilterPart(regionFilter?.dong);

  // 동일 읍/면/동(또는 시/군/구) 모임을 하나의 핀으로 집계 (핀 수 = 지역 수, 배지 수 = 모임 수)
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
      const coords = resolvePinCoords(entry, entry.groups);
      if (!coords) return;
      pins.push({
        key: entry.key,
        label: entry.label,
        title: entry.title,
        region: entry.title,
        sido: entry.sido,
        sigungu: entry.sigungu,
        dong: entry.dong,
        level: entry.level,
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
      overlaysRef.current.forEach(({ overlay }) => overlay.setMap(null));
      overlaysRef.current = [];
      mapRef.current = null;
    };
  }, []);

  // 2) 지역 핀 오버레이 동기화 및 선택 지역/핀 기준 지도 뷰포트 이동
  useEffect(() => {
    const map = mapRef.current;
    const kakao = window.kakao;
    if (status !== 'ready' || !map || !kakao?.maps) return undefined;

    let cancelled = false;

    overlaysRef.current.forEach(({ overlay }) => overlay.setMap(null));
    overlaysRef.current = [];

    if (regionPins.length === 0) {
      if (selectedSido) {
        const targetLevel = resolveRegionZoomLevel(selectedSido, selectedSigungu, selectedDong);
        resolveRegionCenter(kakao, selectedSido, selectedSigungu, selectedDong).then((coords) => {
          if (cancelled || mapRef.current !== map) return;
          map.setLevel(targetLevel);
          map.setCenter(new kakao.maps.LatLng(coords.lat, coords.lng));
        });
      } else {
        map.setLevel(DEFAULT_LEVEL);
        map.setCenter(new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng));
      }
      return () => {
        cancelled = true;
      };
    }

    const bounds = new kakao.maps.LatLngBounds();
    const currentFocusId = focusIdRef.current;

    regionPins.forEach((pin) => {
      const pos = new kakao.maps.LatLng(pin.lat, pin.lng);
      bounds.extend(pos);

      const active = pin.groups.some((g) => g.id === currentFocusId);
      const button = createPinElement(pin, active, (regionTitle, regionGroups, pinKey) => {
        onSelectRegionRef.current?.(regionTitle, regionGroups, pinKey);
      });

      const overlay = new kakao.maps.CustomOverlay({
        position: pos,
        content: button,
        yAnchor: 1,
        zIndex: active ? 10 : 5,
      });
      overlay.setMap(map);
      overlaysRef.current.push({ overlay, button, pin });
    });

    if (regionPins.length === 1) {
      map.setLevel(regionPins[0].level === 'dong' || selectedDong ? 5 : 6);
      map.setCenter(new kakao.maps.LatLng(regionPins[0].lat, regionPins[0].lng));
    } else {
      map.setBounds(bounds, 56, 56, 72, 56);
    }

    return () => {
      cancelled = true;
    };
  }, [status, regionPins, selectedSido, selectedSigungu, selectedDong]);

  // 3) 선택 모임(focusId) 변경 시에는 지도 줌·위치를 유지한 채 활성 핀 스타일만 갱신
  useEffect(() => {
    if (status !== 'ready') return;
    overlaysRef.current.forEach(({ overlay, button, pin }) => {
      const active = pin.groups.some((g) => g.id === focusId);
      button.classList.toggle('active', active);
      overlay.setZIndex(active ? 10 : 5);
    });
  }, [status, focusId]);

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
        같은 동(또는 시/군/구)의 모임은 하나의 핀으로 표시됩니다. 핀을 누르면 해당 지역의 모임 목록이 열립니다.
      </div>
    </div>
  );
}
