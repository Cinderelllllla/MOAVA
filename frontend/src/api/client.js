// frontend/src/api/client.js — 백엔드 공통 호출

/** 로그인 토큰(JWT)은 이 키 하나, sessionStorage 에만 둔다 (브라우저 탭을 닫으면 로그아웃). */
export const TOKEN_KEY = 'moava_access_token';
export const getToken = () => sessionStorage.getItem(TOKEN_KEY);

/** JWT 안의 회원 번호(sub = MEMBER_ID). 토큰이 없거나 읽을 수 없으면 null */
export function tokenMemberId(token = getToken()) {
    try {
        const payload = token?.split('.')[1]?.replace(/-/g, '+').replace(/_/g, '/');
        if (!payload) return null;
        const claims = JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, '=')));
        const id = Number(claims.sub);
        return Number.isSafeInteger(id) && id > 0 ? id : null;
    } catch {
        return null;
    }
}

/** 로그인한 회원 정보(이름·이메일·지역) — 토큰과 같은 sessionStorage 에 둔다. 전화번호는 저장하지 않는다. */
const MEMBER_KEY = 'moava_member';
export function getServerMember() {
    try { return JSON.parse(sessionStorage.getItem(MEMBER_KEY)); } catch { return null; }
}
export function setServerMember(user) {
    if (!user) { sessionStorage.removeItem(MEMBER_KEY); return; }
    const { name = '', email = '', regionSido = '', regionSigungu = '', status = 'ACTIVE' } = user;
    sessionStorage.setItem(MEMBER_KEY, JSON.stringify({ name, email, regionSido, regionSigungu, status }));
}

/** 토큰이 만료·무효(401)일 때 할 일 — auth.js 가 "로그아웃 처리"를 등록한다. */
let unauthorizedHandler = null;
export const onUnauthorized = fn => { unauthorizedHandler = fn; };

const hasKorean = s => typeof s === 'string' && /[가-힣]/.test(s);

/** 서버가 준 한국어 안내가 있으면 그대로, 없으면 상태 코드에 맞는 쉬운 안내 */
export function friendlyError(status, payload) {
    const serverMessage = payload?.error?.message ?? payload?.message;
    if (hasKorean(serverMessage)) return serverMessage;
    if (status === 401) return '로그인이 만료되었어요. 다시 로그인해 주세요.';
    if (status === 403) return '이 기능을 사용할 권한이 없어요.';
    if (status === 404) return '요청한 정보를 찾지 못했어요.';
    if (status >= 500) return '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.';
    return '요청을 처리하지 못했어요. 입력값을 확인해 주세요.';
}

export const NETWORK_ERROR = '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.';

async function send(url, options) {
    const headers = { ...(options.headers || {}) };
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    let response;
    try {
        response = await fetch(url, { ...options, headers });
    } catch {
        throw new Error(NETWORK_ERROR);   // 서버 꺼짐·네트워크 끊김
    }
    if (!response.ok) {
        const payload = await response.json().catch(() => ({}));
        if (response.status === 401 && token && unauthorizedHandler) unauthorizedHandler();
        const error = new Error(friendlyError(response.status, payload));
        error.status = response.status;   // 화면에서 404 등을 구분할 때 사용
        throw error;
    }
    return response;
}

/**
 * 백엔드 공통 호출 함수
 * - Authorization: Bearer <token> 자동 첨부
 * - ApiResponse.java ({ data, meta } / { error, requestId }) 자동 파싱 → data 만 돌려줌
 * - 오류는 사용자에게 보여줄 수 있는 한국어 문장으로 throw
 */
export async function apiFetch(url, options = {}) {
    const headers = { ...(options.headers || {}) };
    // FormData(파일 업로드)가 아닐 때만 JSON Content-Type 지정
    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
        headers['Content-Type'] = 'application/json';
    }
    const response = await send(url, { ...options, headers });
    const payload = await response.json().catch(() => ({}));
    // ApiResponse.ok(data, requestId) 형태면 data만 언패킹해서 반환
    if (payload && typeof payload === 'object' && 'data' in payload && 'meta' in payload) {
        return payload.data;
    }
    return payload;
}

export async function apiFetchBlob(url) {
    const response = await send(url, {});
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
    // 파일로 바꿀 수 없는 값이면 가짜 파일을 만들어 보내지 않고 다시 선택하도록 안내한다
    throw new Error('첨부 파일을 읽지 못했어요. 파일을 다시 선택해 주세요.');
}