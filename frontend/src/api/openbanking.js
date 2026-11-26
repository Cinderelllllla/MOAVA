/* 오픈뱅킹(모의 원장) + CODEF 실계좌인증 API 래퍼
   - CODEF 컨트롤러: /api/openbanking/codef/**
   - 모의은행 컨트롤러: /api/openbanking/accounts/** */
import { apiFetch } from './client';

const CODEF_BASE = '/api/openbanking/codef';
const ACCOUNTS_BASE = '/api/openbanking/accounts';

/** 브라우저 File 객체를 CODEF 전송용 순수 Base64 문자열로 변환 */
export function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    if (!file) {
      resolve('');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || '');
      const commaIdx = result.indexOf(',');
      resolve(commaIdx >= 0 ? result.slice(commaIdx + 1) : result);
    };
    reader.onerror = () => reject(new Error('인증서 파일을 읽지 못했어요. 다시 선택해 주세요.'));
    reader.readAsDataURL(file);
  });
}

// ==================== 1. CODEF 실계좌인증 API ====================

export async function fetchSupportedBanks() {
  return apiFetch(`${CODEF_BASE}/banks`, { method: 'GET', cache: 'no-store' });
}

export async function fetchCodefConnections() {
  return apiFetch(`${CODEF_BASE}/connections`, { method: 'GET', cache: 'no-store' });
}

export async function connectBank(payload) {
  return apiFetch(`${CODEF_BASE}/connect`, {
    method: 'POST',
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function fetchCodefAccounts(payload) {
  return apiFetch(`${CODEF_BASE}/accounts`, {
    method: 'POST',
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function verifyRealAccount(payload) {
  return apiFetch(`${CODEF_BASE}/verify-account`, {
    method: 'POST',
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function fetchCodefTransactions(payload) {
  return apiFetch(`${CODEF_BASE}/transactions`, {
    method: 'POST',
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function disconnectBank(organization) {
  return apiFetch(`${CODEF_BASE}/connections/${encodeURIComponent(organization)}`, {
    method: 'DELETE',
    cache: 'no-store',
  });
}

// ==================== 2. 모의은행(KFTC 가상 원장) API ====================

export async function fetchOverview() {
  return apiFetch(`${ACCOUNTS_BASE}/overview`, { method: 'GET', cache: 'no-store' });
}

export async function fetchMyAccounts() {
  return apiFetch(ACCOUNTS_BASE, { method: 'GET', cache: 'no-store' });
}

export async function syncFromCodef(payload) {
  return apiFetch(`${ACCOUNTS_BASE}/sync`, {
    method: 'POST',
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function fetchAccountBalance(fintechUseNum) {
  return apiFetch(`${ACCOUNTS_BASE}/${encodeURIComponent(fintechUseNum)}/balance`, {
    method: 'GET',
    cache: 'no-store',
  });
}

export async function fetchAccountTransactions(fintechUseNum, params = {}) {
  const qs = new URLSearchParams();
  if (params.inquiryType) qs.set('inquiryType', params.inquiryType);
  if (params.fromDate) qs.set('fromDate', params.fromDate);
  if (params.toDate) qs.set('toDate', params.toDate);
  if (params.sortOrder) qs.set('sortOrder', params.sortOrder);
  if (params.limit) qs.set('limit', String(params.limit));
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  return apiFetch(`${ACCOUNTS_BASE}/${encodeURIComponent(fintechUseNum)}/transactions${suffix}`, {
    method: 'GET',
    cache: 'no-store',
  });
}

export async function updateLeaderFlag(fintechUseNum, leader) {
  return apiFetch(`${ACCOUNTS_BASE}/${encodeURIComponent(fintechUseNum)}/leader`, {
    method: 'PATCH',
    body: JSON.stringify({ leader: Boolean(leader) }),
    cache: 'no-store',
  });
}

export async function depositDemoFunds(fintechUseNum, payload) {
  return apiFetch(`${ACCOUNTS_BASE}/${encodeURIComponent(fintechUseNum)}/deposit-demo`, {
    method: 'POST',
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function transferToLeader(payload) {
  return apiFetch(`${ACCOUNTS_BASE}/transfer`, {
    method: 'POST',
    body: JSON.stringify(payload),
    cache: 'no-store',
  });
}

export async function fetchLeaderTargets() {
  return apiFetch(`${ACCOUNTS_BASE}/leader-targets`, { method: 'GET', cache: 'no-store' });
}
