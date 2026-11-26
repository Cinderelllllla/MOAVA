/* S06 지역 계모임 찾기 · 지도 — /groups · F11~F14 F16 (GET /api/groups 실 서버 + Kakao Maps 연동) */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useApp } from '../common/store';
import { groups } from '../api';
import { PageHead, Card, Select, Input } from '../components/ui';
import { GroupCard, GroupInfo, InlineSimulator, LoadState } from '../components/shared';
import KakaoGroupMap, { resolvePinMeta } from '../components/map/KakaoGroupMap';

const PURPOSE_OPTIONS = ['전체', '목돈 마련', '재테크', '친목', '창업', '여행'];

export default function Groups() {
  const { ui, setUi } = useApp();
  const [modal, setModal] = useState(null); // null | { region: string | null, pinKey: string | null }
  const [sim, setSim] = useState(null);

  const [allGroups, setAllGroups] = useState([]);
  const [allStatus, setAllStatus] = useState('loading'); // loading | ready | error
  const [allError, setAllError] = useState('');

  const [filteredGroups, setFilteredGroups] = useState([]);
  const [filterStatus, setFilterStatus] = useState('ready'); // loading | ready | error
  const [filterError, setFilterError] = useState('');

  const [reloadTick, setReloadTick] = useState(0);
  const prevSearchRef = useRef(ui.search);

  const hasActiveFilter =
    ui.region !== '전체' ||
    ui.purpose !== '전체' ||
    String(ui.search || '').trim() !== '';

  // 1) 전체 공개 모임 1회 조회 (지역 필터 옵션 구성 · 핀 클릭 모달용 전체 목록 유지)
  useEffect(() => {
    let cancelled = false;
    setAllStatus('loading');
    setAllError('');

    groups
      .fetchPublicGroups()
      .then((items) => {
        if (cancelled) return;
        setAllGroups(items);
        setAllStatus('ready');
      })
      .catch((err) => {
        if (cancelled) return;
        setAllGroups([]);
        setAllStatus('error');
        setAllError(err?.message || '공개 모임 목록을 불러오지 못했어요.');
      });

    return () => {
      cancelled = true;
    };
  }, [reloadTick]);

  // 2) 필터가 적용된 경우 서버 API(GET /api/groups?region=&purpose=&q=)로 조건 조회
  useEffect(() => {
    const searchChanged = prevSearchRef.current !== ui.search;
    prevSearchRef.current = ui.search;

    if (!hasActiveFilter) {
      setFilteredGroups(allGroups);
      setFilterStatus('ready');
      setFilterError('');
      return;
    }

    let cancelled = false;
    setFilterStatus('loading');
    setFilterError('');

    const runFetch = () => {
      groups
        .fetchPublicGroups({
          region: ui.region,
          purpose: ui.purpose,
          q: ui.search,
        })
        .then((items) => {
          if (cancelled) return;
          setFilteredGroups(items);
          setFilterStatus('ready');
        })
        .catch((err) => {
          if (cancelled) return;
          setFilteredGroups([]);
          setFilterStatus('error');
          setFilterError(err?.message || '공개 모임 검색에 실패했어요.');
        });
    };

    if (searchChanged) {
      const timerId = window.setTimeout(runFetch, 200);
      return () => {
        cancelled = true;
        window.clearTimeout(timerId);
      };
    }

    runFetch();
    return () => {
      cancelled = true;
    };
  }, [hasActiveFilter, ui.region, ui.purpose, ui.search, allGroups, reloadTick]);

  const reload = useCallback(() => {
    setReloadTick((n) => n + 1);
  }, []);

  // 지역 선택 옵션: 실제 DB 공개 모임의 시/군/구 목록에서 동적으로 생성
  const regionOptions = useMemo(() => {
    const seen = new Set();
    allGroups.forEach((g) => {
      const sigungu = String(g.regionSigungu || '').trim();
      if (sigungu) seen.add(sigungu);
    });
    const sorted = Array.from(seen).sort((a, b) => a.localeCompare(b, 'ko'));
    if (ui.region && ui.region !== '전체' && !seen.has(ui.region)) {
      sorted.unshift(ui.region);
    }
    return ['전체', ...sorted];
  }, [allGroups, ui.region]);

  const list = hasActiveFilter ? filteredGroups : allGroups;
  const listStatus = hasActiveFilter
    ? (allStatus === 'error' && filterStatus !== 'ready' ? 'error' : filterStatus)
    : allStatus;
  const listError = hasActiveFilter ? (filterError || allError) : allError;

  const modalList = useMemo(() => {
    if (!modal?.region) return list;
    return allGroups.filter((g) => {
      if (modal.pinKey) {
        return resolvePinMeta(g).key === modal.pinKey;
      }
      return g.regionSigungu === modal.region || g.region.includes(modal.region);
    });
  }, [modal, list, allGroups]);

  const chosen = useMemo(() => {
    const inFiltered = list.find((g) => g.id === ui.focus);
    if (inFiltered) return inFiltered;
    return list[0] || null;
  }, [list, ui.focus]);

  const select = (id) => {
    setSim(null);
    setModal(null);
    const inCurrentList = list.some((g) => g.id === id);
    if (!inCurrentList) {
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
        sub="지도에서 지역을 선택하면 해당 지역의 공개 모임 목록을 확인할 수 있습니다."
      />
      <Card>
        <div className="form-row">
          <Select
            label="지역"
            name="region"
            options={regionOptions}
            value={ui.region}
            onChange={(e) => setUi({ region: e.target.value })}
          />
          <Select
            label="목적"
            name="purpose"
            options={PURPOSE_OPTIONS}
            value={ui.purpose}
            onChange={(e) => setUi({ purpose: e.target.value })}
          />
          <Input
            label="모임명 검색"
            name="search"
            type="search"
            placeholder="모임명으로 검색"
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
              <LoadState
                status={listStatus}
                error={listError}
                onRetry={reload}
                loadingText="공개 모임 정보를 불러오는 중이에요."
              >
                {list.length ? (
                  <GroupInfo g={chosen} onSim={setSim} />
                ) : (
                  <div className="group-info-empty">
                    <h3>조건에 맞는 공개 모임이 없어요</h3>
                    <p>필터를 초기화하거나 다른 지역·목적으로 찾아보세요.</p>
                  </div>
                )}
              </LoadState>
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
                disabled={listStatus === 'loading'}
                onClick={() => setModal({ region: null, pinKey: null })}
              >
                {listStatus === 'loading' ? '조회 중…' : `목록 보기 (${list.length})`}
              </button>
            </div>
            {listStatus === 'error' ? (
              <LoadState status="error" error={listError} onRetry={reload} />
            ) : (
              <KakaoGroupMap
                groups={list}
                focusId={chosen?.id ?? null}
                onSelectRegion={(region, _regionGroups, pinKey) => setModal({ region, pinKey })}
              />
            )}
            <p className="muted mini">
              {listStatus === 'loading'
                ? '공개 모임 목록을 불러오고 있어요...'
                : '핀의 숫자는 해당 시/군/구에 등록된 공개 모임 수입니다.'}
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
                모임을 선택하면 왼쪽 상세 정보가 바뀝니다. 필터 안의 모임은 지도 필터를 그대로 유지해요.
              </p>
            </div>
            <button type="button" className="btn soft" onClick={() => setModal(null)}>
              닫기 ✕
            </button>
          </div>
          <div className="group-list-scroll">
            {modalList.length ? (
              modalList.map((g) => (
                <GroupCard
                  key={g.id}
                  g={g}
                  selected={chosen?.id === g.id}
                  onSelect={select}
                />
              ))
            ) : (
              <div className="empty">검색된 공개 모임이 없어요.</div>
            )}
          </div>
        </section>
      </div>
    </>
  );
}
