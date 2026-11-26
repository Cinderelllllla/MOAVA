/* F13 지역 계모임 지도 — Kakao Maps 인터랙티브 지도 + 시/군/구 구름 핀 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { loadKakaoMaps } from './loadKakaoMaps';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 }; // 서울 시청
const DEFAULT_LEVEL = 7;

/* 좌표(LAT/LNG)가 아직 없는 목업·기존 데이터용 대표 시/군/구 폴백 좌표 */
const REGION_FALLBACK_COORDS = {
  마포구: { lat: 37.5663, lng: 126.9018 },
  서대문구: { lat: 37.5791, lng: 126.9368 },
  강남구: { lat: 37.5172, lng: 127.0473 },
  성동구: { lat: 37.5634, lng: 127.0369 },
  송파구: { lat: 37.5145, lng: 127.1059 },
  영등포구: { lat: 37.5264, lng: 126.8963 },
  용산구: { lat: 37.5326, lng: 126.9905 },
  종로구: { lat: 37.573, lng: 126.9794 },
  중구: { lat: 37.5641, lng: 126.9979 },
  관악구: { lat: 37.4784, lng: 126.9516 },
};

function resolveRegionKey(group) {
  if (group.regionSigungu?.trim()) {
    return group.regionSigungu.trim();
  }
  const tokens = String(group.region || '').trim().split(/\s+/).filter(Boolean);
  return tokens.length ? tokens[tokens.length - 1] : '기타';
}

function resolvePinCoords(regionKey, regionGroups) {
  const valid = regionGroups.filter(
    (g) => Number.isFinite(Number(g.lat)) && Number.isFinite(Number(g.lng))
  );
  if (valid.length > 0) {
    const lat = valid.reduce((sum, g) => sum + Number(g.lat), 0) / valid.length;
    const lng = valid.reduce((sum, g) => sum + Number(g.lng), 0) / valid.length;
    return { lat, lng };
  }
  return REGION_FALLBACK_COORDS[regionKey] || null;
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
    onClick(pin.region, pin.groups);
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
      const key = resolveRegionKey(g);
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key).push(g);
    });

    const pins = [];
    grouped.forEach((regionGroups, region) => {
      const coords = resolvePinCoords(region, regionGroups);
      if (!coords) return;
      pins.push({ region, groups: regionGroups, ...coords });
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
      const content = createPinElement(pin, active, (region, regionGroups) => {
        onSelectRegionRef.current?.(region, regionGroups);
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
