/* S06 지역 계모임 찾기 · 지도 — /groups · F11~F14 F16 (실서비스: Kakao Maps + ES moava_group) */
import { useState } from 'react';
import { useApp } from '../common/store';
import { groups, groupVisible } from '../api';
import { PageHead, Card, Select, Input } from '../components/ui';
import { GroupCard, GroupInfo, InlineSimulator } from '../components/shared';

const PIN_POS = { 101: [22, 26], 102: [48, 48], 103: [33, 75], 104: [78, 29], 105: [79, 75] };

export default function Groups() {
  const { ui, setUi } = useApp();
  const [modal, setModal] = useState(null); // null | {region: string|null}
  const [sim, setSim] = useState(null);
  const visible = g => groupVisible(g) && (ui.region === '전체' || g.region.includes(ui.region)) && (ui.purpose === '전체' || g.purpose === ui.purpose) && g.title.toLocaleLowerCase().includes(ui.search.toLocaleLowerCase());
  const list = groups.listGroups().filter(visible);
  const modalList = modal?.region ? groups.listGroups().filter(g => groupVisible(g) && g.region.includes(modal.region)) : list;
  const chosen = list.find(g => g.id === ui.focus) || list[0] || null;
  const regionGroups = {}; list.forEach(g => { const r = g.region.split(' ').slice(-1)[0] || g.region; (regionGroups[r] ||= []).push(g); });
  const select = id => { setSim(null); setModal(null); const g = groups.getGroup(id); if (g && !visible(g)) setUi({ region: '전체', purpose: '전체', search: '', focus: id }); else setUi({ focus: id }); };
  return <><PageHead kicker="LOCAL COMMUNITY" title="지역 계모임 찾기 · 지도" sub="지도에서 지역을 선택하면 해당 지역의 모임 목록을 확인할 수 있습니다." />
    <Card><div className="form-row"><Select label="지역" name="region" options={['전체', '마포', '서대문', '강남', '성동']} value={ui.region} onChange={e => setUi({ region: e.target.value })} /><Select label="목적" name="purpose" options={['전체', '목돈 마련', '재테크', '친목', '창업', '여행']} value={ui.purpose} onChange={e => setUi({ purpose: e.target.value })} /><Input label="모임명 검색" name="search" type="search" value={ui.search} onChange={e => setUi({ search: e.target.value })} /><button type="button" className="btn soft reset-map-filters" onClick={() => setUi({ region: '전체', purpose: '전체', search: '' })}>초기화</button></div></Card>
    <div className="matching-layout section matching-layout-two">
      <section className="matching-info"><Card><h2>{sim ? '간단 시뮬레이션' : '선택한 모임 정보'}</h2>{sim ? <InlineSimulator g={sim} onClose={() => setSim(null)} /> : <GroupInfo g={chosen} onSim={setSim} />}</Card></section>
      <section className="matching-map"><Card><div className="map-title-row"><h2>우리 동네 계모임 지도</h2><button className="btn small soft" onClick={() => setModal({ region: null })}>목록 보기 ({list.length})</button></div>
        <div className="map">{Object.entries(regionGroups).map(([region, gs], i) => { const rep = gs.find(g => g.id === ui.focus) || gs[0], pt = PIN_POS[rep.id] || [20 + (i * 17) % 60, 25 + (i * 13) % 55]; return <button type="button" key={region} className={`map-pin ${gs.some(g => g.id === ui.focus) ? 'active' : ''}`} style={{ left: `${pt[0]}%`, top: `${pt[1]}%` }} onClick={() => setModal({ region })} aria-label={`${region} 지역 모임 목록 열기`}>☁ {region}{gs.length > 1 && <> <span className="pin-count">{gs.length}</span></>}</button>; })}<div className="map-hint">같은 지역은 하나의 핀으로 표시됩니다. 핀을 누르면 해당 지역의 모임 목록이 열립니다.</div></div>
        <p className="muted mini">핀에 숫자가 있으면 해당 지역에 여러 모임이 있습니다. 실제 서비스에서는 Kakao Maps 위에 표시됩니다.</p></Card></section>
    </div>
    <div className={`modal-backdrop ${modal ? 'show' : ''}`} onClick={e => { if (e.target === e.currentTarget) setModal(null); }}>
      <section className="modal-panel group-list-modal" role="dialog" aria-modal="true" aria-label="모임 목록"><div className="modal-heading"><div><h2>{modal?.region ? `${modal.region} 지역 모임` : '모임 목록'} <small className="muted">{modalList.length}개</small></h2><p className="muted mini">모임을 선택하면 왼쪽 상세 정보가 바뀝니다. 지도 필터는 그대로 유지돼요.</p></div><button className="btn soft" onClick={() => setModal(null)}>닫기 ✕</button></div>
        <div className="group-list-scroll">{modalList.length ? modalList.map(g => <GroupCard key={g.id} g={g} onSelect={select} />) : <div className="empty">검색된 모임이 없어요.</div>}</div></section>
    </div></>;
}
