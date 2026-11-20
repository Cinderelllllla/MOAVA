import { useCallback, useEffect, useState } from 'react';
import { groups } from '../api';

/**
 * 내 계모임 목록(서버 GET /api/groups/my, 지금은 "내가 만든 모임"만 옴).
 * 반환: { status: 'loading' | 'ready' | 'error', items, error, reload }
 * 실패해도 목업 데이터로 대체하지 않는다. reload() 로 페이지 새로고침 없이 다시 불러온다.
 */
export default function useMyGroups() {
  const [state, setState] = useState({ status: 'loading', items: [], error: '' });
  const [key, setKey] = useState(0);

  useEffect(() => {
    let alive = true;
    setState(s => ({ ...s, status: 'loading', error: '' }));
    groups.fetchMyGroups()
      .then(items => { if (alive) setState({ status: 'ready', items, error: '' }); })
      .catch(e => { if (alive) setState({ status: 'error', items: [], error: e.message || '내 계모임을 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [key]);

  const reload = useCallback(() => setKey(k => k + 1), []);
  return { ...state, reload };
}
