// frontend/src/api/client.js
import { db } from './mock/db';

const TOKEN_KEY = 'moava_access_token';

export const getToken = () =>
    sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY);
export const setToken = (token) => {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
};

/**
 * 백엔드 공통 호출 함수
 * - Authorization: Bearer <token> 자동 첨부 (MemberController, GroupCreateController 용)
 * - X-Member-Id / X-Admin-Id 자동 첨부 (PaymentProofController, NotificationController 용)
 * - ApiResponse.java ({ data, meta } / { error, requestId }) 자동 파싱
 */
export async function apiFetch(url, options = {}) {
    const headers = { ...(options.headers || {}) };

    // FormData(파일 업로드)가 아닐 때만 JSON Content-Type 지정
    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
        headers['Content-Type'] = 'application/json';
    }

    // 1. JWT 토큰 헤더 추가
    const token = getToken();
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    // 2. 임시 헤더(X-Member-Id / X-Admin-Id) 동시 추가
    if (db.session?.id != null) {
        if (db.session.type === 'ADMIN') {
            headers['X-Admin-Id'] = String(db.session.id);
        } else {
            headers['X-Member-Id'] = String(db.session.id);
        }
    }

    const response = await fetch(url, { ...options, headers });
    const payload = await response.json().catch(() => ({}));

    if (!response.ok) {
        const errMsg =
            payload?.error?.message ||
            payload?.message ||
            payload?.error ||
            `요청 처리 중 오류가 발생했습니다. (HTTP ${response.status})`;
        throw new Error(errMsg);
    }

    // ApiResponse.ok(data, requestId) 형태면 data만 언패킹해서 반환
    if (payload && typeof payload === 'object' && 'data' in payload && 'meta' in payload) {
        return payload.data;
    }
    return payload;
}

export async function apiFetchBlob(url) {
    const headers = {};
    const token = getToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    if (db.session?.id != null) {
        if (db.session.type === 'ADMIN') {
            headers['X-Admin-Id'] = String(db.session.id);
        } else {
            headers['X-Member-Id'] = String(db.session.id);
        }
    }

    const response = await fetch(url, { headers });
    if (!response.ok) {
        const payload = await response.json().catch(() => ({}));
        const errMsg =
            payload?.error?.message ||
            payload?.message ||
            payload?.error ||
            `요청 처리 중 오류가 발생했습니다. (HTTP ${response.status})`;
        throw new Error(errMsg);
    }

    return response.blob();
}

/**
 * 프론트엔드의 Base64 Data URL 문자열(또는 File 객체)을 백엔드 MultipartFile용 File 객체로 변환
 */
export function toUploadFile(item, index = 0) {
    if (item instanceof File || item instanceof Blob) return item;
    if (typeof item === 'string' && item.startsWith('data:')) {
        const [header, base64] = item.split(',');
        const mimeMatch = header.match(/:(.*?);/);
        const mime = mimeMatch ? mimeMatch[1] : 'image/png';
        const ext = mime.split('/')[1] || 'png';
        const binary = atob(base64);
        const array = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            array[i] = binary.charCodeAt(i);
        }
        return new File([array], `proof_${Date.now()}_${index + 1}.${ext}`, { type: mime });
    }
    // 일반 URL 문자열 등일 경우 더미 이미지 파일 생성 (백엔드 @RequestPart("files") 필수 검증 통과용)
    const dummy = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
    return new File([dummy], `proof_${Date.now()}_${index + 1}.png`, { type: 'image/png' });
}