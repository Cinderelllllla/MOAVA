/* S07 계모임 시뮬레이터 — /simulator · F14 (5만 원 단위 · 2~30명 · 원 단위 결과 · 시중 적금 금리 비교) */
import { useEffect, useState } from 'react';
import { useApp } from '../common/store';
import { finance } from '../api';
import { PageHead, Card } from '../components/ui';
import { krw, signed } from '../common/format';

function snapHalfPercent(rate) {
  const snapped = Math.round(Number(rate) * 2) / 2;
  return Math.max(0, Math.min(20, snapped));
}

export default function Simulator() {
  const { ui, setUi } = useApp();
  const s = ui.sim;
  const a = Number(s.amount), m = Number(s.members), r = Number(s.round), rate = Number(s.rate);
  const hasRate = String(s.rate ?? '').trim() !== '';
  const valid =
    Number.isInteger(a) && a >= 5 && a % 5 === 0 &&
    Number.isInteger(m) && m >= 2 && m <= 30 &&
    Number.isInteger(r) && r >= 1 && r <= m &&
    hasRate && Number.isFinite(rate) && rate >= 0 && rate <= 20;

  const [savingState, setSavingState] = useState({ status: 'idle', data: null, error: '' });

  useEffect(() => {
    if (!Number.isInteger(m) || m < 2 || m > 30) {
      setSavingState({ status: 'idle', data: null, error: '' });
      return undefined;
    }
    let alive = true;
    setSavingState((prev) => ({ status: 'loading', data: prev.data, error: '' }));
    finance.getSavingComparison(m)
      .then((data) => {
        if (!alive) return;
        setSavingState({ status: 'ready', data, error: '' });
      })
      .catch((err) => {
        if (!alive) return;
        setSavingState({
          status: 'error',
          data: null,
          error: err?.message || '시중 적금 금리 정보를 불러오지 못했어요.',
        });
      });
    return () => { alive = false; };
  }, [m]);

  const total = a * m, shift = finance.payoutShift(a, m, r, rate);
  const expectedPayout = total + shift;
  const saving = savingState.data;
  const avgMaturity = saving?.available && saving.avgBasicRate != null
    ? finance.savingMaturity(a, m, saving.avgBasicRate)
    : null;
  const topMaturity = saving?.available && saving.topMaxRate != null
    ? finance.savingMaturity(a, m, saving.topMaxRate)
    : null;

  const applyRate = (nextRate) => {
    if (!Number.isFinite(Number(nextRate))) return;
    setUi({ sim: { ...s, rate: String(snapHalfPercent(nextRate)) } });
  };

  const F = (label, key, props) => (
    <div className="field">
      <label>{label}</label>
      <input
        type="number"
        value={s[key]}
        onChange={(e) => setUi({ sim: { ...s, [key]: e.target.value } })}
        {...props}
      />
    </div>
  );

  return (
    <>
      <PageHead
        kicker="PAYOUT SIMULATOR"
        title="계모임 시뮬레이터"
        sub="월 납입액은 5만 원 단위, 결과는 원 단위로 표시하며 시중 적금 금리와도 비교할 수 있어요."
      />
      <Card>
        <div className="form-grid">
          {F('월 납입액 (만 원)', 'amount', { min: 5, step: 5 })}
          {F('참여 인원', 'members', { min: 2, max: 30 })}
          {F('희망 수령 회차', 'round', { min: 1, max: m })}
          {F('가정 연이율 (%)', 'rate', { min: 0, max: 20, step: 0.5 })}
        </div>
        <p className="muted mini">
          {valid
            ? '가정 계산 결과이며 실제 지급액이나 은행 이자가 아닙니다.'
            : '월 납입액은 5만 원 단위, 인원 2~30명, 회차는 인원 이내, 이율 0~20%로 입력하세요.'}
        </p>
        {valid && (
          <>
            <div className="grid cols3 section">
              {[
                ['기본 수령액', krw(total)],
                ['보정액', signed(shift, krw)],
                ['예상 수령액', krw(expectedPayout)],
              ].map(([t, v]) => (
                <Card key={t}>
                  <small>{t}</small>
                  <div className="metric" style={{ fontVariantNumeric: 'tabular-nums' }}>{v}</div>
                </Card>
              ))}
            </div>

            <div className="section">
              <div className="item-head" style={{ marginBottom: 8 }}>
                <h3>시중 적금 금리 비교</h3>
                {saving?.available && (
                  <div className="actions" style={{ marginTop: 0 }}>
                    {saving.avgBasicRate != null && (
                      <button
                        type="button"
                        className="btn small soft"
                        onClick={() => applyRate(saving.avgBasicRate)}
                      >
                        평균 금리 적용 ({snapHalfPercent(saving.avgBasicRate)}%)
                      </button>
                    )}
                    {saving.topMaxRate != null && (
                      <button
                        type="button"
                        className="btn small soft"
                        onClick={() => applyRate(saving.topMaxRate)}
                      >
                        최고 금리 적용 ({snapHalfPercent(saving.topMaxRate)}%)
                      </button>
                    )}
                  </div>
                )}
              </div>

              {savingState.status === 'loading' && !saving && (
                <p className="muted mini" role="status">시중 적금 금리를 불러오는 중이에요...</p>
              )}

              {savingState.status === 'error' && (
                <p className="muted mini">{savingState.error} 계모임 시뮬레이션은 그대로 이용할 수 있어요.</p>
              )}

              {savingState.status === 'ready' && !saving?.available && (
                <p className="muted mini">
                  비교할 시중 적금 금리 데이터(PRODUCTS_SAVING)가 아직 등록되지 않았어요.
                </p>
              )}

              {saving?.available && (
                <>
                  <p className="muted mini" style={{ marginBottom: 10 }}>
                    금감원 적금 금리 캐시 ({saving.matchedTermMonths ?? m}개월 만기 기준 · {saving.productCount}개 상품
                    {saving.fetchedAt ? ` · 기준일 ${saving.fetchedAt}` : ''}) — 동일 월 {krw(a)} · {m}개월 납입(세전 단리 참고치)
                  </p>
                  <div className="grid cols3">
                    <Card>
                      <small>계모임 예상 ({r}회차 · 연 {rate}%)</small>
                      <div className="metric" style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {krw(expectedPayout)}
                      </div>
                    </Card>
                    <Card>
                      <small>시중 적금 평균 (연 {saving.avgBasicRate?.toFixed(2)}%)</small>
                      <div className="metric" style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {avgMaturity ? krw(avgMaturity.maturity) : '-'}
                      </div>
                      {avgMaturity && (
                        <small className="muted">
                          계모임 대비 {signed(expectedPayout - avgMaturity.maturity, krw)}
                        </small>
                      )}
                    </Card>
                    <Card>
                      <small>시중 적금 최고 우대 (연 {saving.topMaxRate?.toFixed(2)}%)</small>
                      <div className="metric" style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {topMaturity ? krw(topMaturity.maturity) : '-'}
                      </div>
                      {topMaturity && (
                        <small className="muted">
                          계모임 대비 {signed(expectedPayout - topMaturity.maturity, krw)}
                        </small>
                      )}
                    </Card>
                  </div>

                  {saving.topProducts.length > 0 && (
                    <div className="tablewrap" style={{ marginTop: 12 }}>
                      <table>
                        <thead>
                          <tr>
                            <th>은행</th>
                            <th>상품명</th>
                            <th>만기</th>
                            <th>기본 금리</th>
                            <th>최고 우대</th>
                            <th>만기 예상액 (세전)</th>
                          </tr>
                        </thead>
                        <tbody>
                          {saving.topProducts.map((p, idx) => {
                            const mat = finance.savingMaturity(a, m, p.rateMax);
                            return (
                              <tr key={`${p.bankName}-${p.productName}-${idx}`}>
                                <td>{p.bankName}</td>
                                <td>{p.productName}</td>
                                <td>{p.termMonths}개월</td>
                                <td>연 {p.rateBasic.toFixed(2)}%</td>
                                <td><b>연 {p.rateMax.toFixed(2)}%</b></td>
                                <td style={{ fontVariantNumeric: 'tabular-nums' }}>{krw(mat.maturity)}</td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  )}
                </>
              )}
            </div>

            <p className="info">실제 금융 이자 또는 계약 지급액이 아닌 예상 수치입니다.</p>
            <div className="tablewrap">
              <table>
                <thead>
                  <tr>
                    <th>회차</th>
                    <th>기본 수령액</th>
                    <th>보정액</th>
                    <th>예상 수령액</th>
                  </tr>
                </thead>
                <tbody>
                  {Array.from({ length: m }, (_, i) => {
                    const rr = i + 1, adj = finance.payoutShift(a, m, rr, rate);
                    return (
                      <tr key={rr}>
                        <td>
                          {rr}회차 {rr === r ? <span className="pill mine" style={{ marginLeft: 6 }}>희망 회차</span> : ''}
                        </td>
                        <td>{krw(total)}</td>
                        <td>{signed(adj, krw)}</td>
                        <td>{krw(total + adj)}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Card>
    </>
  );
}
