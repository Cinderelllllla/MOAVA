import { useEffect, useState } from 'react';
import { finance } from '../api';
import { getAccessToken, getAccessTokenMemberId } from '../api/auth';
import { useApp } from './store';

export default function useMlFinanceAssessment() {
  const { session } = useApp();
  const profile = finance.getFinance();
  const token = getAccessToken();
  const serverMemberId = token ? getAccessTokenMemberId(token) : null;
  const memberId = serverMemberId ?? Number(session?.id);
  const [state, setState] = useState({ status: 'idle', error: '' });
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    if (!profile.consentAt) {
      setState({ status: 'idle', error: '' });
      return undefined;
    }
    if (!Number.isSafeInteger(memberId) || memberId < 1) {
      setState({ status: 'error', error: '분석에 사용할 회원 번호를 확인할 수 없습니다.' });
      return undefined;
    }

    let active = true;
    setState({ status: 'loading', error: '' });
    finance.assessWithMl(memberId)
      .then(() => { if (active) setState({ status: 'success', error: '' }); })
      .catch(error => {
        if (active) setState({ status: 'error', error: error.message || 'ML 분석에 실패했습니다.' });
      });

    return () => { active = false; };
  }, [
    memberId,
    profile.consentAt,
    profile.income,
    profile.fixed,
    profile.variable,
    profile.saving,
    profile.reserve,
    token,
    retryKey,
  ]);

  return { ...state, retry: () => setRetryKey(value => value + 1) };
}
