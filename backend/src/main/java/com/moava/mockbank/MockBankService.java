package com.moava.mockbank;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MockBankService {

    private final MockBankMapper mapper;

    /**
     * 1. 인증서 등록 (최초 발급 / 재발급 시 기존 ACTIVE 인증서 자동 폐기)
     */
    @Transactional
    public MockBankDto.CertIssueResponse registerCertificate(MockBankDto.CertRegisterRequest req) {
        if (req.getUserId() == null || req.getPublicKey() == null || req.getPublicKey().isBlank()) {
            throw new IllegalArgumentException("userId와 publicKey는 필수입니다.");
        }
        if (!MockBankCryptoUtil.isValidRsaPublicKey(req.getPublicKey())) {
            throw new IllegalArgumentException("유효한 RSA 공개키가 아닙니다.");
        }

        mapper.revokeActiveCertificatesByUserId(req.getUserId());

        String serialNo = "MOAVA-CERT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusYears(1);

        MockBankDto.CertificateRecord record = new MockBankDto.CertificateRecord();
        record.setUserId(req.getUserId());
        record.setSerialNo(serialNo);
        record.setPublicKey(req.getPublicKey());
        record.setStatus("ACTIVE");
        record.setExpiresAt(expiresAt);

        mapper.insertCertificate(record);

        MockBankDto.CertIssueResponse res = new MockBankDto.CertIssueResponse();
        res.setCertSerialNo(serialNo);
        res.setIssuedAt(now);
        res.setExpiresAt(expiresAt);
        return res;
    }

    /**
     * 2. 서명용 1회성 Challenge 발급
     */
    @Transactional
    public MockBankDto.ChallengeResponse issueChallenge(MockBankDto.ChallengeRequest req) {
        if (req.getUserId() == null || req.getAccountNo() == null || req.getScope() == null) {
            throw new IllegalArgumentException("userId, scope, accountNo는 필수입니다.");
        }
        if (req.getAccountNo().isBlank()) {
            throw new IllegalArgumentException("accountNo는 필수입니다.");
        }

        String consentText;
        if ("CREDIT_ONCE".equals(req.getScope())) {
            consentText = String.format("[MOAVA] 계좌(%s) 최근 3개월 거래내역 1회 조회 및 신용분석 활용에 동의합니다.", req.getAccountNo());
        } else if ("GROUP_LEDGER_READ".equals(req.getScope())) {
            consentText = String.format("[MOAVA] 모임장 계좌(%s)의 모임 활동 시작 이후 거래내역 조회 위임에 동의합니다.", req.getAccountNo());
        } else {
            throw new IllegalArgumentException("지원하지 않는 scope입니다: " + req.getScope());
        }

        String nonce = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(3);

        MockBankDto.ChallengeRecord record = new MockBankDto.ChallengeRecord();
        record.setNonce(nonce);
        record.setUserId(req.getUserId());
        record.setAccountNo(req.getAccountNo());
        record.setScope(req.getScope());
        record.setConsentText(consentText);
        record.setUsedYn("N");
        record.setExpiresAt(expiresAt);

        mapper.insertChallenge(record);

        MockBankDto.ChallengeResponse res = new MockBankDto.ChallengeResponse();
        res.setNonce(nonce);
        res.setConsentText(consentText);
        res.setExpiresAt(expiresAt);
        return res;
    }

    /**
     * 3. 전자서명 검증 및 용도별 Access Token 발급
     */
    @Transactional
    public MockBankDto.TokenResponse verifyAndIssueToken(MockBankDto.VerifySignatureRequest req) {
        if (req == null || req.getNonce() == null || req.getNonce().isBlank()
                || req.getCertSerialNo() == null || req.getCertSerialNo().isBlank()
                || req.getSignature() == null || req.getSignature().isBlank()
                || req.getAccountNo() == null || req.getAccountNo().isBlank()) {
            throw new IllegalArgumentException("nonce, certSerialNo, signature, accountNo는 필수입니다.");
        }

        MockBankDto.ChallengeRecord challenge = mapper.findChallengeByNonce(req.getNonce());
        if (challenge == null) {
            throw new IllegalArgumentException("유효하지 않은 Challenge입니다.");
        }
        if ("Y".equals(challenge.getUsedYn()) || challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("만료되었거나 이미 사용된 서명 요청입니다.");
        }
        if (!req.getAccountNo().equals(challenge.getAccountNo())) {
            throw new SecurityException("Challenge의 계좌와 요청 계좌가 일치하지 않습니다.");
        }

        MockBankDto.CertificateRecord cert = mapper.findActiveCertificateBySerialNo(req.getCertSerialNo());
        if (cert == null || cert.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("유효한 인증서가 없습니다.");
        }
        if (!challenge.getUserId().equals(cert.getUserId())) {
            throw new SecurityException("Challenge와 인증서의 사용자 정보가 일치하지 않습니다.");
        }

        // 프론트와 동일한 원문 포맷(nonce|consentText)으로 RSA 서명 검증
        String payload = challenge.getNonce() + "|" + challenge.getConsentText();
        boolean verified = MockBankCryptoUtil.verifyRsaSignature(cert.getPublicKey(), payload, req.getSignature());
        if (!verified) {
            throw new SecurityException("전자서명 검증에 실패했습니다.");
        }

        int consumed = mapper.markChallengeUsed(challenge.getNonce());
        if (consumed != 1) {
            throw new IllegalStateException("이미 사용된 서명 요청입니다.");
        }

        String tokenId = "mb_tok_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime validFrom = null;
        LocalDateTime expiresAt;

        if ("CREDIT_ONCE".equals(challenge.getScope())) {
            expiresAt = LocalDateTime.now().plusMinutes(10);
        } else {
            // 모임장 계좌: 활동 시작 시점을 토큰에 고정
            validFrom = (req.getActivityStartedAt() != null) ? req.getActivityStartedAt() : LocalDateTime.now();
            expiresAt = LocalDateTime.now().plusYears(1);
        }

        MockBankDto.ConsentTokenRecord token = new MockBankDto.ConsentTokenRecord();
        token.setTokenId(tokenId);
        token.setUserId(cert.getUserId());
        token.setAccountNo(challenge.getAccountNo());
        token.setScope(challenge.getScope());
        token.setValidFrom(validFrom);
        token.setUsedYn("N");
        token.setSignatureHash(MockBankCryptoUtil.sha256Hex(req.getSignature()));
        token.setExpiresAt(expiresAt);

        mapper.insertConsentToken(token);

        MockBankDto.TokenResponse res = new MockBankDto.TokenResponse();
        res.setAccessToken(tokenId);
        res.setScope(challenge.getScope());
        res.setValidFrom(validFrom);
        res.setExpiresAt(expiresAt);
        return res;
    }

    /**
     * 4. 거래내역 조회 (1회성 소진 + 모임장 계좌 과거 이력 원천 차단)
     */
    @Transactional
    public List<MockBankDto.TransactionDto> getTransactions(String tokenId, LocalDateTime requestedFrom, LocalDateTime requestedTo) {
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("토큰은 필수입니다.");
        }

        MockBankDto.ConsentTokenRecord token = mapper.findConsentTokenById(tokenId);
        if (token == null) {
            throw new SecurityException("유효하지 않은 모의 은행 토큰입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (token.getExpiresAt().isBefore(now)) {
            throw new SecurityException("만료된 토큰입니다.");
        }

        LocalDateTime to = (requestedTo != null) ? requestedTo : now;
        if (to.isAfter(now)) {
            throw new IllegalArgumentException("조회 종료 시각은 현재 시각을 넘을 수 없습니다.");
        }

        // CASE A: 개인 신용분석용 (최근 3개월 1회 조회 후 즉시 폐기)
        if ("CREDIT_ONCE".equals(token.getScope())) {
            if ("Y".equals(token.getUsedYn())) {
                throw new SecurityException("이미 1회 조회가 완료되어 폐기된 토큰입니다.");
            }
            LocalDateTime threeMonthsAgo = now.minusMonths(3);
            LocalDateTime from = (requestedFrom != null && requestedFrom.isAfter(threeMonthsAgo))
                    ? requestedFrom : threeMonthsAgo;
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
            }
            List<MockBankDto.TransactionDto> list = mapper.findTransactions(token.getAccountNo(), from, to);
            int consumed = mapper.markTokenUsed(tokenId);
            if (consumed != 1) {
                throw new IllegalStateException("이미 사용된 조회 토큰입니다.");
            }
            return list;
        }

        // CASE B: 모임장 계좌 (활동 시작 이후만 강제 조회)
        if ("GROUP_LEDGER_READ".equals(token.getScope())) {
            if (token.getValidFrom() == null) {
                throw new SecurityException("모임 활동 시작 시각이 설정되지 않은 토큰입니다.");
            }
            LocalDateTime effectiveFrom = token.getValidFrom();
            if (requestedFrom != null && requestedFrom.isAfter(effectiveFrom)) {
                effectiveFrom = requestedFrom;
            }
            if (effectiveFrom.isAfter(to)) {
                throw new IllegalArgumentException("조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
            }
            return mapper.findTransactions(token.getAccountNo(), effectiveFrom, to);
        }

        throw new SecurityException("허용되지 않은 Scope입니다.");
    }
}