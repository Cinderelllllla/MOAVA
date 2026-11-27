/* S06 지역 계모임 찾기 · 지도 — /groups · F11~F14 F16 (GET /api/groups 실 서버 + Kakao Maps 연동) */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useApp } from '../common/store';
import { groups } from '../api';
import { PageHead, Card, Select, Input } from '../components/ui';
import { GroupCard, GroupInfo, InlineSimulator, LoadState } from '../components/shared';
import KakaoGroupMap, { resolvePinMeta } from '../components/map/KakaoGroupMap';
import RegionSelector from '../components/RegionSelector';
import { buildServerRegionQuery, matchesRegionFilter } from '../common/regions';

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

  const regionSido = ui.regionSido || '전체';
  const regionSigungu = ui.regionSigungu || '전체';
  const regionDong = ui.regionDong || '전체';

  const hasActiveFilter =
    regionSido !== '전체' ||
    regionSigungu !== '전체' ||
    regionDong !== '전체' ||
    ui.purpose !== '전체' ||
    String(ui.search || '').trim() !== '';

  // 1) 전체 공개 모임 1회 조회 (기본 지도·목록 표시)
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

  // 2) 필터가 적용된 경우 서버 API(GET /api/groups?region=&purpose=&q=) 조회 + 시/도·시/군/구·읍/면/동 정밀 매칭
  useEffect(() => {
    const searchChanged = prevSearchRef.current !== ui.search;
    prevSearchRef.current = ui.search;

    if (!hasActiveFilter) {
      setFilterStatus('ready');
      setFilterError('');
      return;
    }

    let cancelled = false;
    setFilterStatus('loading');
    setFilterError('');

    const regionFilter = {
      sido: regionSido,
      sigungu: regionSigungu,
      dong: regionDong,
    };
    const serverRegionQuery = buildServerRegionQuery(regionFilter);

    const runFetch = () => {
      groups
        .fetchPublicGroups({
          region: serverRegionQuery || '전체',
          purpose: ui.purpose,
          q: ui.search,
        })
        .then((items) => {
          if (cancelled) return;
          const matched = items.filter((g) => matchesRegionFilter(g, regionFilter));
          setFilteredGroups(matched);
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
  }, [hasActiveFilter, regionSido, regionSigungu, regionDong, ui.purpose, ui.search, reloadTick]);

  const reload = useCallback(() => {
    setReloadTick((n) => n + 1);
  }, []);

  useEffect(() => {
    if (!modal) return undefined;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        setModal(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [modal]);

  const list = hasActiveFilter ? filteredGroups : allGroups;
  const listStatus = hasActiveFilter
    ? (allStatus === 'error' && filterStatus !== 'ready' ? 'error' : filterStatus)
    : allStatus;
  const listError = hasActiveFilter ? (filterError || allError) : allError;

  // 핀 배지 숫자와 모달 내 목록 건수가 항상 일치하도록 현재 필터링된 list 기준으로 모달 목록 구성
  const modalList = useMemo(() => {
    if (!modal?.region) return list;
    return list.filter((g) => {
      if (modal.pinKey) {
        return resolvePinMeta(g).key === modal.pinKey;
      }
      return g.regionSigungu === modal.region || g.region.includes(modal.region);
    });
  }, [modal, list]);

  const chosen = useMemo(() => {
    const inFiltered = list.find((g) => g.id === ui.focus);
    if (inFiltered) return inFiltered;
    return list[0] || null;
  }, [list, ui.focus]);

  const resetFilters = () => {
    setUi({
      region: '전체',
      regionSido: '전체',
      regionSigungu: '전체',
      regionDong: '전체',
      purpose: '전체',
      search: '',
    });
  };

  const select = (id) => {
    setSim(null);
    setModal(null);
    const inCurrentList = list.some((g) => g.id === id);
    if (!inCurrentList) {
      setUi({
        region: '전체',
        regionSido: '전체',
        regionSigungu: '전체',
        regionDong: '전체',
        purpose: '전체',
        search: '',
        focus: id,
      });
    } else {
      setUi({ focus: id });
    }
  };

  return (
    <>
      <PageHead
        kicker="LOCAL COMMUNITY"
        title="지역 계모임 찾기 · 지도"
        sub="시/도 · 시/군/구 · 읍/면/동을 선택하거나 지도 핀을 눌러 해당 지역의 공개 모임을 확인하세요."
      />
      <Card>
        <div className="form-row">
          <RegionSelector
            mode="filter"
            namePrefix="mapFilter"
            sido={regionSido}
            sigungu={regionSigungu}
            dong={regionDong}
            onChange={({ sido, sigungu, dong }) =>
              setUi({
                regionSido: sido,
                regionSigungu: sigungu,
                regionDong: dong,
                region: dong !== '전체' ? dong : sigungu !== '전체' ? sigungu : sido,
              })
            }
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
            onClick={resetFilters}
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
              <div>
                <h2>우리 동네 계모임 지도</h2>
                <p className="muted mini" aria-live="polite">
                  {listStatus === 'loading'
                    ? '공개 모임을 검색하는 중이에요...'
                    : hasActiveFilter
                      ? `검색 결과 ${list.length}건`
                      : `공개 모임 총 ${list.length}건`}
                </p>
              </div>
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
                regionFilter={{ sido: regionSido, sigungu: regionSigungu, dong: regionDong }}
                onSelectRegion={(region, _regionGroups, pinKey) => setModal({ region, pinKey })}
              />
            )}
            <p className="muted mini">
              {listStatus === 'loading'
                ? '공개 모임 목록을 불러오고 있어요...'
                : '핀의 숫자는 해당 동(또는 시/군/구)에 등록된 공개 모임 수입니다.'}
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
                {modal?.region ? `${modal.region} 모임` : '모임 목록'}{' '}
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
