/* S06 지역 계모임 찾기 · 지도 — /groups · F11~F14 F16 (Kakao Maps 연동, 3·5번 단계에서 서버 API 연결 예정) */
import { useState } from 'react';
import { useApp } from '../common/store';
import { groups, groupVisible } from '../api';
import { PageHead, Card, Select, Input } from '../components/ui';
import { GroupCard, GroupInfo, InlineSimulator } from '../components/shared';
import KakaoGroupMap from '../components/map/KakaoGroupMap';

export default function Groups() {
  const { ui, setUi } = useApp();
  const [modal, setModal] = useState(null); // null | { region: string | null }
  const [sim, setSim] = useState(null);

  const visible = (g) =>
    groupVisible(g) &&
    (ui.region === '전체' || g.region.includes(ui.region)) &&
    (ui.purpose === '전체' || g.purpose === ui.purpose) &&
    g.title.toLocaleLowerCase().includes(ui.search.toLocaleLowerCase());

  const list = groups.listGroups().filter(visible);
  const modalList = modal?.region
    ? groups.listGroups().filter((g) => groupVisible(g) && g.region.includes(modal.region))
    : list;
  const chosen = list.find((g) => g.id === ui.focus) || list[0] || null;

  const select = (id) => {
    setSim(null);
    setModal(null);
    const g = groups.getGroup(id);
    if (g && !visible(g)) {
      setUi({ region: '전체', purpose: '전체', search: '', focus: id });
    } else {
      setUi({ focus: id });
    }
  };

  return (
    <>
      <PageHead
        kicker="LOCAL COMMUNITY"
        title="지역 계모임 찾기 · 지도"
        sub="지도에서 지역을 선택하면 해당 지역의 모임 목록을 확인할 수 있습니다."
      />
      <Card>
        <div className="form-row">
          <Select
            label="지역"
            name="region"
            options={['전체', '마포', '서대문', '강남', '성동']}
            value={ui.region}
            onChange={(e) => setUi({ region: e.target.value })}
          />
          <Select
            label="목적"
            name="purpose"
            options={['전체', '목돈 마련', '재테크', '친목', '창업', '여행']}
            value={ui.purpose}
            onChange={(e) => setUi({ purpose: e.target.value })}
          />
          <Input
            label="모임명 검색"
            name="search"
            type="search"
            value={ui.search}
            onChange={(e) => setUi({ search: e.target.value })}
          />
          <button
            type="button"
            className="btn soft reset-map-filters"
            onClick={() => setUi({ region: '전체', purpose: '전체', search: '' })}
          >
            초기화
          </button>
        </div>
      </Card>

      <div className="matching-layout section matching-layout-two">
        <section className="matching-info">
          <Card>
            <h2>{sim ? '간단 시뮬레이션' : '선택한 모임 정보'}</h2>
            {sim ? (
              <InlineSimulator g={sim} onClose={() => setSim(null)} />
            ) : (
              <GroupInfo g={chosen} onSim={setSim} />
            )}
          </Card>
        </section>

        <section className="matching-map">
          <Card>
            <div className="map-title-row">
              <h2>우리 동네 계모임 지도</h2>
              <button
                type="button"
                className="btn small soft"
                onClick={() => setModal({ region: null })}
              >
                목록 보기 ({list.length})
              </button>
            </div>
            <KakaoGroupMap
              groups={list}
              focusId={chosen?.id ?? null}
              onSelectRegion={(region) => setModal({ region })}
            />
            <p className="muted mini">
              핀의 숫자는 해당 시/군/구에 등록된 모임 수입니다. (현재는 지도 동작 확인용 샘플 데이터 기준이며, 다음 단계에서 서버 DB 데이터와 연결됩니다.)
            </p>
          </Card>
        </section>
      </div>

      <div
        className={`modal-backdrop ${modal ? 'show' : ''}`}
        onClick={(e) => {
          if (e.target === e.currentTarget) setModal(null);
        }}
      >
        <section
          className="modal-panel group-list-modal"
          role="dialog"
          aria-modal="true"
          aria-label="모임 목록"
        >
          <div className="modal-heading">
            <div>
              <h2>
                {modal?.region ? `${modal.region} 지역 모임` : '모임 목록'}{' '}
                <small className="muted">{modalList.length}개</small>
              </h2>
              <p className="muted mini">
                모임을 선택하면 왼쪽 상세 정보가 바뀝니다. 지도 필터는 그대로 유지돼요.
              </p>
            </div>
            <button type="button" className="btn soft" onClick={() => setModal(null)}>
              닫기 ✕
            </button>
          </div>
          <div className="group-list-scroll">
            {modalList.length ? (
              modalList.map((g) => <GroupCard key={g.id} g={g} onSelect={select} />)
            ) : (
              <div className="empty">검색된 모임이 없어요.</div>
            )}
          </div>
        </section>
      </div>
    </>
  );
}
