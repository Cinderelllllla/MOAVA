/* S07 계모임 시뮬레이터 — /simulator · F14 (5만 원 단위 · 2~30명 · 원 단위 결과) */
import { useApp } from '../common/store';
import { finance } from '../api';
import { PageHead, Card } from '../components/ui';
import { krw, signed } from '../common/format';

export default function Simulator() {
  const { ui, setUi } = useApp(); const s = ui.sim;
  const a = Number(s.amount), m = Number(s.members), r = Number(s.round), rate = Number(s.rate);
  const valid = Number.isInteger(a) && a >= 5 && a % 5 === 0 && Number.isInteger(m) && m >= 2 && m <= 30 && Number.isInteger(r) && r >= 1 && r <= m && rate >= 0 && rate <= 20;
  const total = a * m, shift = finance.payoutShift(a, m, r, rate);
  const F = (label, key, props) => <div className="field"><label>{label}</label><input type="number" value={s[key]} onChange={e => setUi({ sim: { ...s, [key]: e.target.value } })} {...props} /></div>;
  return <><PageHead kicker="GYE SIMULATOR" title="계모임 시뮬레이터" sub="월 납입액은 5만 원 단위, 결과는 원 단위로 표시합니다." />
    <Card><div className="form-grid">{F('월 납입액 (만 원)', 'amount', { min: 5, step: 5 })}{F('참여 인원', 'members', { min: 2, max: 30 })}{F('희망 수령 회차', 'round', { min: 1, max: m })}{F('가정 연이율 (%)', 'rate', { min: 0, max: 20, step: 0.5 })}</div>
      <p className="muted mini">{valid ? '가정 계산 결과이며 실제 지급액이나 은행 이자가 아닙니다.' : '월 납입액은 5만 원 단위, 인원 2~30명, 회차는 인원 이내, 이율 0~20%로 입력하세요.'}</p>
      {valid && <><div className="grid cols3 section">{[['기본 수령액', krw(total)], ['보정액', signed(shift, krw)], ['예상 수령액', krw(total + shift)]].map(([t, v]) => <Card key={t}><small>{t}</small><div className="metric" style={{ fontVariantNumeric: 'tabular-nums' }}>{v}</div></Card>)}</div>
        <p className="info">보정액 = (수령 회차 − 중간 회차) × 기본 수령액 × (연이율 ÷ 100) ÷ 인원. 조기 수령자는 차감, 후기 수령자는 가산되며 전체 합계는 0인 <b>교육용 모형</b>입니다. 실제 금융 이자 또는 계약 지급액이 아닙니다.</p>
        <div className="tablewrap"><table><thead><tr><th>회차</th><th>기본 수령액</th><th>보정액</th><th>예상 수령액</th></tr></thead><tbody>{Array.from({ length: m }, (_, i) => { const rr = i + 1, adj = finance.payoutShift(a, m, rr, rate); return <tr key={rr}><td>{rr}회차 {rr === r ? '☁' : ''}</td><td>{krw(total)}</td><td>{signed(adj, krw)}</td><td>{krw(total + adj)}</td></tr>; })}</tbody></table></div></>}</Card></>;
}
