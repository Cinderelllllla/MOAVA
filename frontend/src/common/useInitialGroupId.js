/* 알림 링크의 ?groupId= 를 화면 첫 진입 때 한 번만 적용하는 훅 (Operations · Evidence 공용) */
import { useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * 반환값 pickGroup(list): 모임 목록이 (다시) 로드됐을 때 setState에 넘길 업데이트 함수를 만든다.
 *   setSelectedId(pickGroup(list))
 * - 첫 로드에서만 URL의 groupId를 우선 적용하고, 이후에는 사용자가 고른 모임을 유지한다.
 * - 고른 모임이 목록에 없으면 첫 번째 모임.
 * (URL 값 소비는 업데이트 함수 밖에서 해서 StrictMode의 이중 호출에도 결과가 같다)
 */
export function useInitialGroupId() {
  const [params] = useSearchParams();
  const pending = useRef(params.get('groupId'));
  return useCallback(list => {
    const ids = (list || []).map(g => String(g.groupId));
    const want = pending.current;
    pending.current = null;
    const initial = want && ids.includes(want) ? want : null;
    return current => initial ?? (ids.includes(String(current)) ? String(current) : (ids[0] ?? ''));
  }, []);
}
