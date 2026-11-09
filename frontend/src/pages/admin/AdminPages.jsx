/* 관리자 화면 A01~A06: 대시보드 · 계모임 관리 · 제재 · 해산 승인 · 참조번호 · 미납 관리 (A07 신고 처리 · A08 알림은 Misc.jsx 공용) */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../../common/store';
import { groups, admin, proofs, memberName, proofPeriod, db } from '../../api';
import { PageHead, Card, Input, HistoryTable, Empty } from '../../components/ui';
import { ProofAttachment } from '../../components/shared';
import { krw, maskName, maskAccount } from '../../common/format';

const VERIFY = { PENDING: '인증 대기', VERIFIED: '확인 완료', REVERIFY_REQUIRED: '재인증 필요' };

/* ---- A01 대시보드 (F37 F41) ---- */
export function AdminDashboard() {
  const { run } = useApp(); const nav = useNavigate();
  const Stat = ({ label, val, ico, to }) => <button type="button" className="card stat" style={{ textAlign: 'left', cursor: 'pointer', font: 'inherit' }} onClick={() => nav(to)}><small>{label}</small><div className="value">{val}</div><div className="symbol">{ico}</div></button>;
  const gs = groups.listGroups();
  return <><PageHead kicker="ADMIN CONSOLE" title="플랫폼 관리자 대시보드" sub="관리자는 가입 승인 대신 이력을 조회하고, 해산·제재·미납·참조번호·계좌 인증을 처리합니다." />
    <div className="grid cols4"><Stat label="전체 회원" val={admin.members().filter(m => m.status !== 'WITHDRAWN').length + '명'} ico="◌" to="/admin/sanctions" /><Stat label="전체 계모임" val={gs.length + '개'} ico="☁" to="/admin/groups" /><Stat label="해산 승인 대기" val={gs.filter(g => g.dissolution?.status === '관리자 검토 대기').length + '건'} ico="◇" to="/admin/dissolve" /><Stat label="미납 감지 (미조치)" val={proofs.overdueCases().filter(c => c.status === 'DETECTED').length + '건'} ico="◷" to="/admin/overdue" /></div>
    <div className="grid cols4 section"><Stat label="가입 제한" val={admin.sanctions().filter(x => x.active).length + '명'} ico="⚑" to="/admin/sanctions" /><Stat label="참조번호 미지정 증빙" val={proofs.allProofs().filter(p => p.status === '납입 확인' && !p.ref).length + '건'} ico="▣" to="/admin/refs" /><Stat label="계좌 인증 대기" val={gs.filter(g => (g.account && g.account.verifyStatus !== 'VERIFIED') || (g.pendingAccount && g.pendingAccount.verifyStatus !== 'VERIFIED')).length + '건'} ico="🔒" to="/admin/groups" /><Stat label="신고 접수" val={admin.reports().filter(r => r.status === '접수').length + '건'} ico="☏" to="/admin/support" /></div>
    <div className="grid cols2 section"><Card><h2>최근 운영 이력</h2><HistoryTable items={admin.history().slice(0, 8)} /></Card>
      <Card><h2>시연용 회원 관리</h2><p className="muted mini">관리자 전용 테스트 계정 5명 생성 (이름·이메일 중복 방지, 비밀번호 demo1234)</p><div className="actions"><button className="btn small soft" onClick={() => run(() => admin.seedDemoMembers(), n => n ? `테스트 회원 ${n}명을 준비했습니다. 비밀번호: demo1234` : '테스트 회원이 이미 준비되어 있습니다.')}>테스트 회원 5명 준비</button><button className="btn small soft" onClick={() => run(() => proofs.detectOverdue(), n => n ? `${n}건의 미납을 새로 감지했습니다.` : '새로 감지된 미납이 없습니다. (납입일 + 5일 경과 기준)')}>미납 감지 배치 수동 실행</button></div><p className="muted mini">현재 회원: {admin.members().filter(m => m.status !== 'WITHDRAWN').map(m => m.name).join(', ')}</p></Card></div></>;
}

/* ---- A02 계모임 관리 (F37 F33) ---- */
export function AdminGroups() {
  const { run } = useApp(); const [detail, setDetail] = useState(null); const [flt, setFlt] = useState({ group: 'all', type: 'all', status: 'all', page: 0 });
  const g = groups.getGroup(detail), rows = admin.history(), types = [...new Set(rows.map(h => h.type))];
  const filtered = rows.filter(h => (flt.group === 'all' || String(h.groupId) === flt.group) && (flt.type === 'all' || h.type === flt.type) && (flt.status === 'all' || (flt.status === '검토' ? /신청|검토|대기|요청/.test(h.type) : !/신청|검토|대기|요청/.test(h.type))));
  const size = 8, pages = Math.max(1, Math.ceil(filtered.length / size)), page = Math.min(flt.page, pages - 1), view = filtered.slice(page * size, (page + 1) * size);
  const verify = (id, which) => confirm('계좌 소유를 확인한 것으로 처리할까요? (시연용 — 실제 은행 인증 아님)') && run(() => admin.verifyAccount(id, which), '소유 확인을 처리했어요.');
  return <><PageHead kicker="ADMIN GROUPS" title="계모임 관리 · 승인 이력" sub="모임장은 가입·납입을 승인하며 ADMIN은 처리 이력을 조회하고 계좌 소유 확인을 처리합니다." />
    <Card><div className="tablewrap"><table><thead><tr><th>모임</th><th>모임장</th><th>상태</th><th>공개</th><th>회원</th><th>납입일</th><th>계좌 인증</th><th>상세</th></tr></thead><tbody>{groups.listGroups().map(x => <tr key={x.id}><td>{x.title}</td><td>{memberName(x.ownerId)}</td><td>{x.status}</td><td>{x.visibility === 'PRIVATE' ? '🔒 비공개' : '공개'}</td><td>{x.members.length}/{x.max}명</td><td>{x.payDay}일</td><td>{x.account ? VERIFY[x.account.verifyStatus] || x.account.verifyStatus : '미등록'}</td><td><button className="btn small soft" onClick={() => setDetail(x.id)}>상세</button></td></tr>)}</tbody></table></div></Card>
    {g && <div className="detail-panel section"><h2>관리자 상세 · {g.title}</h2><p>모임장: {memberName(g.ownerId)} · 상태: {g.status} · {g.members.length}/{g.max}명 · 매월 {g.payDay}일 · 가정 이율 {g.rate}%</p><p>운영 규칙: {g.rulesTitle} / {g.rules}</p><p>가입 요청 {g.joinRequests.length}건 · 증빙 {proofs.listProofs(g.id).length}건 · 탈퇴 {g.withdrawals.length}건 · 미납 감지 {proofs.overdueCases().filter(c => c.groupId === g.id).length}건</p>
      <div className="policy-panel"><h3>모임 계좌 소유 확인 (GYE_ACCOUNT.VERIFY_STATUS)</h3>{g.account ? <><p>{g.account.bank} · 예금주 {maskName(g.account.holder)} · {maskAccount(g.account.accountNo)} <span className="policy-tag">{VERIFY[g.account.verifyStatus] || g.account.verifyStatus}</span></p>{g.account.verifyStatus !== 'VERIFIED' && <div className="actions"><button className="btn small primary" onClick={() => verify(g.id, 'account')}>소유 확인 처리 (시연)</button></div>}</> : <p className="muted">등록된 계좌가 없습니다.</p>}
        {g.pendingAccount && <><p style={{ marginTop: 10 }}>변경 신청 중: {g.pendingAccount.bank} · {maskAccount(g.pendingAccount.accountNo)} · {g.pendingAccount.requestedAt} <span className="policy-tag">{VERIFY[g.pendingAccount.verifyStatus]}</span></p>{g.pendingAccount.verifyStatus !== 'VERIFIED' && <div className="actions"><button className="btn small primary" onClick={() => verify(g.id, 'pending')}>변경 계좌 소유 확인 처리</button></div>}</>}
        <p className="muted mini">1원 인증 API 미연동(11/04 결정 ⑤). 관리자가 확인 처리하면 VERIFIED로 바뀌고 모임장이 변경을 확정할 수 있습니다.</p></div>
      <p className="info mini">가입 승인 및 납입 증빙 승인은 모임장 전용입니다. 관리자는 조회·감사 기능과 위 계좌 확인만 사용합니다.</p><div className="actions"><button className="btn small soft" onClick={() => setDetail(null)}>닫기</button></div></div>}
    <div className="section"><Card><h2>모임별 처리·해산 이력</h2><div className="policy-history-wrap"><div className="policy-admin-filter">
      <div className="field"><label>계모임 선택</label><select value={flt.group} onChange={e => setFlt({ ...flt, group: e.target.value, page: 0 })}><option value="all">전체 계모임</option>{groups.listGroups().map(x => <option key={x.id} value={x.id}>{x.title}</option>)}</select></div>
      <div className="field"><label>처리 유형</label><select value={flt.type} onChange={e => setFlt({ ...flt, type: e.target.value, page: 0 })}><option value="all">전체 유형</option>{types.map(t => <option key={t}>{t}</option>)}</select></div>
      <div className="field"><label>처리 상태</label><select value={flt.status} onChange={e => setFlt({ ...flt, status: e.target.value, page: 0 })}><option value="all">전체 상태</option><option value="처리">처리됨</option><option value="검토">검토·신청</option></select></div></div>
      <div className="policy-history-summary">조회 결과 <b>{filtered.length}건</b> · 페이지 {page + 1}/{pages}</div>
      <div className="policy-history-items">{view.length ? view.map(h => <details className="policy-history-item" key={h.id}><summary><span className="policy-history-type">{h.type}</span><span className="policy-history-group">{groups.getGroup(h.groupId)?.title || '공통'}</span><span className="policy-history-date">{h.date}</span></summary><p>{h.description} · 행위자 {h.actor}</p></details>) : <Empty>선택한 조건의 기록이 없습니다.</Empty>}</div>
      <div className="actions policy-history-pages"><button type="button" className="btn small soft" disabled={page === 0} onClick={() => setFlt({ ...flt, page: page - 1 })}>이전</button><button type="button" className="btn small soft" disabled={page >= pages - 1} onClick={() => setFlt({ ...flt, page: page + 1 })}>다음</button></div></div></Card></div></>;
}

/* ---- A03 제재 (F39) ---- */
export function AdminSanctions() {
  const { run } = useApp(); const [f, setF] = useState({ user: '', reason: '' }); const [wf, setWf] = useState({ search: '', type: 'all', page: 0 });
  const active = admin.sanctions().filter(x => x.active), ids = new Set(active.map(x => x.userId));
  const available = admin.members().filter(x => x.status !== 'WITHDRAWN' && !ids.has(x.id));
  const summaries = groups.listGroups().flatMap(g => Object.entries(g.warnings || {}).filter(([, c]) => c > 0).map(([id, count]) => ({ g, id: Number(id), count }))).sort((a, b) => b.count - a.count);
  const warnRows = admin.history().filter(x => ['경고', '강제 탈퇴'].includes(x.type)).filter(h => (wf.type === 'all' || h.type === wf.type) && (!wf.search || [h.description, groups.getGroup(h.groupId)?.title, h.type].join(' ').toLowerCase().includes(wf.search.toLowerCase())));
  const size = 8, pages = Math.max(1, Math.ceil(warnRows.length / size)), page = Math.min(wf.page, pages - 1);
  return <><PageHead kicker="PLATFORM SAFETY" title="신고 · 제재 관리" sub="중복 제재를 방지하고 회원별 경고·강제 탈퇴 이력을 관리합니다. 플랫폼 가입 제한은 ADMIN만 결정합니다." />
    <Card><h2>신규 가입 제한 설정</h2><div className="form-grid"><div className="field"><label>제재 대상 (현재 제한 회원 제외)</label><select value={f.user} onChange={e => setF({ ...f, user: e.target.value })}><option value="">회원 선택</option>{available.map(a => <option key={a.id} value={a.id}>{a.name} · {a.email}</option>)}</select></div><Input label="제재 사유" name="sanctionReason" placeholder="제재 사유를 입력하세요" value={f.reason} onChange={e => setF({ ...f, reason: e.target.value })} /></div>
      <div className="actions"><button className="btn danger" onClick={() => run(() => admin.setSanction(Number(f.user), f.reason), '가입 제한을 설정했어요.').then(r => r.ok && setF({ user: '', reason: '' }))}>플랫폼 가입 제한 설정</button></div><div className="divider" />
      <h2>현재 가입 제한 · {active.length}명</h2>{active.length ? active.map(x => <div className="list-item item-head" key={x.id}><div><b>{memberName(x.userId)}</b><p className="muted mini">{x.reason} · {x.date}</p></div><button className="btn small soft" onClick={() => confirm('가입 제한을 해제할까요? 이전 제재 기록은 감사 이력에 남습니다.') && run(() => admin.releaseSanction(x.userId), '가입 제한을 해제했어요.')}>가입 제한 해제</button></div>) : <p className="muted">가입 제한 중인 회원이 없습니다.</p>}</Card>
    <div className="section"><Card><h2>모임별 회원 경고 현황</h2><p className="muted mini">모임별 최대 2회. 2회 도달 시 자동 탈퇴하지 않고 모임장이 검토합니다.</p>{summaries.length ? summaries.map(x => <div className="list-item item-head" key={x.g.id + '-' + x.id}><div><b>{memberName(x.id)}</b> · {x.g.title}<p className="muted mini">경고 {x.count}회 · {x.count >= 2 ? '강제 탈퇴 검토 대상' : '경고 관리 중'}</p></div><span className={`pill ${x.count >= 2 ? 'warn' : ''}`}>{x.count}/2회</span></div>) : <p className="muted">현재 경고 대상이 없습니다.</p>}</Card></div>
    <div className="section"><Card><h2>경고 · 강제 탈퇴 상세 이력</h2><div className="form-grid"><Input label="회원·모임 검색" name="warnSearch" placeholder="회원명 또는 모임명" value={wf.search} onChange={e => setWf({ ...wf, search: e.target.value, page: 0 })} /><div className="field"><label>처리 유형</label><select value={wf.type} onChange={e => setWf({ ...wf, type: e.target.value, page: 0 })}><option value="all">전체</option><option>경고</option><option>강제 탈퇴</option></select></div></div>
      <HistoryTable items={warnRows.slice(page * size, (page + 1) * size)} /><div className="actions" style={{ justifyContent: 'flex-end', marginTop: 14 }}><span className="muted mini">{warnRows.length}건 · {page + 1}/{pages}페이지</span><button className="btn small soft" disabled={page <= 0} onClick={() => setWf({ ...wf, page: page - 1 })}>이전</button><button className="btn small soft" disabled={page >= pages - 1} onClick={() => setWf({ ...wf, page: page + 1 })}>다음</button></div></Card></div></>;
}

/* ---- A04 해산 승인 (F32) ---- */
export function AdminDissolve() {
  const { run } = useApp(); const pending = groups.listGroups().filter(g => g.dissolution && g.status !== '해산 완료'), done = groups.listGroups().filter(g => g.status === '해산 완료');
  return <><PageHead kicker="DISSOLUTION REVIEW" title="해산 신청 · 승인" sub="모임장의 해산 신청을 확인하고 정산 검토 후 최종 승인합니다." />
    <Card><div className="list">{pending.length ? pending.map(g => <div className="list-item" key={g.id}><div className="item-head"><b>{g.title}</b><span className="pill">{g.dissolution.status}</span></div><p>해산 사유: {g.dissolution.reason}</p><p className="muted mini">신청일: {g.dissolution.requestedAt} · 회원 {g.members.length}명 · 미처리 증빙 {proofs.listProofs(g.id).filter(x => x.status !== '납입 확인').length}건 · 진행 중 탈퇴 {g.withdrawals.filter(w => !['완료', '반려'].includes(w.status)).length}건</p>
      {g.dissolution.status === '관리자 검토 대기' && <div className="actions"><button className="btn primary" onClick={() => confirm('정산 완료와 참여자 보호 조치를 확인했나요? 이 데모는 실제 송금을 검증하지 않습니다.') && run(() => admin.approveDissolve(g.id), '해산을 승인했어요.')}>정산 확인 후 최종 승인</button><button className="btn soft" onClick={() => { const r = prompt('반려 사유'); if (r) run(() => admin.rejectDissolve(g.id, r), '해산 신청을 반려했어요.'); }}>반려</button></div>}</div>) : <Empty>해산 신청이 없습니다.</Empty>}</div></Card>
    <div className="section"><Card><h2>종료된 모임 기록</h2>{done.length ? done.map(g => <div className="list-item" key={g.id}><b>{g.title}</b><p className="muted">{g.dissolution?.reason} · 승인 {g.dissolution?.approvedAt}</p></div>) : <p className="muted">종료된 모임이 없습니다.</p>}</Card></div></>;
}

/* ---- A05 참조번호 (F24: 지정 · 정정(사유) · 이력) ---- */
export function AdminRefs() {
  const { run } = useApp();
  const assign = p => { const ref = prompt('거래 참조번호를 지정해 주세요.', admin.nextRef()); if (ref) run(() => admin.assignRef(p.id, ref), '참조번호를 지정했어요.'); };
  const correct = p => { const ref = prompt('정정할 참조번호', p.ref); if (!ref || ref === p.ref) return; const reason = prompt('정정 사유 (필수)'); run(() => admin.correctRef(p.id, ref, reason), '참조번호를 정정했어요.'); };
  const items = proofs.allProofs().slice().reverse();
  return <><PageHead kicker="EVIDENCE REFERENCES" title="거래 참조번호 관리" sub="증빙은 회원이 등록하고 거래 참조번호는 ADMIN이 지정합니다. 정정은 사유와 함께 이력이 남습니다." />
    <Card><div className="list">{items.length ? items.map(p => <div className="list-item" key={p.id}><div className="item-head"><b>{groups.getGroup(p.groupId)?.title || '모임'} · {proofPeriod(p)}월분 {p.round}회차 · {krw(p.amount)}</b><span className="pill">{p.status}</span></div><p className="muted mini">등록자 {memberName(p.memberId)} · 납입일 {p.date} · 참조번호 <b>{p.ref || '미지정'}</b>{p.refHistory?.length > 0 && ` · 정정 ${p.refHistory.length}회`}</p>{(p.images || []).slice(0, 1).map((d, i) => <ProofAttachment key={i} data={d} />)}{p.refHistory?.length > 0 && <details className="policy-fold"><summary>정정 이력</summary>{p.refHistory.map((h, i) => <p key={i}>{h.at} · {h.from} → {h.to} · {h.reason}</p>)}</details>}
      <div className="actions">{p.ref ? <button className="btn small soft" onClick={() => correct(p)}>참조번호 정정 (사유)</button> : p.status === '납입 확인' ? <button className="btn small primary" onClick={() => assign(p)}>참조번호 지정</button> : <span className="muted mini">납입 확인 후 지정 가능</span>}</div></div>) : <Empty>등록된 증빙이 없습니다.</Empty>}</div></Card></>;
}

/* ---- A06 미납 관리 (F26: 감지 목록형) ---- */
const OD_STATUS = { DETECTED: '감지됨 · 미조치', WARNED: '경고 알림 발송', RESTRICTED: '채팅 제한 중', REVIEW: '수령 후순위 검토', RESOLVED: '납입 확인 · 해소' };
export function AdminOverdue() {
  const { run } = useApp(); const open = proofs.overdueCases().filter(c => c.status !== 'RESOLVED').slice().sort((a, b) => b.days - a.days), done = proofs.overdueCases().filter(c => c.status === 'RESOLVED');
  const act = (c, a) => run(() => admin.overdueAction(c.id, a), '관리자 조치가 반영되었습니다.');
  const canRestrict = c => (groups.getGroup(c.groupId)?.overduePolicy || '').includes('채팅 제한'); // 모임 생성 시 정한 미납 대응 규칙
  return <><PageHead kicker="OVERDUE CONTROL" title="관리자 미납 관리" sub="매일 02:00 배치가 납입일 + 5일 경과 &amp; 납입 확인 없음을 감지해 목록에 올립니다. 관리자는 감지 건을 선택해 조치합니다."><button className="btn soft" onClick={() => run(() => proofs.detectOverdue(), n => n ? `${n}건의 미납을 새로 감지했습니다.` : '새로 감지된 미납이 없습니다.')}>감지 배치 수동 실행</button></PageHead>
    <Card><h2>감지된 미납 · {open.length}건</h2><p className="muted mini">5일 미만은 시스템이 납입 안내 알림만 자동 발송하며 이 목록에 오르지 않습니다. 실제 입금 조회는 지원하지 않으며 증빙 승인 시 자동 해소됩니다.</p>
      <div className="tablewrap"><table><thead><tr><th>모임</th><th>회원</th><th>귀속 월</th><th>경과일</th><th>상태</th><th>조치</th></tr></thead><tbody>{open.length ? open.map(c => <tr key={c.id}><td>{groups.getGroup(c.groupId)?.title || '—'}<br /><small className="muted">규칙: {groups.getGroup(c.groupId)?.overduePolicy || '-'}</small></td><td>{memberName(c.memberId)}</td><td>{c.period}</td><td>{c.days}일</td><td><span className={`pill ${c.status === 'DETECTED' ? 'warn' : ''}`}>{OD_STATUS[c.status] || c.status}</span></td><td><div className="actions" style={{ margin: 0 }}><button className="btn small soft" onClick={() => act(c, 'warn')}>경고 알림</button>{c.status === 'RESTRICTED' ? <button className="btn small soft" onClick={() => act(c, 'release')}>채팅 제한 해제</button> : <button className="btn small danger" disabled={!canRestrict(c)} title={canRestrict(c) ? '' : "이 모임의 미납 대응 규칙은 '5일차 알림'만 선택되어 채팅 제한을 적용할 수 없습니다."} onClick={() => act(c, 'restrict')}>채팅 제한</button>}<button className="btn small soft" onClick={() => act(c, 'order')}>후순위 검토</button></div></td></tr>) : <tr><td colSpan={6} className="muted">감지된 미납이 없습니다. (배치 수동 실행으로 확인 가능)</td></tr>}</tbody></table></div></Card>
    <div className="section"><Card><h2>조치 이력</h2><HistoryTable items={admin.history().filter(h => h.type.startsWith('미납')).slice(0, 10)} /><details className="policy-fold"><summary>해소된 건 {done.length}건</summary>{done.length ? done.map(c => <p className="muted mini" key={c.id}>{groups.getGroup(c.groupId)?.title} · {memberName(c.memberId)} · {c.period} · 해소 {c.resolvedAt}</p>) : <p className="muted mini">없음</p>}</details></Card></div></>;
}
