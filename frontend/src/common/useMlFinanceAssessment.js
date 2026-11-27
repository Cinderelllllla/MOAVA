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
  // 금융정보를 다시 저장하면 saveFinance 가 분석 결과를 비운다. 희망 납입액(goal)처럼 분석에 안 쓰는 값만 바뀌어도
  // 결과가 비워지므로, 결과가 없어지면 아래 effect 가 다시 돌아 분석을 새로 하도록 의존값에 넣는다.
  const hasAssessment = Boolean(finance.getMlAssessment());
  // 금액은 그대로 두고 신용 질문 답만 새로 해도 다시 분석하도록
  const selfReportVersion = finance.getSelfReportVersion();

  useEffect(() => {
    if (!profile.consentAt) {
      setState({ status: 'idle', error: '' });
      return undefined;
    }
    if (!Number.isSafeInteger(memberId) || memberId < 1) {
      setState({ status: 'error', error: '분석에 사용할 회원 번호를 확인할 수 없습니다.' });
      return undefined;
    }

    if (finance.getMlAssessment()) { setState({ status: 'success', error: '' }); return undefined; }
    // 위험 등급은 자가 응답 4문항이 있어야 계산할 수 있다 (답변은 저장하지 않으므로 새로고침 뒤에는 다시 답해야 함)
    if (!finance.getSelfReport()) { setState({ status: 'needs-answers', error: '' }); return undefined; }

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
    hasAssessment,
    selfReportVersion,
  ]);

  return { ...state, retry: () => setRetryKey(value => value + 1) };
}
