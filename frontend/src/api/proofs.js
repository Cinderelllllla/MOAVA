/* F22 F23 F24 F26 — 납입 증빙(승인·보완 요청·재제출), 참조번호, 미납 감지, 채팅 */
import * as D from './mock/db';
import { apiFetch, apiFetchBlob, toUploadFile } from './client';
const { db, save, fail } = D;

/* 아래 3개는 목업 증빙(관리자·운영 홈 목업 화면용). 서버 증빙은 fetchProofsFromServer 를 쓴다. */
export const listProofs = groupId => db.proofs.filter(x => x.groupId === groupId);
export const allProofs = () => db.proofs;
export const overdueCases = () => db.overdueCases;

const STATUS_FROM_BE = {
  PENDING: '확인 대기',
  CONFIRMED: '납입 확인',
  REVISION: '보완 요청',
};

// 백엔드(YYYYMM, 원 단위) 응답을 프론트엔드 표시 형식으로 변환. 값이 없으면 지어내지 않고 '-' 로 둔다.
export async function fetchProofsFromServer(groupId) {
  const list = await apiFetch(`/api/v1/groups/${groupId}/payment-proofs`, {
    method: 'GET',
  });
  if (!Array.isArray(list)) {
    throw new Error('증빙 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
  }

  const serverProofs = list.map((item) => {
    const rawMonth = String(item.payMonth || '');
    const period = rawMonth.length === 6 ? `${rawMonth.slice(0, 4)}-${rawMonth.slice(4, 6)}` : (rawMonth || '-');
    return {
      id: item.paymentProofId ?? item.proofId ?? item.id,
      groupId,
      memberId: item.memberId,
      memberName: item.memberName || '-',
      amount: Number(item.amount || 0) / 10000,   // 서버는 원 단위 → 화면은 만 원
      date: item.payDate ? String(item.payDate).slice(0, 10) : '-',
      period,
      round: item.roundNo ?? item.round,
      fileCount: Number(item.fileCount || 0),
      images: [],
      status: STATUS_FROM_BE[item.status] || '확인 대기',
      reviewMemo: item.reviewMemo || '',
      resubmitCount: item.resubmitCount || 0,
      ref: item.refNo || null,
      refHistory: [],
      createdAt: item.createdAt ? String(item.createdAt).slice(0, 16).replace('T', ' ') : '-',
    };
  });

  return serverProofs;
}

export async function fetchProofDetailFromServer(groupId, proofId) {
  return apiFetch(`/api/v1/groups/${groupId}/payment-proofs/${proofId}`, {
    method: 'GET',
  });
}

export async function fetchProofFileFromServer(groupId, proofId, fileId) {
  return apiFetchBlob(`/api/v1/groups/${groupId}/payment-proofs/${proofId}/files/${fileId}`);
}

export async function reviewProofOnServer(groupId, proofId, decision, reviewMemo = '') {
  return apiFetch(`/api/v1/groups/${groupId}/payment-proofs/${proofId}/review`, {
    method: 'PATCH',
    body: JSON.stringify({ decision, reviewMemo }),
  });
}

export async function registerProofOnServer(groupId, group, fields, images) {
  const formData = new FormData();
  images.slice(0, 5).forEach((image, index) => {
    formData.append('files', toUploadFile(image, index));
  });
  formData.append('payMonth', String(fields.period).replace('-', ''));
  formData.append('roundNo', String(fields.round));
  formData.append('amount', String(group.amount));   // 서버 모임 금액(원 단위) 그대로
  formData.append('payDate', fields.date);

  return apiFetch(`/api/v1/groups/${groupId}/payment-proofs`, {
    method: 'POST',
    body: formData,
  });
}

export async function resubmitProofOnServer(groupId, proofId, payDate, images) {
  const formData = new FormData();
  images.slice(0, 5).forEach((image, index) => {
    formData.append('files', toUploadFile(image, index));
  });
  formData.append('payDate', payDate);

  return apiFetch(`/api/v1/groups/${groupId}/payment-proofs/${proofId}`, {
    method: 'PUT',
    body: formData,
  });
}

/* ---- 채팅 (F34) & 미납 자동 감지 — 서버 API 없음, 목업 ---- */
export const messages = (groupId) => db.messages.filter((x) => x.groupId === groupId);
export async function sendChat(groupId, text) {
  const g = D.group(groupId);
  if (!text?.trim() || !D.joined(g)) fail('전송할 수 없습니다.');
  if (g.restrictions?.[D.me()]) fail('미납 제재로 채팅 전송이 제한되었습니다.');
  db.messages.push({ id: D.uid(), groupId, memberId: D.me(), text: text.trim(), time: new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) });
  save();
}

export async function detectOverdue() {
  const now = new Date(), period = D.currentPeriod();
  let added = 0;
  db.groups.filter((g) => !D.isEnded(g) && g.members.length > 1).forEach((g) => {
    const due = new Date(now.getFullYear(), now.getMonth(), Math.min(g.payDay || 5, 28)), days = Math.floor((now - due) / 86400000);
    if (days < 5) return;
    g.members.forEach((id) => {
      if (D.paidThisPeriod(g, id, period) || db.overdueCases.some((c) => c.groupId === g.id && c.memberId === id && c.period === period)) return;
      db.overdueCases.push({ id: D.uid(), groupId: g.id, memberId: id, period, days, detectedAt: D.nowText(), status: 'DETECTED', actions: [] });
      added++;
    });
  });
  if (added) {
    D.noticeAdmin('미납 5일차 감지', `${added}건의 미납이 감지되었습니다. 미납 관리에서 조치해 주세요.`);
    save();
  }
  return added;
}
