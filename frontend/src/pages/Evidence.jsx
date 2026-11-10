/* S12 납입 증빙 — /evidence · F22 F23 (승인 · 보완 요청(사유) · 재제출 · 귀속 월/회차 중복 방지) */
import { useState } from 'react';
import { useApp } from '../common/store';
import { groups, proofs, leader, me, memberName, proofPeriod } from '../api';
import { PageHead, Card, Input, Field, Empty } from '../components/ui';
import { useOpGroup, ProofAttachment } from '../components/shared';
import { won, krw, todayStr } from '../common/format';

const periodOptions = g => { const now = new Date(); return Array.from({ length: Math.max(2, Math.min(g.months || 6, 36)) }, (_, i) => { const dt = new Date(now.getFullYear(), now.getMonth() - i, 1); return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}`; }); };
const ACCEPT = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];

export default function Evidence() {
  const { run, toast } = useApp(); const [g, setGroup] = useOpGroup(); const mine = groups.myGroups();
  const [f, setF] = useState({ period: g ? periodOptions(g)[0] : '', round: 1, date: todayStr() });
  const [files, setFiles] = useState([]); const [redo, setRedo] = useState(null);
  if (!g) return <><PageHead kicker="PAYMENT EVIDENCE" title="납입 증빙 · 확인" sub="참여 중인 모임이 없습니다." /></>;
  const items = proofs.listProofs(g.id).slice().reverse();
  const pick = async e => {
    const list = [...e.target.files]; e.target.value = ''; if (!list.length) return;
    if (list.length + files.length > 5) return toast('증빙은 최대 5개까지 선택할 수 있어요.');
    if (list.some(x => !ACCEPT.includes(x.type) || x.size > 1024 * 1024)) return toast('JPG, PNG, WebP, PDF만 파일당 1MB 이하로 올려 주세요.');
    const data = await Promise.all(list.map(x => new Promise((ok, no) => { const r = new FileReader(); r.onload = () => ok(r.result); r.onerror = no; r.readAsDataURL(x); })));
    if (data.some(d => files.includes(d)) || new Set(data).size !== data.length) return toast('동일한 증빙 파일은 중복으로 추가할 수 없습니다.');
    setFiles([...files, ...data]);
  };
  const submit = async e => { e.preventDefault(); const r = await run(() => proofs.addProof(g.id, { ...f, amount: g.amount }, files, redo?.id), redo ? '보완 증빙을 재제출했어요.' : '증빙이 등록됐어요.'); if (r.ok) { setFiles([]); setRedo(null); } };
  const revision = x => { const memo = prompt('보완 요청 사유를 입력해 주세요. (예: 입금자명 불일치, 금액 식별 불가)'); if (memo) run(() => proofs.requestRevision(x.id, memo), '보완 요청을 보냈어요.'); };
  return <><PageHead kicker="PAYMENT EVIDENCE" title="납입 증빙 · 확인" sub="모임·귀속 월·회차·약정 금액을 확인하고 증빙을 제출합니다. 거래 참조번호는 ADMIN이 지정합니다." />
    <Card><div className="field"><label>계모임 선택</label><select value={g.id} onChange={e => { setGroup(e.target.value); setFiles([]); setRedo(null); }}>{mine.map(x => <option key={x.id} value={x.id}>{x.title}</option>)}</select></div>
      <form onSubmit={submit} className="section">{redo && <p className="warning mini">보완 재제출: {proofPeriod(redo)}월분 {redo.round}회차 · 보완 사유 "{redo.reviewMemo}" — 귀속 월·회차·약정액은 고정되며 증빙 파일과 실제 납입일만 수정할 수 있습니다.</p>}
        <div className="form-grid"><Field label={redo ? '납입 귀속 월 (재제출 · 변경 불가)' : '납입 귀속 월'}><select value={redo ? proofPeriod(redo) : f.period} disabled={!!redo} onChange={e => setF({ ...f, period: e.target.value })}>{(redo ? [proofPeriod(redo)] : periodOptions(g)).map(v => <option key={v} value={v}>{v.replace('-', '년 ')}월분</option>)}</select></Field><Input label={redo ? '납입 회차 (재제출 · 변경 불가)' : '납입 회차'} name="round" type="number" min={1} max={g.months || 36} required readOnly={!!redo} value={redo ? redo.round : f.round} onChange={e => setF({ ...f, round: e.target.value })} /><Input label="약정 납입액 (만 원)" name="amount" type="number" readOnly value={g.amount} /><Input label="실제 납입일" name="date" type="date" required value={f.date} onChange={e => setF({ ...f, date: e.target.value })} />
          <Field label="거래 증빙 파일 (JPG/PNG/WebP/PDF · 최대 5개 · 파일당 1MB)"><input type="file" multiple accept="image/png,image/jpeg,image/webp,application/pdf,.pdf" onChange={pick} /></Field>
          <Field label="선택한 증빙"><div className="info mini">{files.length ? <div className="proof-multi-preview">{files.map((d, i) => <div className="proof-preview-item" key={i}><small>증빙 {i + 1}</small> <button type="button" className="btn small soft" onClick={() => setFiles(files.filter((_, j) => j !== i))}>삭제</button><ProofAttachment data={d} /></div>)}</div> : '증빙 파일을 선택해 주세요.'}</div></Field></div>
        <p className="muted mini">약정액(매월 {g.payDay}일 · {won(g.amount)})과 일치하는 정상 납입만 접수합니다. 파일은 서버 저장 시 UUID 이름으로 암호화 보관됩니다.</p>
        <button className="btn primary">{redo ? '보완 증빙 재제출' : '증빙 등록'}</button>{redo && <button type="button" className="btn soft" onClick={() => setRedo(null)}>재제출 취소</button>}</form></Card>
    <div className="section"><Card><h2>증빙 목록</h2>{items.length ? items.map(x => <div className="list-item" key={x.id}><div className="item-head"><b>{proofPeriod(x)}월분 · {x.round}회차 · {memberName(x.memberId)}</b><span className={`pill ${x.status === '보완 요청' ? 'warn' : ''}`}>{x.status}</span></div><p className="muted mini">납입액 {krw(x.amount)} · 납입일 {x.date} · 참조번호 {x.ref || '미지정'}{x.resubmitCount > 0 && ` · 재제출 ${x.resubmitCount}회`}</p>{x.status === '보완 요청' && <p className="warning mini">보완 사유: {x.reviewMemo}</p>}{(x.images || []).map((d, i) => <ProofAttachment key={i} data={d} />)}
      <div className="actions">{leader(g) && x.status === '확인 대기' && <><button className="btn small primary" onClick={() => run(() => proofs.approveProof(x.id), '납입을 승인했어요.')}>납입 승인</button><button className="btn small soft" onClick={() => revision(x)}>보완 요청</button></>}{x.memberId === me() && x.status === '보완 요청' && <button className="btn small primary" onClick={() => { setRedo(x); setF({ ...f, period: proofPeriod(x), round: x.round, date: x.date || todayStr() }); setFiles([]); window.scrollTo(0, 0); }}>보완 재제출</button>}</div></div>) : <Empty>등록된 증빙이 없습니다.</Empty>}</Card></div></>;
}
