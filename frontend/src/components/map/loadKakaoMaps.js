/* Kakao Maps JavaScript SDK 동적 로더 (F13) */

let kakaoPromise = null;
const SDK_SCRIPT_ID = 'moava-kakao-maps-sdk';

export function loadKakaoMaps() {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('브라우저 환경에서만 지도를 불러올 수 있어요.'));
  }

  if (window.kakao?.maps?.Map) {
    return Promise.resolve(window.kakao);
  }

  const appKey = String(import.meta.env.VITE_KAKAO_MAP_APP_KEY || '').trim();
  if (!appKey) {
    return Promise.reject(
      new Error('Kakao Maps JavaScript 키(VITE_KAKAO_MAP_APP_KEY)가 설정되지 않았어요.')
    );
  }

  if (kakaoPromise) {
    return kakaoPromise;
  }

  kakaoPromise = new Promise((resolve, reject) => {
    const timeoutId = window.setTimeout(() => {
      kakaoPromise = null;
      reject(new Error('Kakao Maps SDK 응답 시간이 초과되었어요. 네트워크와 도메인 설정을 확인해 주세요.'));
    }, 10000);

    const finish = (kakao) => {
      window.clearTimeout(timeoutId);
      resolve(kakao);
    };

    const fail = (message) => {
      window.clearTimeout(timeoutId);
      kakaoPromise = null;
      reject(new Error(message));
    };

    const initMaps = () => {
      if (!window.kakao?.maps?.load) {
        fail('Kakao Maps SDK 초기화에 실패했어요. JavaScript 키와 도메인 등록(http://localhost:3000), 카카오맵 활성화 여부를 확인해 주세요.');
        return;
      }
      window.kakao.maps.load(() => {
        if (window.kakao?.maps?.Map) {
          finish(window.kakao);
        } else {
          fail('Kakao Maps 객체를 불러오지 못했어요.');
        }
      });
    };

    const existing = document.getElementById(SDK_SCRIPT_ID);
    if (existing) {
      if (window.kakao?.maps) {
        initMaps();
        return;
      }
      existing.remove();
    }

    const script = document.createElement('script');
    script.id = SDK_SCRIPT_ID;
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
    script.onload = initMaps;
    script.onerror = () => {
      script.remove();
      fail('Kakao Maps 스크립트를 불러오지 못했어요. Kakao Developers에서 http://localhost:3000 도메인 등록과 카카오맵 활성화를 확인해 주세요.');
    };

    document.head.appendChild(script);
  });

  return kakaoPromise;
}
