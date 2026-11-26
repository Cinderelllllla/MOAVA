/* S09 계모임 만들기 — /groups/new · F15 F19 (납입일 필수 · 5만 원 단위 · 2~30명 · 모임 계좌 필수) */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { groups } from '../api';
import { PageHead, Card, Input, Select, Field } from '../components/ui';
import RegionSelector from '../components/RegionSelector';
import { parseRegionFromServer } from '../common/regions';

export default function GroupNew() {
  const { user, run } = useApp();
  const nav = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [f, setF] = useState(() => {
    const defaultRegion = parseRegionFromServer(
      user?.regionSido,
      user?.regionSigungu,
      user?.regionDong
    );
    return {
      title: '',
      purpose: '목돈 마련',
      regionSido: defaultRegion.sido,
      regionSigungu: defaultRegion.sigungu,
      regionDong: defaultRegion.dong,
      amount: 20,
      payDay: 5,
      max: 6,
      months: 6,
      rate: 3,
      rulesTitle: '우리 모임 운영 규칙',
      description: '',
      rules: '',
      bank: '',
      accountNo: '',
      visibility: 'PUBLIC',
      overduePolicy: '5일차 알림'
    };
  });
  const on = k => e => setF(prev => ({ ...prev, [k]: e.target.value }));
  const submit = async (e) => {
    e.preventDefault();

    if (isSubmitting) return;

    setIsSubmitting(true);
    setSubmitError('');

    // run 이 오류를 토스트로 보여 주고 { ok:false, error } 를 돌려준다 → 폼 아래에도 같은 문구를 남긴다
    const r = await run(
      () => groups.createGroup(f),
      '계모임을 생성했어요. 이제 해당 모임의 모임장이에요.'
    );
    setIsSubmitting(false);

    if (!r.ok) {
      setSubmitError(r.error?.message || '모임을 만들지 못했어요. 입력값을 확인해 주세요.');
      return;
    }

    nav('/my-groups');
  };
  return <><PageHead kicker="CREATE A GROUP" title="새 계모임 만들기" sub="일반 회원도 모임을 만들 수 있고, 생성 즉시 해당 모임의 모임장이 됩니다." />
    <Card><form onSubmit={submit}>
      <div className="form-grid">
        <Input label="모임 이름" name="title" required value={f.title} onChange={on('title')} />
        <Select label="모임 목적" name="purpose" options={['목돈 마련', '재테크', '친목', '창업', '여행']} value={f.purpose} onChange={on('purpose')} />
        <RegionSelector
          mode="required"
          namePrefix="groupRegion"
          labels={{ sido: '모임 지역 · 시/도', sigungu: '모임 지역 · 시/군/구', dong: '모임 지역 · 읍/면/동' }}
          sido={f.regionSido}
          sigungu={f.regionSigungu}
          dong={f.regionDong}
          disabled={isSubmitting}
          onChange={({ sido, sigungu, dong }) =>
            setF(prev => ({ ...prev, regionSido: sido, regionSigungu: sigungu, regionDong: dong }))
          }
        />
        <Input label="월 납입액 (만 원 · 5만 원 단위)" name="amount" type="number" min={5} step={5} required value={f.amount} onChange={on('amount')} />
        <Input label="매월 납입일 (1~28일)" name="payDay" type="number" min={1} max={28} required value={f.payDay} onChange={on('payDay')} />
        <Input label="최대 인원 (2~30명)" name="max" type="number" min={2} max={30} required value={f.max} onChange={on('max')} />
        <Input label="운영 기간 (개월)" name="months" type="number" min={2} max={36} required value={f.months} onChange={on('months')} />
        <Input label="가정 연이율 (%) · 시뮬레이터용" name="rate" type="number" min={0} max={20} step={0.5} value={f.rate} onChange={on('rate')} />
        <Input label="운영 규칙 제목" name="rulesTitle" required value={f.rulesTitle} onChange={on('rulesTitle')} />
        <Field label="모임 소개"><textarea required placeholder="모임 소개를 입력해 주세요." value={f.description} onChange={on('description')} /></Field>
      </div>
      <div className="field" style={{ marginTop: 16 }}><label>운영 규칙</label><textarea required placeholder="채팅에서 협의한 규칙을 정리해 주세요." value={f.rules} onChange={on('rules')} /></div>
      {/* TODO(MOA-69): 서버 API 연결 시 준비 중 안내 제거 — 계좌 소유 확인(1원 인증) */}
      <div className="policy-panel"><h3>모임 계좌 등록 · 모임장 전용</h3><p className="muted mini">본인 명의 계좌를 등록합니다. 계좌 소유 확인(1원 인증)은 아직 준비 중이라, 등록 후에는 '인증 대기' 상태로 표시돼요.</p>
        <div className="policy-grid"><Field label="은행명"><input required placeholder="예: 카카오뱅크" value={f.bank} onChange={on('bank')} /></Field><Field label="예금주"><input value={user.name} readOnly /></Field><Field label="계좌번호"><input required inputMode="numeric" placeholder="숫자만 입력" value={f.accountNo} onChange={on('accountNo')} /></Field><Select label="모임 공개 설정" name="visibility" options={[['PUBLIC', '공개 모임 (지도·검색·추천 노출)'], ['PRIVATE', '비공개 모임 (초대 링크 + 인증코드)']]} value={f.visibility} onChange={on('visibility')} /><Select label="미납 대응 규칙" name="overduePolicy" options={['5일차 알림', '5일차 알림 + 채팅 제한·수령 후순위 검토']} value={f.overduePolicy} onChange={on('overduePolicy')} /></div></div>
      <div className="actions" style={{ marginTop: 18 }}>
        <button className="btn primary" disabled={isSubmitting}>
          {isSubmitting ? '만드는 중…' : '계모임 생성'}
        </button>
      </div>
      {submitError && <p className="warning mini" role="alert" style={{ marginTop: 12 }}>{submitError}</p>}
    </form></Card></>;
}
