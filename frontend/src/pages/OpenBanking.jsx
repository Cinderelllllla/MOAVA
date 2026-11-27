/* 오픈뱅킹 · 간편 은행 연결(1원 4자리 인증) + 가상은행(KFTC 모의 원장) + CODEF 실계좌인증 통합 화면 (/openbanking) */
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../common/store';
import { openbanking } from '../api';
import { formatKrw } from '../common/format';
import { Card, Empty, Field, Input, PageHead, Pill, Select } from '../components/ui';
import Icon from '../components/Icon';

function fmtDateTime(val) {
  if (!val) return '-';
  return String(val).replace('T', ' ').slice(0, 16);
}

function fmtYmd(ymd, hms) {
  if (!ymd || ymd.length !== 8) return ymd || '-';
  const d = `${ymd.slice(0, 4)}-${ymd.slice(4, 6)}-${ymd.slice(6, 8)}`;
  if (!hms || hms.length < 4) return d;
  return `${d} ${hms.slice(0, 2)}:${hms.slice(2, 4)}`;
}

function fmtYm(ym) {
  if (!ym || ym.length !== 6) return ym || '-';
  return `${ym.slice(0, 4)}년 ${Number(ym.slice(4, 6))}월`;
}

export default function OpenBanking() {
  const { user, run, toast } = useApp();
  const nav = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const initialTab = ['quick', 'ledger', 'transfer', 'codef'].includes(searchParams.get('tab'))
    ? searchParams.get('tab')
    : 'quick';
  const [tab, setTab] = useState(initialTab);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [banks, setBanks] = useState([]);
  const [overview, setOverview] = useState(null);
  const [busyKey, setBusyKey] = useState('');

  // Tab 1 (기본): 간편 은행 연결 폼 (은행명·계좌번호·소유주·전화번호 -> 해당 은행 인증 화면 이동)
  const [quickForm, setQuickForm] = useState({
    organization: '0004',
    accountNo: '',
    holderName: user?.name || '',
    phone: '',
    initialMockBalance: '',
    leaderAccount: 'auto',
  });
  const [showQuickAdvanced, setShowQuickAdvanced] = useState(false);

  // Tab 4 (고급): CODEF 연결 폼
  const [connectForm, setConnectForm] = useState({
    organization: '0004',
    loginType: '0',
    certType: '1',
    id: '',
    password: '',
    birthDate: '',
  });
  const [certFiles, setCertFiles] = useState({ der: null, key: null, pfx: null });

  // Tab 4: CODEF 보유 계좌 조회 + 실계좌 인증 폼
  const [codefAccountsRes, setCodefAccountsRes] = useState(null);
  const [verifyForm, setVerifyForm] = useState({
    organization: '0004',
    account: '',
    accountPassword: '',
    birthDate: '',
    startDate: '',
    endDate: '',
    syncToMockBank: true,
    initialMockBalance: '',
    leaderAccount: 'auto', // 'auto' | 'true' | 'false'
  });
  const [verifyResult, setVerifyResult] = useState(null);

  // Tab 4: CODEF 실거래 직접 조회
  const [codefTxForm, setCodefTxForm] = useState({
    organization: '0004',
    account: '',
    startDate: '',
    endDate: '',
    orderBy: '0',
  });
  const [codefTxRes, setCodefTxRes] = useState(null);

  // Tab 2: 모의은행 원장 상세 + 시연 충전 + 재동기화
  const [selectedFinNum, setSelectedFinNum] = useState('');
  const [txFilter, setTxFilter] = useState({ inquiryType: 'A', sortOrder: 'D', fromDate: '', toDate: '' });
  const [accountTxRes, setAccountTxRes] = useState(null);
  const [depositForm, setDepositForm] = useState({ fintechUseNum: '', amount: '100000', printContent: '모의은행 시연 충전' });
  const [syncForm, setSyncForm] = useState({
    organization: '',
    refreshFromCodef: false,
    initialMockBalance: '',
    leaderAccount: 'auto',
  });

  // Tab 3: 회비 이체
  const [transferForm, setTransferForm] = useState({
    fromFintechUseNum: '',
    toFintechUseNum: '',
    amount: '50000',
    wdPrintContent: '모임 회비 납부',
    dpsPrintContent: '',
  });
  const [transferReceipt, setTransferReceipt] = useState(null);

  const changeTab = nextTab => {
    setTab(nextTab);
    setSearchParams(nextTab === 'quick' ? {} : { tab: nextTab }, { replace: true });
  };

  const loadAll = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const [bankList, ov] = await Promise.all([
        openbanking.fetchSupportedBanks(),
        openbanking.fetchOverview(),
      ]);
      const safeBanks = Array.isArray(bankList) ? bankList : [];
      setBanks(safeBanks);
      setOverview(ov);

      if (ov?.memberName) {
        setQuickForm(f => (f.holderName ? f : { ...f, holderName: ov.memberName }));
      }

      const firstConnOrg = ov?.connections?.[0]?.organization || safeBanks[0]?.organization || '0004';
      const verifiedConnOrg = ov?.connections?.find(c => c.verified)?.organization || firstConnOrg;
      setVerifyForm(f => ({ ...f, organization: f.organization || firstConnOrg }));
      setCodefTxForm(f => ({ ...f, organization: f.organization || verifiedConnOrg }));
      setSyncForm(f => ({ ...f, organization: f.organization || verifiedConnOrg }));

      const firstFin = ov?.accounts?.[0]?.fintechUseNum || '';
      const firstLeader = ov?.leaderTargets?.[0]?.fintechUseNum || '';
      setSelectedFinNum(prev => (prev && ov?.accounts?.some(a => a.fintechUseNum === prev) ? prev : firstFin));
      setDepositForm(f => ({
        ...f,
        fintechUseNum: f.fintechUseNum && ov?.accounts?.some(a => a.fintechUseNum === f.fintechUseNum) ? f.fintechUseNum : firstFin,
      }));
      setTransferForm(f => ({
        ...f,
        fromFintechUseNum: f.fromFintechUseNum && ov?.accounts?.some(a => a.fintechUseNum === f.fromFintechUseNum) ? f.fromFintechUseNum : firstFin,
        toFintechUseNum: f.toFintechUseNum && ov?.leaderTargets?.some(t => t.fintechUseNum === f.toFintechUseNum) ? f.toFintechUseNum : firstLeader,
      }));
    } catch (e) {
      setLoadError(e?.message || '오픈뱅킹 정보를 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const loadSelectedTransactions = useCallback(async (finNum, filter = txFilter) => {
    if (!finNum) {
      setAccountTxRes(null);
      return;
    }
    setBusyKey(`tx:${finNum}`);
    const r = await run(() => openbanking.fetchAccountTransactions(finNum, filter));
    setBusyKey('');
    if (r.ok) setAccountTxRes(r.value);
  }, [run, txFilter]);

  useEffect(() => {
    if (tab === 'ledger' && selectedFinNum) {
      loadSelectedTransactions(selectedFinNum);
    }
  }, [tab, selectedFinNum]); // eslint-disable-line react-hooks/exhaustive-deps

  // ==================== Tab 1 핸들러 ====================

  const handleConnect = async e => {
    e.preventDefault();
    setBusyKey('connect');
    try {
      const payload = {
        organization: connectForm.organization,
        loginType: connectForm.loginType,
        password: connectForm.password,
        ...(connectForm.birthDate.trim() ? { birthDate: connectForm.birthDate.trim() } : {}),
      };
      if (connectForm.loginType === '0') {
        payload.certType = connectForm.certType;
        if (connectForm.certType === 'pfx') {
          if (!certFiles.pfx) {
            toast('PFX(.p12/.pfx) 인증서 파일을 선택해 주세요.');
            setBusyKey('');
            return;
          }
          payload.certFile = await openbanking.fileToBase64(certFiles.pfx);
        } else {
          if (!certFiles.der || !certFiles.key) {
            toast('공동인증서 signCert.der 파일과 signPri.key 파일을 모두 선택해 주세요.');
            setBusyKey('');
            return;
          }
          payload.derFile = await openbanking.fileToBase64(certFiles.der);
          payload.keyFile = await openbanking.fileToBase64(certFiles.key);
        }
      } else {
        payload.id = connectForm.id.trim();
      }

      const r = await run(
        () => openbanking.connectBank(payload),
        res => `${res.bankName} 계정 연결이 완료되었어요. (${res.maskedConnectedId || 'connectedId 발급'})`,
      );
      if (r.ok) {
        setConnectForm(f => ({ ...f, password: '' }));
        setCertFiles({ der: null, key: null, pfx: null });
        setVerifyForm(f => ({ ...f, organization: r.value.organization }));
        await loadAll();
      }
    } finally {
      setBusyKey('');
    }
  };

  const handleFetchCodefAccounts = async org => {
    const targetOrg = org || verifyForm.organization;
    if (!targetOrg) return;
    setBusyKey(`codef-acc:${targetOrg}`);
    const r = await run(
      () => openbanking.fetchCodefAccounts({
        organization: targetOrg,
        ...(verifyForm.birthDate.trim() ? { birthDate: verifyForm.birthDate.trim() } : {}),
      }),
      res => `${res.bankName} 보유 계좌 ${res.count}건을 불러왔어요.`,
    );
    setBusyKey('');
    if (r.ok) {
      setCodefAccountsRes(r.value);
      setVerifyForm(f => ({ ...f, organization: r.value.organization }));
    }
  };

  const handleVerifyAccount = async e => {
    e.preventDefault();
    setBusyKey('verify');
    const payload = {
      organization: verifyForm.organization,
      account: verifyForm.account.trim(),
      syncToMockBank: verifyForm.syncToMockBank,
      ...(verifyForm.accountPassword.trim() ? { accountPassword: verifyForm.accountPassword.trim() } : {}),
      ...(verifyForm.birthDate.trim() ? { birthDate: verifyForm.birthDate.trim() } : {}),
      ...(verifyForm.startDate.trim() ? { startDate: verifyForm.startDate.trim() } : {}),
      ...(verifyForm.endDate.trim() ? { endDate: verifyForm.endDate.trim() } : {}),
      ...(verifyForm.initialMockBalance !== '' ? { initialMockBalance: Number(verifyForm.initialMockBalance) } : {}),
      ...(verifyForm.leaderAccount !== 'auto' ? { leaderAccount: verifyForm.leaderAccount === 'true' } : {}),
    };
    const r = await run(
      () => openbanking.verifyRealAccount(payload),
      res => res.message || `${res.bankName} 실계좌 인증이 완료되었어요.`,
    );
    setBusyKey('');
    if (r.ok) {
      setVerifyResult(r.value);
      setVerifyForm(f => ({ ...f, accountPassword: '' }));
      await loadAll();
    }
  };

  const handleFetchCodefTx = async e => {
    e.preventDefault();
    setBusyKey('codef-tx');
    const payload = {
      organization: codefTxForm.organization,
      orderBy: codefTxForm.orderBy,
      ...(codefTxForm.account.trim() ? { account: codefTxForm.account.trim() } : {}),
      ...(codefTxForm.startDate.trim() ? { startDate: codefTxForm.startDate.trim() } : {}),
      ...(codefTxForm.endDate.trim() ? { endDate: codefTxForm.endDate.trim() } : {}),
    };
    const r = await run(
      () => openbanking.fetchCodefTransactions(payload),
      res => `${res.bankName} 실거래내역 ${res.count}건을 조회했어요.`,
    );
    setBusyKey('');
    if (r.ok) setCodefTxRes(r.value);
  };

  const handleDisconnect = async conn => {
    if (!window.confirm(`${conn.bankName} CODEF 연결을 해제할까요? (원격 계정 정리 후 로컬 연결이 삭제됩니다.)`)) return;
    setBusyKey(`disc:${conn.organization}`);
    const r = await run(
      () => openbanking.disconnectBank(conn.organization),
      `${conn.bankName} 연결을 해제했어요.`,
    );
    setBusyKey('');
    if (r.ok) {
      if (codefAccountsRes?.organization === conn.organization) setCodefAccountsRes(null);
      await loadAll();
    }
  };

  // ==================== Tab 2 핸들러 ====================

  const handleToggleLeader = async acc => {
    const next = !acc.leaderAccount;
    setBusyKey(`leader:${acc.fintechUseNum}`);
    const r = await run(
      () => openbanking.updateLeaderFlag(acc.fintechUseNum, next),
      next ? '모임장 수취 계좌로 설정했어요.' : '모임장 수취 계좌 설정을 해제했어요.',
    );
    setBusyKey('');
    if (r.ok) await loadAll();
  };

  const handleCheckBalance = async finNum => {
    setBusyKey(`bal:${finNum}`);
    const r = await run(
      () => openbanking.fetchAccountBalance(finNum),
      res => `${res.bankName} (${res.accountMasked}) 현재 잔액: ${formatKrw(res.balanceAmt)}`,
    );
    setBusyKey('');
    if (r.ok) await loadAll();
  };

  const handleDepositDemo = async e => {
    e.preventDefault();
    if (!depositForm.fintechUseNum) {
      toast('충전할 모의 계좌를 먼저 선택해 주세요.');
      return;
    }
    setBusyKey('deposit');
    const r = await run(
      () => openbanking.depositDemoFunds(depositForm.fintechUseNum, {
        amount: Number(depositForm.amount),
        printContent: depositForm.printContent.trim() || '모의은행 시연 충전',
      }),
      res => `${formatKrw(res.depositAmt)} 충전 완료! 잔액: ${formatKrw(res.afterBalanceAmt)}`,
    );
    setBusyKey('');
    if (r.ok) {
      await loadAll();
      if (selectedFinNum === depositForm.fintechUseNum) {
        await loadSelectedTransactions(selectedFinNum);
      }
    }
  };

  const handleSyncFromCodef = async e => {
    e.preventDefault();
    if (!syncForm.organization) {
      toast('동기화할 은행을 선택해 주세요.');
      return;
    }
    setBusyKey('sync');
    const payload = {
      organization: syncForm.organization,
      refreshFromCodef: syncForm.refreshFromCodef,
      ...(syncForm.initialMockBalance !== '' ? { initialMockBalance: Number(syncForm.initialMockBalance) } : {}),
      ...(syncForm.leaderAccount !== 'auto' ? { leaderAccount: syncForm.leaderAccount === 'true' } : {}),
    };
    const r = await run(
      () => openbanking.syncFromCodef(payload),
      res => `${res.bankName} 모의 계좌 동기화 완료 (거래 ${res.syncedTransactionCount}건 반영)`,
    );
    setBusyKey('');
    if (r.ok) {
      await loadAll();
      if (r.value?.fintechUseNum) {
        setSelectedFinNum(r.value.fintechUseNum);
        await loadSelectedTransactions(r.value.fintechUseNum);
      }
    }
  };

  // ==================== Tab 3 핸들러 ====================

  const handleTransfer = async e => {
    e.preventDefault();
    if (!transferForm.fromFintechUseNum || !transferForm.toFintechUseNum) {
      toast('출금 계좌와 모임장 수취 계좌를 모두 선택해 주세요.');
      return;
    }
    setBusyKey('transfer');
    const payload = {
      fromFintechUseNum: transferForm.fromFintechUseNum,
      toFintechUseNum: transferForm.toFintechUseNum,
      amount: Number(transferForm.amount),
      wdPrintContent: transferForm.wdPrintContent.trim() || '모임 회비 납부',
      ...(transferForm.dpsPrintContent.trim() ? { dpsPrintContent: transferForm.dpsPrintContent.trim() } : {}),
    };
    const r = await run(
      () => openbanking.transferToLeader(payload),
      res => `${res.toHolderNameMasked}님 계좌로 ${formatKrw(res.tranAmt)} 이체가 완료되었어요.`,
    );
    setBusyKey('');
    if (r.ok) {
      setTransferReceipt(r.value);
      await loadAll();
    }
  };

  // ==================== Tab 1 (간편 연결) 핸들러 ====================

  const handleQuickStart = async e => {
    e.preventDefault();
    if (!quickForm.accountNo.trim() || !quickForm.holderName.trim() || !quickForm.phone.trim()) {
      toast('은행명, 계좌번호, 소유주, 전화번호를 모두 입력해 주세요.');
      return;
    }
    setBusyKey('quick-start');
    const payload = {
      organization: quickForm.organization,
      accountNo: quickForm.accountNo.trim(),
      holderName: quickForm.holderName.trim(),
      phone: quickForm.phone.trim(),
      ...(quickForm.initialMockBalance !== '' ? { initialMockBalance: Number(quickForm.initialMockBalance) } : {}),
      ...(quickForm.leaderAccount !== 'auto' ? { leaderAccount: quickForm.leaderAccount === 'true' } : {}),
    };
    const r = await run(
      () => openbanking.startQuickConnect(payload),
      res => `${res.bankName} 1원 인증 화면으로 이동합니다.`,
    );
    setBusyKey('');
    if (r.ok && r.value?.bankAuthUrl) {
      nav(r.value.bankAuthUrl);
    }
  };

  const bankOptions = banks.map(b => [b.organization, `${b.bankName} (${b.organization})`]);
  const connections = overview?.connections || [];
  const accounts = overview?.accounts || [];
  const leaderTargets = overview?.leaderTargets || [];
  const verifiedConnections = connections.filter(c => c.verified);

  return (
    <>
      <PageHead
        kicker="OPEN BANKING · QUICK AUTH"
        title="오픈뱅킹 · 내 계좌 연결 & 가상은행"
        sub="은행명·계좌번호·소유주·전화번호만 입력하면 선택한 은행의 1원 인증 화면으로 바로 이동해 4자리 인증번호 확인 후 계좌를 연결할 수 있어요."
      >
        <button type="button" className="btn soft" onClick={loadAll} disabled={loading}>
          {loading ? '불러오는 중…' : '새로고침'}
        </button>
      </PageHead>

      {loadError && (
        <div className="warning section">
          <strong>오류:</strong> {loadError}{' '}
          <button type="button" className="btn small" style={{ marginLeft: 8 }} onClick={loadAll}>
            다시 시도
          </button>
        </div>
      )}

      {/* 상단 KPI 요약 */}
      <div className="grid cols4">
        <Card className="stat">
          <small>연결 / 인증 완료 은행</small>
          <div className="value">
            {connections.length}개 / {overview?.codefVerifiedCount ?? 0}건
          </div>
          <span className="symbol"><Icon name="shield" size={24} /></span>
        </Card>
        <Card className="stat">
          <small>모의은행 보유 계좌</small>
          <div className="value">{overview?.mockAccountCount ?? 0}개</div>
          <span className="symbol"><Icon name="wallet" size={24} /></span>
        </Card>
        <Card className="stat">
          <small>모의은행 총 잔액</small>
          <div className="value">{formatKrw(overview?.totalBalanceAmt ?? 0)}</div>
          <span className="symbol"><Icon name="won" size={24} /></span>
        </Card>
        <Card className="stat">
          <small>오픈뱅킹 사용자번호</small>
          <div className="value" style={{ fontSize: 18 }}>
            {overview?.userSeqNo || '-'}
          </div>
          <small>{overview?.memberName ? `실명: ${overview.memberName}` : '로그인 회원'}</small>
          <span className="symbol"><Icon name="user" size={24} /></span>
        </Card>
      </div>

      {/* 탭 네비게이션 */}
      <div className="section">
        <div className="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'quick'}
            className={`tab ${tab === 'quick' ? 'active' : ''}`}
            onClick={() => changeTab('quick')}
          >
            1. 간편 은행 계좌 연결
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'ledger'}
            className={`tab ${tab === 'ledger' ? 'active' : ''}`}
            onClick={() => changeTab('ledger')}
          >
            2. 모의은행 계좌 · 거래원장 ({accounts.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'transfer'}
            className={`tab ${tab === 'transfer' ? 'active' : ''}`}
            onClick={() => changeTab('transfer')}
          >
            3. 모임장 회비 이체 ({leaderTargets.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'codef'}
            className={`tab ${tab === 'codef' ? 'active' : ''}`}
            onClick={() => changeTab('codef')}
          >
            4. CODEF 실계좌 연동 (고급)
          </button>
        </div>

        {/* ==================== TAB 1: 간편 은행 계좌 연결 (4필드 -> 은행 1원 4자리 인증) ==================== */}
        {tab === 'quick' && (
          <div className="grid cols2">
            <Card>
              <h2>은행 계좌 간편 연결</h2>
              <p className="info mini">
                아래 4가지 정보를 입력하고 버튼을 누르면 <strong>선택하신 은행의 본인인증(1원 입금 4자리 확인) 화면</strong>으로 자동 이동합니다. 인증 완료 즉시 모의은행 계좌와 F19 모임 계좌 인증이 활성화됩니다.
              </p>

              <form className="list" onSubmit={handleQuickStart} style={{ marginTop: 16 }}>
                <Select
                  label="은행명"
                  name="quickBank"
                  options={bankOptions.length ? bankOptions : [['0004', '국민은행 (0004)']]}
                  value={quickForm.organization}
                  onChange={e => setQuickForm(f => ({ ...f, organization: e.target.value }))}
                />
                <Input
                  label="계좌번호"
                  name="quickAccountNo"
                  placeholder="예: 110-123-456789 (하이픈 포함/미포함 모두 가능)"
                  required
                  value={quickForm.accountNo}
                  onChange={e => setQuickForm(f => ({ ...f, accountNo: e.target.value }))}
                />
                <div className="form-grid">
                  <Input
                    label="소유주 (예금주 실명)"
                    name="quickHolderName"
                    placeholder="로그인 회원 실명과 동일"
                    required
                    value={quickForm.holderName}
                    onChange={e => setQuickForm(f => ({ ...f, holderName: e.target.value }))}
                  />
                  <Input
                    label="전화번호"
                    name="quickPhone"
                    type="tel"
                    placeholder="예: 010-1234-5678"
                    required
                    value={quickForm.phone}
                    onChange={e => setQuickForm(f => ({ ...f, phone: e.target.value }))}
                  />
                </div>

                <div>
                  <button
                    type="button"
                    className="toggle mini"
                    onClick={() => setShowQuickAdvanced(v => !v)}
                  >
                    {showQuickAdvanced ? '▾ 시연 옵션 접기' : '▸ 시연 옵션 (초기 잔액 · 모임장 수취 계좌 설정)'}
                  </button>
                </div>

                {showQuickAdvanced && (
                  <div className="form-grid">
                    <Input
                      label="초기 모의 잔액 (원 · 미입력 시 100만 원)"
                      name="quickInitialBalance"
                      type="number"
                      min={0}
                      placeholder="1000000"
                      value={quickForm.initialMockBalance}
                      onChange={e => setQuickForm(f => ({ ...f, initialMockBalance: e.target.value }))}
                    />
                    <Select
                      label="모임장 수취 계좌 여부"
                      name="quickLeader"
                      options={[
                        ['auto', '자동 판별 (내 개설 모임 기준)'],
                        ['true', '모임장 수취 계좌로 등록 (Y)'],
                        ['false', '일반 출금 계좌로 등록 (N)'],
                      ]}
                      value={quickForm.leaderAccount}
                      onChange={e => setQuickForm(f => ({ ...f, leaderAccount: e.target.value }))}
                    />
                  </div>
                )}

                <div className="actions" style={{ marginTop: 4 }}>
                  <button type="submit" className="btn primary" disabled={busyKey === 'quick-start'}>
                    {busyKey === 'quick-start'
                      ? '은행 인증 화면 준비 중…'
                      : '선택한 은행 로그인 · 1원 인증으로 이동 →'}
                  </button>
                </div>
              </form>
            </Card>

            <Card>
              <div className="section-title">
                <h2>연결된 내 은행 계좌</h2>
                <Pill>{accounts.length}개 연결됨</Pill>
              </div>
              {accounts.length === 0 ? (
                <Empty>
                  아직 연결된 계좌가 없어요.
                  <br />
                  좌측에서 <strong>은행명 · 계좌번호 · 소유주 · 전화번호</strong>를 입력해 1원 인증을 진행해 보세요!
                </Empty>
              ) : (
                <div className="list">
                  {accounts.map(acc => (
                    <div key={acc.fintechUseNum} className="list-item">
                      <div className="item-head">
                        <div>
                          <b>{acc.bankName} {acc.accountMasked}</b>{' '}
                          <small>({acc.holderName})</small>{' '}
                          {acc.leaderAccount && <Pill>모임장 계좌</Pill>}
                        </div>
                        <strong style={{ color: '#1f74b8', fontSize: 16 }}>
                          {formatKrw(acc.balanceAmt)}
                        </strong>
                      </div>
                      <div className="actions" style={{ marginTop: 10 }}>
                        <button
                          type="button"
                          className="btn small soft"
                          onClick={() => {
                            setSelectedFinNum(acc.fintechUseNum);
                            changeTab('ledger');
                          }}
                        >
                          거래내역 · 충전
                        </button>
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => {
                            setTransferForm(f => ({ ...f, fromFintechUseNum: acc.fintechUseNum }));
                            changeTab('transfer');
                          }}
                        >
                          회비 이체
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {connections.length > 0 && (
                <>
                  <div className="divider" />
                  <div className="section-title" style={{ marginBottom: 8 }}>
                    <h3 style={{ margin: 0 }}>인증 완료된 은행 연결</h3>
                    <small>{connections.length}건</small>
                  </div>
                  <div className="list">
                    {connections.map(c => (
                      <div key={c.organization} className="list-item" style={{ padding: '11px 14px' }}>
                        <div className="item-head">
                          <div>
                            <b>{c.bankName}</b>{' '}
                            {c.verified ? (
                              <Pill>{c.verifiedAccountMasked} 인증됨</Pill>
                            ) : (
                              <Pill kind="warn">인증 대기</Pill>
                            )}
                          </div>
                          <button
                            type="button"
                            className="btn small danger"
                            disabled={busyKey === `disc:${c.organization}`}
                            onClick={() => handleDisconnect(c)}
                          >
                            {busyKey === `disc:${c.organization}` ? '해제 중…' : '연결 해제'}
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </Card>
          </div>
        )}

        {/* ==================== TAB 4: CODEF 실계좌 연동 (고급 / 백업) ==================== */}
        {tab === 'codef' && (
          <>
            <Card>
              <div className="section-title">
                <h2>내 CODEF 은행 연결 현황</h2>
                <Pill>{connections.length}개 은행 연결됨</Pill>
              </div>
              {connections.length === 0 ? (
                <Empty>
                  아직 CODEF에 연결된 은행이 없어요. 아래 <strong>1단계 은행 계정 연결</strong>에서 공동인증서 또는 인터넷뱅킹 로그인으로 연결해 주세요.
                </Empty>
              ) : (
                <div className="list">
                  {connections.map(c => (
                    <div key={c.organization} className="list-item">
                      <div className="item-head">
                        <div>
                          <b>{c.bankName}</b> <small>({c.organization})</small>{' '}
                          <Pill kind={c.verified ? '' : 'warn'}>
                            {c.verified ? '실계좌 인증 완료' : '연결됨 · 계좌 인증 대기'}
                          </Pill>
                        </div>
                        <div className="actions">
                          <button
                            type="button"
                            className="btn small soft"
                            disabled={busyKey === `codef-acc:${c.organization}`}
                            onClick={() => handleFetchCodefAccounts(c.organization)}
                          >
                            {busyKey === `codef-acc:${c.organization}` ? '조회 중…' : '보유 계좌 조회'}
                          </button>
                          {c.verified && (
                            <button
                              type="button"
                              className="btn small"
                              onClick={() => {
                                setSyncForm(f => ({ ...f, organization: c.organization }));
                                setTab('ledger');
                              }}
                            >
                              모의은행 보기
                            </button>
                          )}
                          <button
                            type="button"
                            className="btn small danger"
                            disabled={busyKey === `disc:${c.organization}`}
                            onClick={() => handleDisconnect(c)}
                          >
                            {busyKey === `disc:${c.organization}` ? '해제 중…' : '연결 해제'}
                          </button>
                        </div>
                      </div>
                      <div className="mini muted" style={{ marginTop: 8 }}>
                        {c.verified ? (
                          <>
                            인증 계좌: <strong>{c.verifiedAccountMasked}</strong> · 예금주: <strong>{c.verifiedHolderName}</strong> · 인증일시: {fmtDateTime(c.verifiedAt)}
                          </>
                        ) : (
                          <>최근 연결일시: {fmtDateTime(c.updatedAt)} · 아래 2단계에서 실계좌 소유 인증을 진행해 주세요.</>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>

            <div className="grid cols2 section">
              {/* 1단계: 은행 계정 연결 */}
              <Card>
                <h2>1단계 · 은행 계정 연결 (connectedId 발급)</h2>
                <p className="info mini">
                  CODEF 개인 수시입출 API는 <strong>공동인증서(loginType=0)</strong> 또는 <strong>인터넷뱅킹 아이디/비밀번호(loginType=1)</strong>를 지원합니다. 비밀번호는 서버에서 CODEF RSA 공개키로 암호화 전송 후 즉시 폐기됩니다.
                </p>
                <form className="list" onSubmit={handleConnect} style={{ marginTop: 14 }}>
                  <div className="form-grid">
                    <Select
                      label="은행 선택"
                      name="connectOrg"
                      options={bankOptions.length ? bankOptions : [['0004', '국민은행 (0004)']]}
                      value={connectForm.organization}
                      onChange={e => setConnectForm(f => ({ ...f, organization: e.target.value }))}
                    />
                    <Select
                      label="로그인 방식"
                      name="loginType"
                      options={[
                        ['0', '공동인증서 (loginType=0)'],
                        ['1', '인터넷뱅킹 ID/PW (loginType=1)'],
                      ]}
                      value={connectForm.loginType}
                      onChange={e => setConnectForm(f => ({ ...f, loginType: e.target.value }))}
                    />
                  </div>

                  {connectForm.loginType === '0' ? (
                    <>
                      <Select
                        label="인증서 형식"
                        name="certType"
                        options={[
                          ['1', 'DER + KEY 파일 (signCert.der + signPri.key)'],
                          ['pfx', 'PFX / P12 파일 (.pfx, .p12)'],
                        ]}
                        value={connectForm.certType}
                        onChange={e => setConnectForm(f => ({ ...f, certType: e.target.value }))}
                      />
                      {connectForm.certType === 'pfx' ? (
                        <Field label="PFX / P12 인증서 파일">
                          <input
                            type="file"
                            accept=".pfx,.p12"
                            onChange={e => setCertFiles(cf => ({ ...cf, pfx: e.target.files?.[0] || null }))}
                          />
                        </Field>
                      ) : (
                        <div className="form-grid">
                          <Field label="signCert.der 파일">
                            <input
                              type="file"
                              accept=".der"
                              onChange={e => setCertFiles(cf => ({ ...cf, der: e.target.files?.[0] || null }))}
                            />
                          </Field>
                          <Field label="signPri.key 파일">
                            <input
                              type="file"
                              accept=".key"
                              onChange={e => setCertFiles(cf => ({ ...cf, key: e.target.files?.[0] || null }))}
                            />
                          </Field>
                        </div>
                      )}
                    </>
                  ) : (
                    <Input
                      label="은행 인터넷뱅킹 아이디"
                      name="bankUserId"
                      placeholder="은행 로그인 ID"
                      required
                      value={connectForm.id}
                      onChange={e => setConnectForm(f => ({ ...f, id: e.target.value }))}
                    />
                  )}

                  <div className="form-grid">
                    <Input
                      label={connectForm.loginType === '0' ? '인증서 비밀번호' : '인터넷뱅킹 비밀번호'}
                      name="connectPassword"
                      type="password"
                      autoComplete="off"
                      required
                      value={connectForm.password}
                      onChange={e => setConnectForm(f => ({ ...f, password: e.target.value }))}
                    />
                    <Input
                      label="생년월일 YYMMDD (선택 · 은행별 조건부)"
                      name="connectBirthDate"
                      placeholder="예: 950101"
                      maxLength={6}
                      value={connectForm.birthDate}
                      onChange={e => setConnectForm(f => ({ ...f, birthDate: e.target.value }))}
                    />
                  </div>

                  <div className="actions">
                    <button type="submit" className="btn primary" disabled={busyKey === 'connect'}>
                      {busyKey === 'connect' ? 'CODEF 연결 중… (최대 90초)' : '은행 계정 연결하기'}
                    </button>
                  </div>
                </form>
              </Card>

              {/* 2단계: 실계좌 소유 인증 */}
              <Card>
                <h2>2단계 · 실계좌 소유 인증 & 모의은행 적재</h2>
                <p className="info mini">
                  CODEF 거래내역 조회(<code>inquiryType=1</code>)로 예금주 실명(<code>resAccountHolder</code>)이 로그인 회원 실명과 일치하는지 검증하고, 모의은행 계좌를 자동 생성합니다.
                </p>

                {codefAccountsRes && (
                  <div className="detail-panel" style={{ marginBottom: 14 }}>
                    <div className="item-head">
                      <b>{codefAccountsRes.bankName} 보유 계좌 ({codefAccountsRes.count}건)</b>
                      <small>클릭하면 아래 계좌번호에 자동 입력됩니다</small>
                    </div>
                    <div className="list" style={{ marginTop: 10 }}>
                      {(codefAccountsRes.accounts || []).length === 0 ? (
                        <p className="muted mini">조회된 수시입출 계좌가 없어요.</p>
                      ) : (
                        codefAccountsRes.accounts.map((a, idx) => (
                          <button
                            key={`${a.accountNo}-${idx}`}
                            type="button"
                            className="list-item"
                            style={{ textAlign: 'left', cursor: 'pointer' }}
                            onClick={() => setVerifyForm(f => ({
                              ...f,
                              organization: codefAccountsRes.organization,
                              account: a.accountNo,
                            }))}
                          >
                            <div className="item-head">
                              <b>{a.accountName || '입출금 계좌'} · {a.accountDisplay || a.accountNo}</b>
                              <span>{a.balanceAmt != null ? formatKrw(a.balanceAmt) : '-'}</span>
                            </div>
                          </button>
                        ))
                      )}
                    </div>
                  </div>
                )}

                <form className="list" onSubmit={handleVerifyAccount} style={{ marginTop: 14 }}>
                  <div className="form-grid">
                    <Select
                      label="인증할 은행"
                      name="verifyOrg"
                      options={bankOptions.length ? bankOptions : [['0004', '국민은행 (0004)']]}
                      value={verifyForm.organization}
                      onChange={e => setVerifyForm(f => ({ ...f, organization: e.target.value }))}
                    />
                    <Input
                      label="계좌번호 (하이픈 허용)"
                      name="verifyAccount"
                      placeholder="예: 110-123-456789"
                      required
                      value={verifyForm.account}
                      onChange={e => setVerifyForm(f => ({ ...f, account: e.target.value }))}
                    />
                  </div>
                  <div className="form-grid">
                    <Input
                      label="계좌 비밀번호 4자리 (선택)"
                      name="verifyAccountPw"
                      type="password"
                      autoComplete="off"
                      placeholder="일부 은행 필요 시"
                      value={verifyForm.accountPassword}
                      onChange={e => setVerifyForm(f => ({ ...f, accountPassword: e.target.value }))}
                    />
                    <Input
                      label="생년월일 YYMMDD (선택)"
                      name="verifyBirthDate"
                      placeholder="예: 950101"
                      maxLength={6}
                      value={verifyForm.birthDate}
                      onChange={e => setVerifyForm(f => ({ ...f, birthDate: e.target.value }))}
                    />
                  </div>
                  <div className="form-grid">
                    <Input
                      label="모의은행 초기잔액 지정 (선택 · 원)"
                      name="initialMockBalance"
                      type="number"
                      min={0}
                      placeholder="비우면 CODEF 실잔액 사용"
                      value={verifyForm.initialMockBalance}
                      onChange={e => setVerifyForm(f => ({ ...f, initialMockBalance: e.target.value }))}
                    />
                    <Select
                      label="모임장 수취 계좌 설정"
                      name="verifyLeader"
                      options={[
                        ['auto', '자동 판별 (내 개설 모임 기준)'],
                        ['true', '모임장 수취 계좌로 등록 (Y)'],
                        ['false', '일반 출금 계좌로 등록 (N)'],
                      ]}
                      value={verifyForm.leaderAccount}
                      onChange={e => setVerifyForm(f => ({ ...f, leaderAccount: e.target.value }))}
                    />
                  </div>

                  <label className="checkline" style={{ margin: '4px 0' }}>
                    <input
                      type="checkbox"
                      checked={verifyForm.syncToMockBank}
                      onChange={e => setVerifyForm(f => ({ ...f, syncToMockBank: e.target.checked }))}
                    />
                    <span>인증 성공 시 KFTC 모의은행 원장에 계좌 및 최근 실거래내역을 자동 적재</span>
                  </label>

                  <div className="actions">
                    <button type="submit" className="btn primary" disabled={busyKey === 'verify'}>
                      {busyKey === 'verify' ? 'CODEF 실계좌 인증 중…' : '실계좌 소유 인증 실행'}
                    </button>
                    <button
                      type="button"
                      className="btn soft"
                      disabled={busyKey === `codef-acc:${verifyForm.organization}`}
                      onClick={() => handleFetchCodefAccounts(verifyForm.organization)}
                    >
                      보유 계좌 목록 먼저 불러오기
                    </button>
                  </div>
                </form>

                {verifyResult && (
                  <div className="detail-panel">
                    <div className="item-head">
                      <b><Icon name="check" size={16} /> 실계좌 인증 성공</b>
                      <Pill>{verifyResult.bankName} {verifyResult.accountMasked}</Pill>
                    </div>
                    <p className="mini" style={{ marginTop: 8, marginBottom: 0 }}>
                      예금주: <strong>{verifyResult.holderName}</strong> (회원 실명: {verifyResult.memberName}) · 실잔액: <strong>{formatKrw(verifyResult.balanceAmt)}</strong> · 조회 거래 {verifyResult.recentTransactionCount}건
                      {verifyResult.mockAccount && (
                        <>
                          <br />
                          모의은행 핀테크번호: <code>{verifyResult.mockAccount.fintechUseNum}</code> · 적재 잔액: <strong>{formatKrw(verifyResult.mockAccount.balanceAmt)}</strong> · 원장 거래 {verifyResult.mockAccount.syncedTransactionCount}건 반영
                        </>
                      )}
                    </p>
                  </div>
                )}
              </Card>
            </div>

            {/* 3단계: CODEF 실거래내역 직접 조회 */}
            <Card className="section">
              <h2>3단계 · CODEF 실거래내역 직접 조회</h2>
              <form className="form-row" onSubmit={handleFetchCodefTx}>
                <Select
                  label="은행"
                  name="codefTxOrg"
                  options={bankOptions.length ? bankOptions : [['0004', '국민은행 (0004)']]}
                  value={codefTxForm.organization}
                  onChange={e => setCodefTxForm(f => ({ ...f, organization: e.target.value }))}
                />
                <Input
                  label="계좌번호 (생략 시 인증계좌)"
                  name="codefTxAccount"
                  placeholder="생략 가능"
                  value={codefTxForm.account}
                  onChange={e => setCodefTxForm(f => ({ ...f, account: e.target.value }))}
                />
                <Input
                  label="시작일 yyyyMMdd"
                  name="codefTxStart"
                  placeholder="예: 20261101"
                  maxLength={8}
                  value={codefTxForm.startDate}
                  onChange={e => setCodefTxForm(f => ({ ...f, startDate: e.target.value }))}
                />
                <Input
                  label="종료일 yyyyMMdd"
                  name="codefTxEnd"
                  placeholder="예: 20261126"
                  maxLength={8}
                  value={codefTxForm.endDate}
                  onChange={e => setCodefTxForm(f => ({ ...f, endDate: e.target.value }))}
                />
                <Select
                  label="정렬"
                  name="codefTxOrder"
                  options={[
                    ['0', '최신순 (0)'],
                    ['1', '과거순 (1)'],
                  ]}
                  value={codefTxForm.orderBy}
                  onChange={e => setCodefTxForm(f => ({ ...f, orderBy: e.target.value }))}
                />
                <div className="field">
                  <label>조회</label>
                  <button type="submit" className="btn primary" disabled={busyKey === 'codef-tx'}>
                    {busyKey === 'codef-tx' ? '조회 중…' : 'CODEF 실거래 조회'}
                  </button>
                </div>
              </form>

              {codefTxRes && (
                <div style={{ marginTop: 16 }}>
                  <div className="item-head" style={{ marginBottom: 10 }}>
                    <b>
                      {codefTxRes.bankName} {codefTxRes.accountMasked} ({codefTxRes.holderName})
                    </b>
                    <Pill>
                      잔액 {formatKrw(codefTxRes.balanceAmt)} · {codefTxRes.count}건 ({codefTxRes.startDate}~{codefTxRes.endDate})
                    </Pill>
                  </div>
                  {(codefTxRes.transactions || []).length === 0 ? (
                    <Empty>해당 기간에 조회된 실거래내역이 없어요.</Empty>
                  ) : (
                    <div className="tablewrap">
                      <table>
                        <thead>
                          <tr>
                            <th>거래일시</th>
                            <th>구분</th>
                            <th>금액</th>
                            <th>거래후 잔액</th>
                            <th>적요</th>
                            <th>거래처/지점</th>
                          </tr>
                        </thead>
                        <tbody>
                          {codefTxRes.transactions.map((t, i) => (
                            <tr key={`${t.tranDate}-${t.tranTime}-${i}`}>
                              <td>{fmtYmd(t.tranDate, t.tranTime)}</td>
                              <td>
                                <Pill kind={t.inoutType === '입금' ? '' : 'warn'}>{t.inoutType}</Pill>
                              </td>
                              <td>{t.inoutType === '입금' ? '+' : '-'}{formatKrw(t.tranAmt)}</td>
                              <td>{formatKrw(t.afterBalanceAmt)}</td>
                              <td>{t.desc3 || t.desc1 || '-'}</td>
                              <td>{t.desc4 || t.desc2 || '-'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              )}
            </Card>
          </>
        )}

        {/* ==================== TAB 2: 모의은행 원장 ==================== */}
        {tab === 'ledger' && (
          <>
            <Card>
              <div className="section-title">
                <h2>내 모의은행 계좌 목록</h2>
                <Pill>{accounts.length}개 활성 계좌</Pill>
              </div>
              {accounts.length === 0 ? (
                <Empty>
                  아직 적재된 모의은행 계좌가 없어요. <strong>1. 간편 은행 계좌 연결</strong>에서 은행명·계좌번호·소유주·전화번호로 1원 인증을 완료하면 자동으로 생성됩니다.
                </Empty>
              ) : (
                <div className="list">
                  {accounts.map(acc => {
                    const isSelected = acc.fintechUseNum === selectedFinNum;
                    return (
                      <div
                        key={acc.fintechUseNum}
                        className={`list-item ${isSelected ? 'selected' : ''}`}
                      >
                        <div className="item-head">
                          <div>
                            <b>{acc.bankName} {acc.accountMasked}</b>{' '}
                            <small>({acc.holderName})</small>{' '}
                            {acc.leaderAccount && <Pill>모임장 수취 계좌</Pill>}
                          </div>
                          <div className="metric" style={{ fontSize: 20 }}>
                            {formatKrw(acc.balanceAmt)}
                          </div>
                        </div>
                        <div className="mini muted" style={{ marginTop: 6 }}>
                          핀테크이용번호: <code>{acc.fintechUseNum}</code> · 은행코드: {acc.bankCode}
                        </div>
                        <div className="actions" style={{ marginTop: 10 }}>
                          <button
                            type="button"
                            className={`btn small ${isSelected ? 'primary' : 'soft'}`}
                            onClick={() => {
                              setSelectedFinNum(acc.fintechUseNum);
                              loadSelectedTransactions(acc.fintechUseNum);
                            }}
                          >
                            거래내역 보기
                          </button>
                          <button
                            type="button"
                            className="btn small"
                            disabled={busyKey === `bal:${acc.fintechUseNum}`}
                            onClick={() => handleCheckBalance(acc.fintechUseNum)}
                          >
                            잔액 실시간 확인
                          </button>
                          <button
                            type="button"
                            className="btn small"
                            disabled={busyKey === `leader:${acc.fintechUseNum}`}
                            onClick={() => handleToggleLeader(acc)}
                          >
                            {acc.leaderAccount ? '모임장 계좌 해제' : '모임장 계좌로 지정'}
                          </button>
                          <button
                            type="button"
                            className="btn small soft"
                            onClick={() => setDepositForm(f => ({ ...f, fintechUseNum: acc.fintechUseNum }))}
                          >
                            이 계좌에 자금 충전
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </Card>

            <div className="grid cols2 section">
              {/* 시연용 자금 충전 */}
              <Card>
                <h2>시연용 모의 자금 충전</h2>
                <p className="info mini">
                  모의은행 원장에 테스트 자금을 충전하고 입금 거래내역을 기록합니다. (실제 금융 거래가 아닙니다.)
                </p>
                <form className="list" onSubmit={handleDepositDemo} style={{ marginTop: 12 }}>
                  <Select
                    label="충전할 모의 계좌"
                    name="depositFinNum"
                    options={
                      accounts.length
                        ? accounts.map(a => [a.fintechUseNum, `${a.bankName} ${a.accountMasked} (${formatKrw(a.balanceAmt)})`])
                        : [['', '보유 모의 계좌 없음']]
                    }
                    value={depositForm.fintechUseNum}
                    onChange={e => setDepositForm(f => ({ ...f, fintechUseNum: e.target.value }))}
                  />
                  <div className="form-grid">
                    <Input
                      label="충전 금액 (원)"
                      name="depositAmt"
                      type="number"
                      min={1}
                      max={100000000}
                      required
                      value={depositForm.amount}
                      onChange={e => setDepositForm(f => ({ ...f, amount: e.target.value }))}
                    />
                    <Input
                      label="통장 적요"
                      name="depositContent"
                      maxLength={40}
                      value={depositForm.printContent}
                      onChange={e => setDepositForm(f => ({ ...f, printContent: e.target.value }))}
                    />
                  </div>
                  <div className="actions">
                    {[100000, 500000, 1000000].map(amt => (
                      <button
                        key={amt}
                        type="button"
                        className="btn small soft"
                        onClick={() => setDepositForm(f => ({ ...f, amount: String(amt) }))}
                      >
                        +{amt / 10000}만 원
                      </button>
                    ))}
                    <button
                      type="submit"
                      className="btn primary"
                      disabled={busyKey === 'deposit' || accounts.length === 0}
                    >
                      {busyKey === 'deposit' ? '충전 중…' : '모의 자금 충전'}
                    </button>
                  </div>
                </form>
              </Card>

              {/* CODEF → 모의은행 재동기화 */}
              <Card>
                <h2>CODEF 인증 계좌 → 모의은행 재동기화</h2>
                <p className="info mini">
                  저장된 인증 정보로 모의 계좌를 다시 적재(CODEF 일 100건 한도 절약)하거나, CODEF 실거래를 재조회해 최신 원장을 반영할 수 있습니다.
                </p>
                <form className="list" onSubmit={handleSyncFromCodef} style={{ marginTop: 12 }}>
                  <Select
                    label="인증 완료된 은행"
                    name="syncOrg"
                    options={
                      verifiedConnections.length
                        ? verifiedConnections.map(c => [c.organization, `${c.bankName} (${c.verifiedAccountMasked})`])
                        : [['', '인증 완료된 CODEF 은행 없음']]
                    }
                    value={syncForm.organization}
                    onChange={e => setSyncForm(f => ({ ...f, organization: e.target.value }))}
                  />
                  <div className="form-grid">
                    <Input
                      label="모의 잔액 직접 지정 (선택 · 원)"
                      name="syncInitBal"
                      type="number"
                      min={0}
                      placeholder="비우면 기존/실잔액 유지"
                      value={syncForm.initialMockBalance}
                      onChange={e => setSyncForm(f => ({ ...f, initialMockBalance: e.target.value }))}
                    />
                    <Select
                      label="모임장 계좌 여부"
                      name="syncLeader"
                      options={[
                        ['auto', '자동 판별'],
                        ['true', '모임장 계좌 (Y)'],
                        ['false', '일반 계좌 (N)'],
                      ]}
                      value={syncForm.leaderAccount}
                      onChange={e => setSyncForm(f => ({ ...f, leaderAccount: e.target.value }))}
                    />
                  </div>
                  <label className="checkline" style={{ margin: '2px 0' }}>
                    <input
                      type="checkbox"
                      checked={syncForm.refreshFromCodef}
                      onChange={e => setSyncForm(f => ({ ...f, refreshFromCodef: e.target.checked }))}
                    />
                    <span>CODEF API 실시간 재조회 후 최신 잔액·거래내역 반영 (체크 해제 시 저장 정보만 사용)</span>
                  </label>
                  <div className="actions">
                    <button
                      type="submit"
                      className="btn primary"
                      disabled={busyKey === 'sync' || verifiedConnections.length === 0}
                    >
                      {busyKey === 'sync' ? '동기화 중…' : '모의은행 동기화 실행'}
                    </button>
                  </div>
                </form>
              </Card>
            </div>

            {/* 선택 계좌 거래내역 & 월별 입출금 집계 */}
            <Card className="section">
              <div className="section-title">
                <h2>
                  모의은행 거래원장{' '}
                  {accountTxRes?.account && (
                    <small>
                      ({accountTxRes.account.bankName} {accountTxRes.account.accountMasked})
                    </small>
                  )}
                </h2>
                {accountTxRes && (
                  <div className="actions">
                    <Pill>총 입금 +{formatKrw(accountTxRes.totalDepositAmt)}</Pill>
                    <Pill kind="warn">총 출금 -{formatKrw(accountTxRes.totalWithdrawAmt)}</Pill>
                  </div>
                )}
              </div>

              {selectedFinNum ? (
                <>
                  <form
                    className="form-row"
                    style={{ marginBottom: 14 }}
                    onSubmit={e => {
                      e.preventDefault();
                      loadSelectedTransactions(selectedFinNum, txFilter);
                    }}
                  >
                    <Select
                      label="구분"
                      name="txType"
                      options={[
                        ['A', '전체 (A)'],
                        ['I', '입금만 (I)'],
                        ['O', '출금만 (O)'],
                      ]}
                      value={txFilter.inquiryType}
                      onChange={e => setTxFilter(f => ({ ...f, inquiryType: e.target.value }))}
                    />
                    <Select
                      label="정렬"
                      name="txSort"
                      options={[
                        ['D', '최신순 (D)'],
                        ['A', '과거순 (A)'],
                      ]}
                      value={txFilter.sortOrder}
                      onChange={e => setTxFilter(f => ({ ...f, sortOrder: e.target.value }))}
                    />
                    <Input
                      label="시작일 yyyyMMdd"
                      name="txFrom"
                      placeholder="기본 최근 1개월"
                      maxLength={8}
                      value={txFilter.fromDate}
                      onChange={e => setTxFilter(f => ({ ...f, fromDate: e.target.value }))}
                    />
                    <Input
                      label="종료일 yyyyMMdd"
                      name="txTo"
                      placeholder="기본 오늘"
                      maxLength={8}
                      value={txFilter.toDate}
                      onChange={e => setTxFilter(f => ({ ...f, toDate: e.target.value }))}
                    />
                    <div className="field">
                      <label>적용</label>
                      <button type="submit" className="btn soft" disabled={busyKey.startsWith('tx:')}>
                        조회
                      </button>
                    </div>
                  </form>

                  {(accountTxRes?.monthlySummary || []).length > 0 && (
                    <div className="actions" style={{ marginBottom: 14 }}>
                      {accountTxRes.monthlySummary.map(m => (
                        <span key={m.yearMonth} className="pill">
                          {fmtYm(m.yearMonth)} · 입금 {formatKrw(m.totalDepositAmt)} / 출금 {formatKrw(m.totalWithdrawAmt)} ({m.txCount}건)
                        </span>
                      ))}
                    </div>
                  )}

                  {(accountTxRes?.transactions || []).length === 0 ? (
                    <Empty>조건에 해당하는 모의은행 거래내역이 없어요.</Empty>
                  ) : (
                    <div className="tablewrap">
                      <table>
                        <thead>
                          <tr>
                            <th>거래일시</th>
                            <th>구분</th>
                            <th>금액</th>
                            <th>거래후 잔액</th>
                            <th>적요</th>
                            <th>상대방</th>
                            <th>지점</th>
                          </tr>
                        </thead>
                        <tbody>
                          {accountTxRes.transactions.map((t, idx) => (
                            <tr key={`${t.bankTranId}-${idx}`}>
                              <td>{fmtYmd(t.tranDate, t.tranTime)}</td>
                              <td>
                                <Pill kind={t.inoutType === '입금' ? '' : 'warn'}>{t.inoutType}</Pill>
                              </td>
                              <td>{t.inoutType === '입금' ? '+' : '-'}{formatKrw(t.tranAmt)}</td>
                              <td>{formatKrw(t.afterBalanceAmt)}</td>
                              <td>{t.printContent || '-'}</td>
                              <td>{t.counterpartyName || '-'}</td>
                              <td>{t.branchName || '-'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </>
              ) : (
                <Empty>위 계좌 목록에서 모의 계좌를 선택하면 거래내역과 월별 집계를 볼 수 있어요.</Empty>
              )}
            </Card>
          </>
        )}

        {/* ==================== TAB 3: 모임장 회비 이체 ==================== */}
        {tab === 'transfer' && (
          <div className="grid cols2">
            <Card>
              <div className="section-title">
                <h2>이체 가능한 모임장 수취 계좌</h2>
                <Pill>{leaderTargets.length}개</Pill>
              </div>
              <p className="info mini">
                보안 정책상 <code>LEADER_YN = 'Y'</code>로 등록된 모임장 수취 계좌로만 이체할 수 있어요. 계좌를 클릭하면 우측 이체 폼에 자동 입력됩니다.
              </p>
              {leaderTargets.length === 0 ? (
                <Empty>
                  등록된 모임장 수취 계좌가 없어요. 테스트하려면 <strong>2. 모의은행 계좌·원장</strong> 탭에서 본인 계좌를 <em>모임장 계좌로 지정</em>해 보세요.
                </Empty>
              ) : (
                <div className="list" style={{ marginTop: 12 }}>
                  {leaderTargets.map((t, idx) => {
                    const selected = transferForm.toFintechUseNum === t.fintechUseNum;
                    return (
                      <button
                        key={`${t.fintechUseNum}-${t.groupId ?? idx}`}
                        type="button"
                        className={`list-item ${selected ? 'selected' : ''}`}
                        style={{ textAlign: 'left', cursor: 'pointer' }}
                        onClick={() => setTransferForm(f => ({
                          ...f,
                          toFintechUseNum: t.fintechUseNum,
                          ...(t.monthlyDuesAmt ? { amount: String(t.monthlyDuesAmt) } : {}),
                          ...(t.groupTitle ? { wdPrintContent: `${t.groupTitle} 회비`.slice(0, 40) } : {}),
                        }))}
                      >
                        <div className="item-head">
                          <div>
                            <b>{t.bankName} {t.accountMasked}</b> · {t.holderNameMasked}
                          </div>
                          {t.myGroup ? <Pill>내 참여 모임</Pill> : <Pill kind="warn">기타 모임장</Pill>}
                        </div>
                        <div className="mini muted" style={{ marginTop: 6 }}>
                          {t.groupTitle ? (
                            <>모임: <strong>{t.groupTitle}</strong> · 월 회비 {formatKrw(t.monthlyDuesAmt)}</>
                          ) : (
                            <>핀테크번호: <code>{t.fintechUseNum}</code></>
                          )}
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </Card>

            <Card>
              <h2>모의 회비 이체 (KFTC 원자적 출금이체)</h2>
              <p className="info mini">
                출금 계좌와 수취 계좌를 <code>SELECT FOR UPDATE</code> 오름차순 락으로 잠그고 멱등 <code>bank_tran_id</code>로 이중 이체를 차단합니다.
              </p>
              <form className="list" onSubmit={handleTransfer} style={{ marginTop: 12 }}>
                <Select
                  label="출금할 내 모의 계좌"
                  name="fromFinNum"
                  options={
                    accounts.length
                      ? accounts.map(a => [a.fintechUseNum, `${a.bankName} ${a.accountMasked} (잔액 ${formatKrw(a.balanceAmt)})`])
                      : [['', '보유 모의 계좌 없음']]
                  }
                  value={transferForm.fromFintechUseNum}
                  onChange={e => setTransferForm(f => ({ ...f, fromFintechUseNum: e.target.value }))}
                />
                <Select
                  label="수취 모임장 계좌"
                  name="toFinNum"
                  options={
                    leaderTargets.length
                      ? leaderTargets.map(t => [
                          t.fintechUseNum,
                          `${t.bankName} ${t.accountMasked} · ${t.holderNameMasked}${t.groupTitle ? ` (${t.groupTitle})` : ''}`,
                        ])
                      : [['', '등록된 모임장 계좌 없음']]
                  }
                  value={transferForm.toFintechUseNum}
                  onChange={e => setTransferForm(f => ({ ...f, toFintechUseNum: e.target.value }))}
                />
                <Input
                  label="이체 금액 (원)"
                  name="transferAmt"
                  type="number"
                  min={1}
                  max={100000000}
                  required
                  value={transferForm.amount}
                  onChange={e => setTransferForm(f => ({ ...f, amount: e.target.value }))}
                />
                <div className="form-grid">
                  <Input
                    label="내 통장 표시 적요"
                    name="wdPrint"
                    maxLength={40}
                    value={transferForm.wdPrintContent}
                    onChange={e => setTransferForm(f => ({ ...f, wdPrintContent: e.target.value }))}
                  />
                  <Input
                    label="받는 분 통장 표시 적요 (선택)"
                    name="dpsPrint"
                    maxLength={40}
                    placeholder="비우면 자동 생성"
                    value={transferForm.dpsPrintContent}
                    onChange={e => setTransferForm(f => ({ ...f, dpsPrintContent: e.target.value }))}
                  />
                </div>
                <div className="actions">
                  <button
                    type="submit"
                    className="btn primary"
                    disabled={busyKey === 'transfer' || accounts.length === 0 || leaderTargets.length === 0}
                  >
                    {busyKey === 'transfer' ? '이체 처리 중…' : '모의 회비 이체 실행'}
                  </button>
                </div>
              </form>

              {transferReceipt && (
                <div className="detail-panel">
                  <div className="item-head">
                    <b><Icon name="check" size={16} /> 이체 완료 영수증</b>
                    <Pill>{transferReceipt.bankTranDate}</Pill>
                  </div>
                  <p className="mini" style={{ marginTop: 8, marginBottom: 0 }}>
                    거래고유번호: <code>{transferReceipt.bankTranId}</code>
                    <br />
                    출금: {transferReceipt.fromBankName} ({transferReceipt.fromHolderName}) → 수취: {transferReceipt.toBankName} ({transferReceipt.toHolderNameMasked})
                    <br />
                    이체 금액: <strong>{formatKrw(transferReceipt.tranAmt)}</strong> · 출금 후 잔액: <strong>{formatKrw(transferReceipt.fromAfterBalanceAmt)}</strong>
                  </p>
                </div>
              )}
            </Card>
          </div>
        )}
      </div>
    </>
  );
}
