/* S12 납입 증빙 — 서버에서 모임장 소유 모임과 증빙을 조회·심사 */
import { useEffect, useState } from 'react';
import { useApp } from '../common/store';
import { useInitialGroupId } from '../common/useInitialGroupId';
import { groups, proofs } from '../api';
import { getAccessTokenMemberId } from '../api/auth';
import { PageHead, Card, Empty, Field, Input } from '../components/ui';
import { ProofAttachment } from '../components/shared';
import { krw, todayStr, won } from '../common/format';

const STATUS_FROM_BE = {
  PENDING: '확인 대기',
  CONFIRMED: '납입 확인',
  REVISION: '보완 요청',
};
const ACCEPT = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
const currentPeriod = () => {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
};
const periodOptions = (group) => {
  const now = new Date();
  return Array.from({ length: Math.max(2, Math.min(group.months || 6, 36)) }, (_, index) => {
    const date = new Date(now.getFullYear(), now.getMonth() - index, 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  });
};

export default function Evidence() {
  const { run, toast } = useApp();
  const [serverGroups, setServerGroups] = useState([]);
  const pickGroup = useInitialGroupId();
  const [selectedGroupId, setSelectedGroupId] = useState('');
  const [groupsLoading, setGroupsLoading] = useState(true);
  const [groupsError, setGroupsError] = useState('');
  const [groupsRefresh, setGroupsRefresh] = useState(0);
  const [items, setItems] = useState([]);
  const [proofsLoading, setProofsLoading] = useState(false);
  const [proofsError, setProofsError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [form, setForm] = useState({ period: currentPeriod(), round: 1, date: todayStr() });
  const [files, setFiles] = useState([]);
  const [redo, setRedo] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setGroupsLoading(true);
    setGroupsError('');

    groups.fetchMyGroups()
      .then((result) => {
        if (cancelled) return;
        setServerGroups(result);
        // 알림에서 들어온 경우(?groupId=) 첫 로드에만 그 모임을 선택
        setSelectedGroupId(pickGroup(result));
      })
      .catch((error) => {
        if (!cancelled) setGroupsError(error.message || '모임 목록을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!cancelled) setGroupsLoading(false);
      });

    return () => { cancelled = true; };
  }, [groupsRefresh, pickGroup]);

  const selectedGroup = serverGroups.find(
    (group) => String(group.groupId) === String(selectedGroupId)
  );
  const isOwner = selectedGroup?.role === 'OWNER';   // 승인·보완 요청은 모임장만 (서버도 403으로 막음)
  const amountInManwon = Number(selectedGroup?.amount || 0) / 10000;

  useEffect(() => {
    if (!selectedGroup) {
      setItems([]);
      setProofsError('');
      setProofsLoading(false);
      return undefined;
    }

    let cancelled = false;
    const objectUrls = [];
    setItems([]);
    setProofsError('');
    setProofsLoading(true);

    async function loadProofs() {
      try {
        const list = await proofs.fetchProofsFromServer(selectedGroup.groupId);
        const detailed = await Promise.all(list.map(async (proof) => {
          const detail = await proofs.fetchProofDetailFromServer(selectedGroup.groupId, proof.id);
          const files = await Promise.all((detail.files || []).map(async (file) => {
            const blob = await proofs.fetchProofFileFromServer(
              selectedGroup.groupId,
              proof.id,
              file.fileId
            );
            const url = URL.createObjectURL(blob);
            if (cancelled) {
              URL.revokeObjectURL(url);
              return { ...file, url: '' };
            }
            objectUrls.push(url);
            return { ...file, url };
          }));

          return {
            ...proof,
            memberName: detail.memberName || proof.memberName,
            reviewMemo: detail.reviewMemo || proof.reviewMemo,
            resubmitCount: detail.resubmitCount ?? proof.resubmitCount,
            status: STATUS_FROM_BE[detail.status] || detail.status || proof.status,
            files,
          };
        }));

        if (!cancelled) setItems(detailed.reverse());
      } catch (error) {
        objectUrls.forEach((url) => URL.revokeObjectURL(url));
        objectUrls.length = 0;
        if (!cancelled) {
          setProofsError(error.message || '증빙 목록을 불러오지 못했습니다.');
        }
      } finally {
        if (!cancelled) setProofsLoading(false);
      }
    }

    void loadProofs();
    return () => {
      cancelled = true;
      objectUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [selectedGroup?.groupId, refresh]);

  const review = (proof, decision, reviewMemo = '') => run(async () => {
    await proofs.reviewProofOnServer(
      selectedGroup.groupId,
      proof.id,
      decision,
      reviewMemo
    );
    setRefresh((current) => current + 1);
  }, decision === 'CONFIRMED' ? '납입을 승인했어요.' : '보완 요청을 보냈어요.');

  const requestRevision = (proof) => {
    const memo = prompt('보완 요청 사유를 입력해 주세요. (예: 입금자명 불일치, 금액 식별 불가)');
    if (memo?.trim()) void review(proof, 'REVISION', memo.trim());
  };

  const selectGroup = (groupId) => {
    setSelectedGroupId(groupId);
    setForm({ period: currentPeriod(), round: 1, date: todayStr() });
    setFiles([]);
    setRedo(null);
  };

  const pickFiles = async (event) => {
    const selected = [...event.target.files];
    event.target.value = '';
    if (!selected.length) return;
    if (selected.length + files.length > 5) {
      toast('증빙은 최대 5개까지 선택할 수 있어요.');
      return;
    }
    if (selected.some((file) => !ACCEPT.includes(file.type) || file.size > 1024 * 1024)) {
      toast('JPG, PNG, WebP, PDF만 파일당 1MB 이하로 올려 주세요.');
      return;
    }

    try {
      const data = await Promise.all(selected.map((file) => new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(new Error('증빙 파일을 읽지 못했습니다.'));
        reader.readAsDataURL(file);
      })));
      if (data.some((item) => files.includes(item)) || new Set(data).size !== data.length) {
        toast('동일한 증빙 파일은 중복으로 추가할 수 없습니다.');
        return;
      }
      setFiles((current) => [...current, ...data]);
    } catch (error) {
      toast(error.message);
    }
  };

  const submitProof = async (event) => {
    event.preventDefault();
    const result = await run(
      () => redo
        ? proofs.resubmitProofOnServer(selectedGroup.groupId, redo.id, form.date, files)
        : proofs.registerProofOnServer(selectedGroup.groupId, selectedGroup, form, files),
      redo ? '보완 증빙을 재제출했어요.' : '증빙이 등록됐어요.'
    );
    if (result.ok) {
      setFiles([]);
      setRedo(null);
      setRefresh((current) => current + 1);
    }
  };

  return <>
    <PageHead
      kicker="PAYMENT EVIDENCE"
      title="납입 증빙 · 확인"
      sub="모임장인 모임의 납입 증빙과 첨부 파일을 확인하고 심사합니다."
    />
    <Card>
      {groupsLoading ? <p className="muted">모임 목록을 불러오는 중입니다.</p>
        : groupsError ? <div className="warning" role="alert">
          {groupsError}{' '}
          <button type="button" className="btn small soft" onClick={() => setGroupsRefresh((current) => current + 1)}>
            다시 시도
          </button>
        </div>
          : serverGroups.length === 0 ? <Empty>모임장으로 생성한 모임이 없습니다.</Empty>
            : <>
              <div className="field">
                <label htmlFor="evidence-group">모임 선택</label>
                <select
                  id="evidence-group"
                  value={selectedGroupId}
                  onChange={(event) => selectGroup(event.target.value)}
                >
                  {serverGroups.map((group) => (
                    <option key={group.groupId} value={group.groupId}>{group.title}</option>
                  ))}
                </select>
              </div>
              <form className="section" onSubmit={submitProof}>
                {redo && <p className="warning mini">
                  보완 재제출: {redo.period}월분 {redo.round}회차 · 보완 사유 "{redo.reviewMemo}" —
                  귀속 월·회차·약정액은 변경할 수 없습니다.
                </p>}
                <div className="form-grid">
                  <Field label={redo ? '납입 귀속 월 (변경 불가)' : '납입 귀속 월'}>
                    <select
                      value={redo ? redo.period : form.period}
                      disabled={!!redo}
                      onChange={(event) => setForm((current) => ({ ...current, period: event.target.value }))}
                    >
                      {(redo ? [redo.period] : periodOptions(selectedGroup)).map((period) => (
                        <option key={period} value={period}>{period.replace('-', '년 ')}월분</option>
                      ))}
                    </select>
                  </Field>
                  <Input
                    label={redo ? '납입 회차 (변경 불가)' : '납입 회차'}
                    name="round"
                    type="number"
                    min={1}
                    max={selectedGroup.months || 36}
                    required
                    readOnly={!!redo}
                    value={redo ? redo.round : form.round}
                    onChange={(event) => setForm((current) => ({ ...current, round: event.target.value }))}
                  />
                  <Input label="약정 납입액 (만 원)" name="amount" type="number" readOnly value={amountInManwon} />
                  <Input
                    label="실제 납입일"
                    name="date"
                    type="date"
                    required
                    value={form.date}
                    onChange={(event) => setForm((current) => ({ ...current, date: event.target.value }))}
                  />
                  <Field label="거래 증빙 파일 (JPG/PNG/WebP/PDF · 최대 5개 · 파일당 1MB)">
                    <input
                      type="file"
                      multiple
                      accept="image/png,image/jpeg,image/webp,application/pdf,.pdf"
                      onChange={pickFiles}
                    />
                  </Field>
                  <Field label="선택한 증빙">
                    <div className="info mini">
                      {files.length ? <div className="proof-multi-preview">
                        {files.map((file, index) => (
                          <div className="proof-preview-item" key={`${index}-${file.slice(-12)}`}>
                            <small>증빙 {index + 1}</small>{' '}
                            <button
                              type="button"
                              className="btn small soft"
                              onClick={() => setFiles((current) => current.filter((_, fileIndex) => fileIndex !== index))}
                            >삭제</button>
                            <ProofAttachment data={file} />
                          </div>
                        ))}
                      </div> : '증빙 파일을 선택해 주세요.'}
                    </div>
                  </Field>
                </div>
                {selectedGroup.status === 'RECRUITING' && (
                  <p className="info mini" style={{ marginBottom: 10 }}>
                    아직 <b>모집 중</b>인 모임이에요. 모임장이 모집을 마감(정원의 절반 이상)하거나 정원이 채워져 운영이 시작된 뒤 납입 증빙을 등록할 수 있어요.
                  </p>
                )}
                <p className="muted mini">
                  약정액(매월 {selectedGroup.payDay}일 · {won(amountInManwon)})에 맞는 증빙만 등록할 수 있습니다.
                </p>
                <button className="btn primary" disabled={!files.length || selectedGroup.status === 'RECRUITING'}>
                  {redo ? '보완 증빙 재제출' : '증빙 등록'}
                </button>
                {redo && <button
                  type="button"
                  className="btn soft"
                  onClick={() => setRedo(null)}
                >재제출 취소</button>}
              </form>
              {proofsLoading ? <p className="muted section">증빙 목록과 첨부 파일을 불러오는 중입니다.</p>
                : proofsError ? <p className="warning section" role="alert">
                  {proofsError}{' '}
                  <button type="button" className="btn small soft" onClick={() => setRefresh((current) => current + 1)}>
                    다시 시도
                  </button>
                </p>
                  : items.length === 0 ? <Empty>이 모임에 등록된 납입 증빙이 없습니다.</Empty>
                    : <div className="section">
                      <h2>증빙 목록 · {selectedGroup?.title}</h2>
                      {items.map((proof) => (
                        <div className="list-item" key={proof.id}>
                          <div className="item-head">
                            <b>{proof.period || '귀속 월 미지정'}월분 · {proof.round}회차 · {proof.memberName}</b>
                            <span className={`pill ${proof.status === '보완 요청' ? 'warn' : ''}`}>
                              {proof.status}
                            </span>
                          </div>
                          <p className="muted mini">
                            납입액 {krw(proof.amount)} · 납입일 {proof.date} · 참조번호 {proof.ref || '미지정'}
                            {proof.resubmitCount > 0 && ` · 재제출 ${proof.resubmitCount}회`}
                          </p>
                          {proof.reviewMemo && <p className="warning mini">보완 사유: {proof.reviewMemo}</p>}
                          {proof.files.map((file) => file.url && (
                            file.mimeType === 'application/pdf'
                              ? <a
                                className="pdf-proof-link"
                                href={file.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                key={file.fileId}
                              >PDF 증빙 보기</a>
                              : <ProofAttachment key={file.fileId} data={file.url} />
                          ))}
                          {proof.status === '보완 요청' &&
                            Number(proof.memberId) === getAccessTokenMemberId() && (
                              <button
                                type="button"
                                className="btn small primary"
                                onClick={() => {
                                  setRedo(proof);
                                  setForm((current) => ({
                                    ...current,
                                    period: proof.period,
                                    round: proof.round,
                                    date: proof.date || todayStr(),
                                  }));
                                  setFiles([]);
                                  window.scrollTo(0, 0);
                                }}
                              >보완 재제출</button>
                            )}
                          {proof.status === '확인 대기' && isOwner && <div className="actions">
                            <button
                              type="button"
                              className="btn small primary"
                              onClick={() => void review(proof, 'CONFIRMED')}
                            >납입 승인</button>
                            <button
                              type="button"
                              className="btn small soft"
                              onClick={() => requestRevision(proof)}
                            >보완 요청</button>
                          </div>}
                        </div>
                      ))}
                    </div>}
            </>}
    </Card>
  </>;
}
