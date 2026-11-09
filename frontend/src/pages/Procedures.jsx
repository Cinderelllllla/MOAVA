/* S10b 모임 절차 — /procedures · 탭: 수령 순서(F27) / 탈퇴·정산(F21 F30) / 규칙·경고·승계(F24 F25 F36) / 해산(F31) */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { groups, procedures as P, proofs, leader, me, memberName } from '../api';
import { PageHead, Card, Flow, Select, Input } from '../components/ui';
import { useOpGroup, GroupSelect } from '../components/shared';
import { won, maskName } from '../common/format';

export default function Procedures() {
  const { ui, setUi } = useApp(); const nav = useNavigate(); const [g, setGroup] = useOpGroup();
  if (!g) return <><PageHead kicker="PROCEDURES" title="모임 절차" sub="참여 중인 모임이 없습니다." /><button className="btn primary" onClick={() => nav('/groups')}>모임 찾기</button></>;
  const t = groups.opsTodos(g), m = me();
  const pendW = g.withdrawals.filter(w => w.status === '정산 동의 대기' && w.userId !== m && !w.consents.includes(m)).length + (g.transfer && !g.transfer.consents.includes(m) ? 1 : 0) + (g.pendingAccount && !g.pendingAccount.consents.includes(m) ? 1 : 0);
  const tabs = [['order', '수령 순서', leader(g) ? t.orders : P.orderRequests(g).filter(r => r.status === '동의 요청' && r.affected?.includes(m) && !r.consents.includes(m)).length], ['withdraw', '탈퇴 · 정산', pendW], ['rules', '규칙 · 경고 · 승계', g.transfer ? 1 : 0], ['dissolve', '해산', 0]];
  const Body = { order: OrderTab, withdraw: WithdrawTab, rules: RulesTab, dissolve: DissolveTab }[ui.procTab] || OrderTab;
  return <><PageHead kicker="GROUP PROCEDURES" title="모임 절차" sub="신청 → 동의 → 확정 흐름이 있는 일은 여기서 한 가지씩 진행합니다."><button className="btn soft" onClick={() => nav('/operations')}>← 운영 홈</button></PageHead>
    <Card><GroupSelect value={g.id} onChange={setGroup} /></Card>
    <div className="proc-tabs">{tabs.map(([k, tt, n]) => <button type="button" key={k} className={`tab ${ui.procTab === k ? 'active' : ''}`} onClick={() => setUi({ procTab: k })}>{tt}{n > 0 && <span className="cnt">{n}</span>}</button>)}</div>
    <Body g={g} /></>;
}

function OrderTab({ g }) {
  const { run } = useApp(); const nav = useNavigate(); const l = leader(g), m = me(), reqs = P.orderRequests(g);
  const mine = reqs.find(x => x.userId === m && ['신청', '동의 요청'].includes(x.status));
  const [round, setRound] = useState(String(mine?.round || 1)), [reason, setReason] = useState('');
  return <Card><h2>수령 순서 신청 · 조정</h2><Flow steps={[['신청', '회원이 희망 회차 제출'], ['조정', '모임장이 채팅으로 협의'], ['동의', '영향받는 회원 동의'], ['확정', '모임장 확정']]} />
    {!l ? <><h3>수령 순서 신청</h3>{mine ? <p className="info">현재 <b>{mine.round}회차</b> 신청이 <b>{mine.status}</b> 상태입니다. {mine.status === '신청' ? '다시 신청하면 기존 내용이 수정됩니다.' : '동의 요청 중에는 수정할 수 없습니다.'}</p> : <p className="muted mini">AI 제안 회차: <b>{P.aiSuggestRound(g, m)}회차</b> (참고용)</p>}
      <div className="form-grid"><Select label="희망 수령 회차" name="orderRound" options={Array.from({ length: g.max }, (_, i) => String(i + 1))} value={round} onChange={e => setRound(e.target.value)} /><Input label="신청 사유" name="orderReason" value={reason} onChange={e => setReason(e.target.value)} /></div>
      <div className="actions"><button className="btn primary" onClick={() => { if (mine && !confirm('기존 수령 순서 신청을 새 내용으로 수정할까요?')) return; run(() => P.submitOrder(g.id, Number(round), reason), r => r).then(r => r.ok && setReason('')); }}>{mine ? '신청 내용 수정' : '수령 순서 신청'}</button></div><div className="divider" /></>
      : <><p className="info">회원 신청을 확인하고 채팅으로 조정한 뒤, 영향받는 회원의 동의를 받아 확정합니다.</p>
        <div className="policy-panel"><h3>AI 제안 수령 순서 <span className="policy-tag">데모 · 가설 H2</span></h3><p className="muted mini">연체 위험이 높은 회원을 앞 회차에 배치해 회수 위험을 줄이는 제안입니다. 모델 연동 전에는 경고·미납·납입 이력으로 산출하며, AI는 제안만 하고 확정은 모임장 + 영향받는 회원 동의입니다.</p>
          <div className="tablewrap"><table className="policy-table"><thead><tr><th>제안 회차</th><th>회원</th><th>근거</th></tr></thead><tbody>{P.aiRiskRank(g).map((x, i) => <tr key={x.id}><td>{i + 1}회차</td><td>{maskName(memberName(x.id))}{x.id === g.ownerId && ' (모임장)'}</td><td className="muted mini">위험 점수 {x.score} · 경고 {g.warnings?.[x.id] || 0}회 · 미납 감지 {proofs.overdueCases().filter(c => c.groupId === g.id && c.memberId === x.id).length}건</td></tr>)}</tbody></table></div></div></>}
    <h3>신청 · 동의 현황</h3><div className="list">{reqs.length ? reqs.map(r => <div className="list-item" key={r.id}><div className="item-head"><b>{memberName(r.userId)} · {r.round}회차</b><span className="pill">{r.status}</span></div><p className="muted mini">{r.reason}{r.aiSuggestedRound && ` · AI 제안 ${r.aiSuggestedRound}회차`}{r.status === '동의 요청' && ` · 동의 ${r.consents.length}/${r.affected?.length || 0}`}</p>
      <div className="actions">{l ? <><button className="btn small soft" onClick={() => nav('/chat')}>채팅으로 조정</button>{r.status === '신청' && <button className="btn small primary" onClick={() => run(() => P.requestOrderConsent(r.id), '영향받는 회원에게 동의 요청을 보냈어요.')}>동의 요청 보내기</button>}{r.status === '동의 요청' && <button className="btn small primary" onClick={() => run(() => P.confirmOrder(r.id), '수령 순서를 확정했어요.')}>동의 확인 후 확정</button>}</> : (r.status === '동의 요청' && r.affected?.includes(m) && !r.consents.includes(m) && <button className="btn small primary" onClick={() => run(() => P.consentOrder(r.id), '동의 이력이 기록됐어요.')}>변경 순서 동의</button>)}</div></div>) : <p className="muted">아직 신청이 없습니다.</p>}</div>
    {g.orderConfirmed && <p className="info" style={{ marginTop: 14 }}>✓ 확정 순서: {g.order.map(o => `${memberName(o.userId)} ${o.round}회차`).join(', ')}</p>}</Card>;
}

function WithdrawTab({ g }) {
  const { run } = useApp(); const l = leader(g), m = me(); const myW = g.withdrawals.find(x => x.userId === m && !['완료', '반려'].includes(x.status));
  const [reason, setReason] = useState(''), [text, setText] = useState('');
  const pend = g.withdrawals.filter(w => !['완료', '반려'].includes(w.status));
  const others = w => g.members.filter(id => id !== w.userId).length;
  const settle = w => { const paid = P.paidTotal(g, w.userId); const amt = prompt(`정산 금액(만 원) — 참고: 납입 확인 합계 ${won(paid)}${g.payoutReceived.includes(w.userId) ? ', 수령 완료자(반환 정산)' : ''}`, String(paid)); if (amt === null) return; const memo = prompt('정산 메모(산정 근거)', '') ?? ''; run(() => P.writeSettlement(g.id, w.id, amt, memo), '정산안을 작성하고 전원에게 동의 요청을 보냈어요.'); };
  return <Card><h2>중도 탈퇴 · 정산 동의</h2><Flow steps={[['신청', '탈퇴 사유 제출'], ['정산안', '모임장이 금액·메모 작성'], ['동의', '회원 전원 공식 동의'], ['처리', '모임장 정산 확인 후 완료']]} />
    {!l ? <><h3>중도 탈퇴 신청</h3><p className="muted mini">곗돈 수령 완료자는 일반 탈퇴가 제한됩니다. 특별 정산은 별도로 요청할 수 있어요.</p>
      {myW ? <p className="info">신청 상태: <b>{myW.status}</b>{myW.settlementAmount !== null && <> · 정산 금액 {won(myW.settlementAmount)} · 동의 {myW.consents.length}/{others(myW)}</>} <button className="btn small soft" onClick={() => run(() => P.reconfirmWithdrawal(g.id), '재확인 요청을 추가했어요.')}>재확인 요청</button></p>
        : <><div className="field"><label>탈퇴 사유</label><select value={reason} onChange={e => setReason(e.target.value)}><option value="">선택해주세요</option><option>경제적 사정</option><option>개인 사정</option><option>기타</option></select></div><div className="field"><label>상세 사유</label><textarea value={text} onChange={e => setText(e.target.value)} placeholder="탈퇴 사유를 작성해 주세요." /></div>
          <div className="actions"><button className="btn primary" disabled={!reason || !text.trim()} onClick={() => confirm('탈퇴 신청 내용을 다시 확인해 주세요. 신청하시겠습니까?') && run(() => P.submitWithdrawal(g.id, `${reason} · ${text.trim()}`), '탈퇴 신청이 접수되었어요.')}>중도 탈퇴 신청</button>{g.payoutReceived.includes(m) && <button className="btn soft" onClick={() => { const r = prompt('특별 정산 예외 요청 사유를 입력해 주세요.'); if (r) run(() => P.submitWithdrawal(g.id, r, true), '특별 정산 예외 요청을 접수했어요.'); }}>특별 정산 예외 요청</button>}</div></>}<div className="divider" /></>
      : <p className="info">회원의 탈퇴 신청에 대해 모임장이 <b>정산 금액·메모</b>를 입력하면 전원 동의 요청이 발송되고, 동의 완료 후 처리할 수 있습니다.</p>}
    <h3>동의가 필요한 건</h3><div className="list">{pend.length ? pend.map(w => <div className="list-item" key={w.id}><div className="item-head"><b>{memberName(w.userId)}의 탈퇴 · 정산</b><span className="pill">{w.status}{w.special && ' · 특별 정산'}</span></div><p className="muted mini">{w.reason} · {w.requestedAt}</p>{w.settlementAmount !== null ? <p className="mini">정산 금액 <b>{won(w.settlementAmount)}</b>{w.settlementMemo && ` · ${w.settlementMemo}`} · 동의 <b>{w.consents.length}/{others(w)}</b></p> : <p className="mini muted">정산안이 아직 작성되지 않았습니다.</p>}
      <div className="actions">{l && w.status === '정산안 작성 대기' && <button className="btn small primary" onClick={() => settle(w)}>정산안 작성</button>}{w.status === '정산 동의 대기' && w.userId !== m && !w.consents.includes(m) && <button className="btn small primary" onClick={() => run(() => P.consentWithdrawal(g.id, w.id), '정산안에 동의했어요.')}>정산안 동의</button>}{l && w.status === '정산 동의 대기' && <button className="btn small primary" onClick={() => confirm(`정산 ${won(w.settlementAmount)} 완료를 확인했나요? 실제 송금은 이루어지지 않습니다.`) && run(() => P.approveWithdrawal(g.id, w.id), '탈퇴를 처리했어요.')}>정산·동의 확인 후 처리</button>}</div></div>) : <p className="muted">진행 중인 탈퇴·정산 건이 없습니다.</p>}
      {g.transfer && <div className="list-item"><div className="item-head"><b>모임장 승계 동의</b><span className="pill">진행 중</span></div><p className="muted mini">승계 대상 {memberName(g.transfer.to)} · 동의 {g.transfer.consents.length}/{g.members.length}</p><div className="actions">{!g.transfer.consents.includes(m) ? <button className="btn small primary" onClick={() => run(() => groups.consentTransfer(g.id), '승계에 동의했어요.')}>승계 동의</button> : <span className="pill">동의 완료</span>}</div></div>}
      {g.pendingAccount && <div className="list-item"><div className="item-head"><b>모임 계좌 변경 동의</b><span className="pill warn">송금 보류</span></div><p className="muted mini">{g.pendingAccount.bank} · 동의 {g.pendingAccount.consents.length}/{g.members.length}</p><div className="actions">{!g.pendingAccount.consents.includes(m) ? <button className="btn small primary" onClick={() => run(() => groups.consentAccount(g.id), '동의를 기록했어요.')}>계좌 변경 동의</button> : <span className="pill">동의 완료</span>}</div></div>}</div></Card>;
}

function RulesTab({ g }) {
  const { run } = useApp(); const nav = useNavigate(); const l = leader(g), m = me(), people = g.members.filter(id => id !== g.ownerId), t = g.transfer;
  const [warnUser, setWarnUser] = useState(String(people[0] || '')), [warnReason, setWarnReason] = useState(''), [succ, setSucc] = useState(String(g.successor || people[0] || ''));
  const edit = () => { const title = prompt('운영 규칙 제목', g.rulesTitle); if (title === null) return; const body = prompt('채팅 협의 후 확정된 운영 규칙', g.rules); if (body === null) return; run(() => groups.editRule(g.id, title, body), '운영 규칙을 등록했어요.'); };
  return <Card><h2>운영 규칙 · 경고 · 승계</h2><div className="info"><b>{g.rulesTitle}</b><br />{g.rules}</div><div className="actions"><button className="btn soft" onClick={() => nav('/chat')}>채팅에서 협의</button>{l && <button className="btn primary" onClick={edit}>규칙 등록 · 수정</button>}</div><p className="muted mini">규칙은 채팅에서 논의하고 모임장이 공식 등록합니다. 등록 시 전원에게 알림이 갑니다.</p><div className="divider" />
    {l ? <><h3>경고 · 강제 탈퇴</h3><p className="muted mini">모임별 최대 2회. 2회부터 강제 탈퇴 검토 가능하며 플랫폼 가입 제한은 ADMIN만 결정합니다.</p><div className="form-grid"><div className="field"><label>대상 회원</label><select value={warnUser} onChange={e => setWarnUser(e.target.value)}>{people.map(id => <option key={id} value={id}>{memberName(id)} ({g.warnings[id] || 0}회)</option>)}{!people.length && <option value="">다른 회원 없음</option>}</select></div><Input label="사유" name="warnReason" placeholder="경고 또는 제재 사유" value={warnReason} onChange={e => setWarnReason(e.target.value)} /></div>
      <div className="actions"><button className="btn soft" onClick={() => run(() => groups.warnMember(g.id, Number(warnUser), warnReason), '경고를 기록했어요.').then(r => r.ok && setWarnReason(''))}>경고 누적</button><button className="btn danger" onClick={() => confirm('소명 및 정산 확인 후 강제 탈퇴를 처리할까요?') && run(() => groups.forceMember(g.id, Number(warnUser), warnReason), '강제 탈퇴를 처리했어요.')}>강제 탈퇴 검토</button></div></>
      : <><h3>나의 경고 내역</h3><p>누적 경고 <b>{g.warnings[m] || 0}회</b> · 제재 이의 신청은 고객센터로 문의하세요.</p></>}
    <div className="divider" /><h3>모임장 승계</h3><p>지정된 승계 대상: <b>{g.successor ? memberName(g.successor) : '미지정'}</b></p>
    {l && <><div className="form-grid"><div className="field"><label>승계 대상 회원</label><select value={succ} onChange={e => setSucc(e.target.value)}>{people.map(id => <option key={id} value={id}>{memberName(id)}</option>)}{!people.length && <option value="">다른 참여 회원이 필요합니다</option>}</select></div></div><div className="actions"><button className="btn small primary" onClick={() => run(() => groups.setSuccessor(g.id, Number(succ)), '승계 대상을 지정했어요.')}>승계 대상 지정</button><button className="btn small soft" onClick={() => run(() => groups.requestTransfer(g.id), '전원에게 승계 동의 요청을 보냈어요.')}>권한 승계 요청</button></div></>}
    {t && <div className="policy-lock"><b>승계 동의 진행 중</b><p>대상 {memberName(t.to)} · 동의 {t.consents.length}/{g.members.length} · 대상 수락 {t.accepted ? '완료' : '대기'}</p><div className="actions">{m === t.to && !t.accepted && <button className="btn small primary" onClick={() => run(() => groups.acceptTransfer(g.id), '승계를 수락했어요.')}>승계 수락</button>}{!t.consents.includes(m) && <button className="btn small soft" onClick={() => run(() => groups.consentTransfer(g.id), '승계에 동의했어요.')}>승계 동의</button>}{l && <button className="btn small primary" onClick={() => run(() => groups.confirmTransfer(g.id), '권한 승계 완료! 기존 모임장은 Member로 남습니다.').then(r => r.ok && nav('/operations'))}>동의 완료 후 확정</button>}</div></div>}
    <details className="policy-fold"><summary>승계 정책 안내</summary><p>부계모임장 제도는 없으며(11/04 결정) 모임장이 승계 대상 회원을 지정 → 대상 수락 + 회원 전원 동의 → 확정합니다. 기존 모임장은 Member로 남고 자동 탈퇴되지 않습니다. 승계 후 모임 계좌는 재인증 필요 상태가 되며 확정 전 송금을 보류하세요.</p></details></Card>;
}

function DissolveTab({ g }) {
  const { run } = useApp(); const l = leader(g), d = g.dissolution;
  return <Card><h2>계모임 해산</h2><Flow steps={[['신청', '모임장이 사유 제출'], ['검토', 'ADMIN이 정산·증빙 확인'], ['승인', '종료 이력 보관']]} /><p>현재 상태: <span className="pill">{d?.status || '운영 중'}</span></p>{d && <p className="info">신청 사유: {d.reason}<br />신청일: {d.requestedAt}{d.rejectReason && <><br />반려 사유: {d.rejectReason}</>}</p>}
    {l && !d && <><p className="muted mini">확인 대기 증빙이 남아 있으면 관리자가 승인하지 않습니다. 해산 전 증빙 처리를 마무리해 주세요.</p><div className="actions"><button className="btn danger" onClick={() => { const r = prompt('해산 사유를 입력해 주세요.'); if (r) run(() => groups.requestDissolve(g.id, r), '해산 신청을 접수했어요. 관리자 검토 후 승인됩니다.'); }}>해산 신청</button></div></>}
    {!l && <p className="muted mini">해산 신청은 모임장만 할 수 있습니다.</p>}</Card>;
}
