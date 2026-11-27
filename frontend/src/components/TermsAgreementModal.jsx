/* 토스·카카오 스타일 약관 동의 팝업 모달 + 약관 전문 상세 모달 */
import { useEffect, useState } from 'react';

export default function TermsAgreementModal({
    open,
    terms = [],
    agreedTermsIds = [],
    onToggleTerm,
    onToggleAll,
    onClose,
    onConfirm,
    busy = false,
    error = '',
    confirmLabel = '동의하고 가입하기',
}) {
    const [viewingTerm, setViewingTerm] = useState(null);

    useEffect(() => {
        if (!open) {
            setViewingTerm(null);
            return;
        }

        const handleKeyDown = (e) => {
            if (e.key !== 'Escape' || busy) return;
            if (viewingTerm) {
                setViewingTerm(null);
            } else {
                onClose?.();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [open, viewingTerm, busy, onClose]);

    if (!open) return null;

    const requiredTerms = terms.filter((t) => t.requiredYn === 'Y');
    const optionalTerms = terms.filter((t) => t.requiredYn !== 'Y');

    const allAgreed =
        terms.length > 0 &&
        terms.every((t) => agreedTermsIds.includes(t.termsId));

    const allRequiredAgreed =
        requiredTerms.length > 0 &&
        requiredTerms.every((t) => agreedTermsIds.includes(t.termsId));

    const canSubmit = !busy && terms.length === 6 && allRequiredAgreed;

    const renderRow = (term) => {
        const checked = agreedTermsIds.includes(term.termsId);
        const isRequired = term.requiredYn === 'Y';

        return (
            <div className="terms-row" key={term.termsId}>
                <label className="terms-row-check">
                    <input
                        type="checkbox"
                        checked={checked}
                        disabled={busy}
                        onChange={(e) => onToggleTerm?.(term.termsId, e.target.checked)}
                    />
                    <span className={`terms-badge ${isRequired ? 'req' : 'opt'}`}>
                        {isRequired ? '필수' : '선택'}
                    </span>
                    <span className="terms-row-title">{term.title}</span>
                </label>
                <button
                    type="button"
                    className="terms-view-btn"
                    onClick={() => setViewingTerm(term)}
                    aria-label={`${term.title} 전문 보기`}
                >
                    보기 &rsaquo;
                </button>
            </div>
        );
    };

    const viewingChecked = viewingTerm
        ? agreedTermsIds.includes(viewingTerm.termsId)
        : false;

    return (
        <>
            <div
                className="modal-backdrop show terms-modal-backdrop"
                role="dialog"
                aria-modal="true"
                aria-labelledby="termsModalTitle"
                onClick={(e) => {
                    if (e.target === e.currentTarget && !busy) onClose?.();
                }}
            >
                <div className="modal-panel terms-modal-panel">
                    <div className="modal-heading terms-modal-head">
                        <div>
                            <h2 id="termsModalTitle">약관에 동의해 주세요</h2>
                            <p className="muted mini">
                                필수 약관에 동의하시면 가입이 완료됩니다.
                            </p>
                        </div>
                        <button
                            type="button"
                            className="terms-close-btn"
                            onClick={onClose}
                            disabled={busy}
                            aria-label="닫기"
                        >
                            ✕
                        </button>
                    </div>

                    <div className="terms-modal-body">
                        <label className={`terms-all-box ${allAgreed ? 'checked' : ''}`}>
                            <input
                                type="checkbox"
                                checked={allAgreed}
                                disabled={busy || terms.length === 0}
                                onChange={(e) => onToggleAll?.(e.target.checked)}
                            />
                            <span className="terms-all-text">
                                <strong>전체 동의하기</strong>
                                <small>선택 약관에 대한 동의 포함</small>
                            </span>
                        </label>

                        <div className="terms-list-group">
                            {requiredTerms.map(renderRow)}
                        </div>

                        {optionalTerms.length > 0 && (
                            <div className="terms-list-group terms-list-optional">
                                {optionalTerms.map(renderRow)}
                            </div>
                        )}

                        {terms.length !== 6 && (
                            <p className="terms-error-msg">
                                활성 약관 6종 등록이 필요합니다.
                            </p>
                        )}

                        {error && (
                            <p className="terms-error-msg" role="alert">
                                {error}
                            </p>
                        )}
                    </div>

                    <div className="terms-modal-actions">
                        <button
                            type="button"
                            className="btn soft"
                            onClick={onClose}
                            disabled={busy}
                        >
                            취소
                        </button>
                        <button
                            type="button"
                            className="btn primary"
                            onClick={onConfirm}
                            disabled={!canSubmit}
                        >
                            {busy ? '가입 중...' : confirmLabel}
                        </button>
                    </div>
                </div>
            </div>

            {viewingTerm && (
                <div
                    className="modal-backdrop show terms-detail-backdrop"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="termsDetailTitle"
                    onClick={(e) => {
                        if (e.target === e.currentTarget) setViewingTerm(null);
                    }}
                >
                    <div className="modal-panel terms-detail-panel">
                        <div className="modal-heading terms-modal-head">
                            <div>
                                <div className="terms-detail-meta">
                                    <span
                                        className={`terms-badge ${
                                            viewingTerm.requiredYn === 'Y' ? 'req' : 'opt'
                                        }`}
                                    >
                                        {viewingTerm.requiredYn === 'Y' ? '필수' : '선택'}
                                    </span>
                                    {viewingTerm.version && (
                                        <span className="muted mini">
                                            v{viewingTerm.version}
                                        </span>
                                    )}
                                </div>
                                <h2 id="termsDetailTitle">{viewingTerm.title}</h2>
                            </div>
                            <button
                                type="button"
                                className="terms-close-btn"
                                onClick={() => setViewingTerm(null)}
                                aria-label="약관 전문 닫기"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="terms-detail-scroll">
                            <p className="terms-detail-body">{viewingTerm.body}</p>
                        </div>

                        <div className="terms-modal-actions">
                            <button
                                type="button"
                                className="btn soft"
                                onClick={() => setViewingTerm(null)}
                            >
                                닫기
                            </button>
                            <button
                                type="button"
                                className="btn primary"
                                onClick={() => {
                                    if (!viewingChecked) {
                                        onToggleTerm?.(viewingTerm.termsId, true);
                                    }
                                    setViewingTerm(null);
                                }}
                            >
                                {viewingChecked ? '확인' : '동의하고 닫기'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
