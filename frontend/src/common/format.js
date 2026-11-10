/* 포맷·마스킹 유틸 — 화면 단위 만 원(won), API·DB 단위 원(krw) */
export const won = n => Number(n || 0).toLocaleString('ko-KR') + '만 원';
export const krw = man => Math.round(Number(man || 0) * 10000).toLocaleString('ko-KR') + '원';
export const signed = (n, fmt) => (n > 0 ? '+' : '') + fmt(n);
export function maskName(name) { name = String(name || '회원'); if (name.length === 1) return name + '○'; if (name.length === 2) return name[0] + '○'; return name[0] + '○'.repeat(name.length - 2) + name.slice(-1); }
export const maskAccount = a => { a = String(a || ''); return a ? a.replace(/.(?=.{4})/g, '*') : '미등록'; };
export const MONTHS = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];
export const todayStr = () => new Date().toISOString().slice(0, 10);
export const VERIFY_LABEL = { PENDING: '인증 대기 · 관리자 확인 전', VERIFIED: '소유 확인 완료 (관리자 처리)', REVERIFY_REQUIRED: '재인증 필요 · 송금 보류 권고' };
