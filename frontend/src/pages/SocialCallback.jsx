import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { finishSocial } from '../api/auth';
import { encodeRegionForServer } from '../common/regions';
import RegionSelector from '../components/RegionSelector';
import TermsAgreementModal from '../components/TermsAgreementModal';

export default function SocialCallback() {

    const navigate = useNavigate();
    const started = useRef(false);

    const [params] = useState(() =>
        new URLSearchParams(
            window.location.hash.slice(1)
        )
    );

    const type = params.get('type');
    const token = params.get('token');

    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [ready, setReady] = useState(false);
    const [providerEmail, setProviderEmail] = useState('');
    const [terms, setTerms] = useState([]);
    const [termsOpen, setTermsOpen] = useState(false);

    const [form, setForm] = useState({
        name: '',
        email: '',
        phone: '',
        regionSido: '',
        regionSigungu: '',
        regionDong: '',
        agreedTermsIds: []
    });

    const request = async (path, body) => {

        const response = await fetch(
            '/api/auth/social/' + path,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(body)
            }
        );

        const result = await response.json()
            .catch(() => null);

        if (!response.ok) {
            throw new Error(
                result?.message ||
                '간편 로그인 처리에 실패했습니다.'
            );
        }

        return result;
    };

    useEffect(() => {

        if (started.current) return;
        started.current = true;

        // URL에서 임시 인증 토큰 제거
        window.history.replaceState(
            window.history.state,
            '',
            window.location.pathname
        );

        if (!token || type === 'error') {
            setError('간편 로그인을 다시 시도해주세요.');
            return;
        }

        // 기존 회원 로그인
        if (type === 'login') {

            setBusy(true);

            finishSocial('social/exchange', {
                loginCode: token
            })
            .then(() => {
                navigate('/dashboard', {
                    replace: true
                });
            })
            .catch(e => setError(e.message))
            .finally(() => setBusy(false));

            return;
        }

        // 신규 회원가입
        if (type === 'signup') {

            Promise.all([
                request('signup-info', {
                    signupToken: token
                }),

                fetch('/api/auth/social/terms')
                    .then(r => {
                        if (!r.ok) {
                            throw new Error(
                                '약관 조회에 실패했습니다.'
                            );
                        }
                        return r.json();
                    })
            ])
            .then(([user, policies]) => {

                setProviderEmail(user.email || '');

                setForm(old => ({
                    ...old,
                    name: user.name || '',
                    email: user.email || ''
                }));

                setTerms(policies);
                setReady(true);
            })
            .catch(e => setError(e.message));
        }

    }, []);

    const change = key => e => {

        setForm(old => ({
            ...old,
            [key]: e.target.value
        }));
    };

    const checkTerms = (id, checked) => {

        setForm(old => ({
            ...old,
            agreedTermsIds: checked
                ? (old.agreedTermsIds.includes(id)
                    ? old.agreedTermsIds
                    : [...old.agreedTermsIds, id])
                : old.agreedTermsIds.filter(
                    value => value !== id
                )
        }));
    };

    const toggleAllTerms = checked => {

        setForm(old => ({
            ...old,
            agreedTermsIds: checked
                ? terms.map(t => t.termsId)
                : []
        }));
    };

    const openTermsModal = e => {

        e.preventDefault();

        if (busy || !ready) return;

        const encodedRegion = encodeRegionForServer({
            sido: form.regionSido,
            sigungu: form.regionSigungu,
            dong: form.regionDong
        });

        if (!encodedRegion.regionSido || !encodedRegion.regionSigungu || !encodedRegion.regionDong) {
            setError('활동 지역을 시/도, 시/군/구, 읍/면/동까지 선택해 주세요.');
            return;
        }

        if (terms.length !== 6) {
            setError('활성 약관 6종 등록이 필요합니다.');
            return;
        }

        setError('');
        setTermsOpen(true);
    };

    const submitSignup = async () => {

        if (busy || !ready) return;

        const encodedRegion = encodeRegionForServer({
            sido: form.regionSido,
            sigungu: form.regionSigungu,
            dong: form.regionDong
        });

        if (!encodedRegion.regionSido || !encodedRegion.regionSigungu || !encodedRegion.regionDong) {
            setError('활동 지역을 시/도, 시/군/구, 읍/면/동까지 선택해 주세요.');
            setTermsOpen(false);
            return;
        }

        setBusy(true);
        setError('');

        try {

            await finishSocial('social/signup', {
                ...form,
                regionSido: encodedRegion.regionSido,
                regionSigungu: encodedRegion.regionSigungu,
                regionDong: encodedRegion.regionDong,
                signupToken: token
            });

            setTermsOpen(false);
            navigate('/dashboard', {
                replace: true
            });

        } catch (e) {

            setError(
                e.message +
                ' 필요한 경우 간편 로그인을 다시 시작해주세요.'
            );

        } finally {

            setBusy(false);
        }
    };

    return (
        <div className="auth">

            <div className="auth-card">

                <div className="auth-brand">
                    ☁ MOAVA
                    <span>TOGETHER, BRIGHTER</span>
                </div>

                <h1>
                    {type === 'signup'
                        ? '간편 회원가입'
                        : '간편 로그인'}
                </h1>

                {error && !termsOpen && (
                    <p style={{ color: 'red' }}>
                        {error}
                    </p>
                )}

                {type === 'login' && busy && (
                    <p>로그인 중입니다...</p>
                )}

                {type === 'signup' && ready && (

                    <form onSubmit={openTermsModal}>

                        <p className="muted">
                            소셜 인증이 완료되었습니다.
                            추가 정보를 입력해주세요.
                        </p>

                        <label className="field">
                            이름
                            <input
                                value={form.name}
                                onChange={change('name')}
                                required
                            />
                        </label>

                        <label className="field">
                            이메일
                            <input
                                type="email"
                                value={form.email}
                                onChange={change('email')}
                                readOnly={!!providerEmail}
                                required
                            />
                        </label>

                        <label className="field">
                            휴대전화번호
                            <input
                                type="tel"
                                value={form.phone}
                                onChange={change('phone')}
                                placeholder="01012345678"
                                required
                            />
                        </label>

                        <RegionSelector
                            mode="required"
                            namePrefix="socialRegion"
                            className="region-selector-grid region-selector-compact"
                            sido={form.regionSido}
                            sigungu={form.regionSigungu}
                            dong={form.regionDong}
                            disabled={busy}
                            onChange={({ sido, sigungu, dong }) =>
                                setForm(old => ({
                                    ...old,
                                    regionSido: sido,
                                    regionSigungu: sigungu,
                                    regionDong: dong
                                }))
                            }
                        />

                        {terms.length !== 6 && (
                            <p className="terms-error-msg">
                                활성 약관 6종 등록이 필요합니다.
                            </p>
                        )}

                        <button
                            type="submit"
                            className="btn primary"
                            disabled={busy || terms.length !== 6}
                        >
                            {busy
                                ? '가입 중...'
                                : '약관 동의하고 가입하기'}
                        </button>

                    </form>
                )}

                <div className="auth-link">
                    <Link to="/login">
                        로그인 화면으로 돌아가기
                    </Link>
                </div>

            </div>

            <TermsAgreementModal
                open={termsOpen}
                terms={terms}
                agreedTermsIds={form.agreedTermsIds}
                onToggleTerm={checkTerms}
                onToggleAll={toggleAllTerms}
                onClose={() => setTermsOpen(false)}
                onConfirm={submitSignup}
                busy={busy}
                error={error}
                confirmLabel="동의하고 가입하기"
            />

        </div>
    );
}