
package com.moava.group.create.service;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.create.dto.JoinCodeVerifyResponse;
import com.moava.group.create.mapper.JoinCodeAttemptMapper;
import com.moava.group.create.mapper.GroupJoinRequestMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class JoinCodeVerifyService {

    // F18 - 회원별 최대 인증 실패 횟수
    private static final int MAX_ATTEMPTS = 3;

    private static final String LOCKED_MESSAGE =
            "인증에 3회 실패했습니다. 모임장에게 인증코드 재발급을 요청해 주세요.";

    private final JoinCodeAttemptMapper mapper;
    private final CryptoKeyService cryptoKeyService;
    private final GroupJoinRequestMapper auditMapper;

    public JoinCodeVerifyService(
            JoinCodeAttemptMapper mapper,
            CryptoKeyService cryptoKeyService,
            GroupJoinRequestMapper auditMapper
    ) {
        this.mapper = mapper;
        this.cryptoKeyService = cryptoKeyService;
        this.auditMapper = auditMapper;
    }

    /**
     * F18 - 인증 관련 공통 감사 로그 저장
     *
     * 인증코드 원문, 입력 코드, 암호문, 암호화 키,
     * 초대 토큰은 기록하지 않는다.
     */
    private void saveVerificationAuditLog(
            Long groupId,
            Long memberId,
            String eventType,
            String description
    ) {

        int inserted = auditMapper.insertAuditLog(
                groupId,
                memberId,
                memberId,
                eventType,
                description
        );

        if (inserted != 1) {

            throw new IllegalStateException(
                    "인증코드 감사 로그 저장에 실패했습니다."
            );
        }
    }

    /**
     * F18 - 인증 요청 사전 상태 확인
     *
     * 현재 인증코드 버전과 회원별 인증 상태를 확인한다.
     * 실제 인증코드를 비교하지 않는다.
     *
     * 단순 상태 조회는 감사 로그에 기록하지 않는다.
     */
    @Transactional
    public JoinCodeVerifyResponse checkVerificationState(
            Long groupId,
            Long memberId
    ) {

        Map<String, Object> attempt =
                prepareVerification(groupId, memberId);

        return buildVerificationState(attempt);
    }

    /**
     * F18 - 실제 인증코드 검증
     *
     * 회원별 최대 3회 실패 허용
     * 3회 실패 시 모임장의 코드 재발급 전까지 인증 차단
     * 다른 회원의 인증 시도에는 영향을 주지 않는다.
     */
    @Transactional
    public JoinCodeVerifyResponse verifyJoinCode(
            Long groupId,
            Long memberId,
            String inputCode
    ) {

        // 1. 모임 및 회원 검증, 코드 버전 동기화
        Map<String, Object> attempt =
                prepareVerification(groupId, memberId);

        // 2. 현재 인증 상태 확인
        JoinCodeVerifyResponse state =
                buildVerificationState(attempt);

        // 3회 실패한 회원은 재발급 전까지 인증 차단
        // 이미 잠긴 상태의 반복 요청은 추가 로그를 저장하지 않는다.
        if ("LOCKED".equals(state.status())) {
            return state;
        }

        boolean alreadyVerified =
                "ALREADY_VERIFIED".equals(state.status());

        // 3. 입력 형식 검증
        if (inputCode == null
                || !inputCode.matches("[0-9]{6}")) {

            return recordInvalidCode(groupId, memberId);
        }

        // 4. 암호화된 현재 인증코드 조회
        Map<String, Object> codeRow =
                mapper.findEncryptedJoinCode(groupId);

        if (codeRow == null
                || codeRow.get("JOIN_CODE_ENC") == null) {

            throw new IllegalStateException(
                    "인증코드 정보가 없습니다."
            );
        }

        Number keyVersionValue =
                (Number) codeRow.get("JOIN_CODE_KEY_VER");

        if (keyVersionValue == null) {
            throw new IllegalStateException(
                    "인증코드 암호화 키 버전이 없습니다."
            );
        }

        int keyVersion = keyVersionValue.intValue();

        String encryptedCode =
                (String) codeRow.get("JOIN_CODE_ENC");

        // 5. 공통 암호화 서비스로 복호화
        String savedCode = cryptoKeyService.decrypt(
                encryptedCode,
                keyVersion
        );

        if (savedCode == null
                || !savedCode.matches("[0-9]{6}")) {

            throw new IllegalStateException(
                    "저장된 인증코드 형식이 올바르지 않습니다."
            );
        }

        // 6. 실제 입력 코드 비교
        boolean matched = MessageDigest.isEqual(
                savedCode.getBytes(StandardCharsets.UTF_8),
                inputCode.getBytes(StandardCharsets.UTF_8)
        );

        // 7. 인증 실패
        if (!matched) {
            return recordInvalidCode(groupId, memberId);
        }

        // 8. 이미 인증한 회원의 올바른 코드 재입력
        if (alreadyVerified) {

            // 기존 인증 완료 회원의 재확인 성공 감사 로그
            saveVerificationAuditLog(
                    groupId,
                    memberId,
                    "JOIN_CODE_VERIFY",
                    "비공개 모임 인증코드 재확인 성공"
                            + " (버전: "
                            + attempt.get("CODE_VERSION")
                            + ")"
            );

            return new JoinCodeVerifyResponse(
                    true,
                    JoinCodeVerifyStatus.ALREADY_VERIFIED.name(),
                    "이미 인증이 완료되었습니다.",
                    state.remainingAttempts()
            );
        }

        // 9. 최초 인증 성공
        int updated =
                mapper.markVerified(groupId, memberId);

        if (updated != 1) {
            throw new IllegalStateException(
                    "인증 성공 처리에 실패했습니다."
            );
        }

        // 10. 인증 성공 감사 로그 저장
        saveVerificationAuditLog(
                groupId,
                memberId,
                "JOIN_CODE_VERIFY",
                "비공개 모임 인증코드 검증 성공"
                        + " (버전: "
                        + attempt.get("CODE_VERSION")
                        + ")"
        );

        return new JoinCodeVerifyResponse(
                true,
                JoinCodeVerifyStatus.VERIFIED.name(),
                "인증코드 확인이 완료되었습니다.",
                MAX_ATTEMPTS
        );
    }

    /**
     * 인증 준비 공통 처리
     *
     * 모임 행 잠금을 통해 동시 인증 요청과
     * 인증코드 재발급 사이의 충돌을 방지한다.
     */
    private Map<String, Object> prepareVerification(
            Long groupId,
            Long memberId
    ) {

        // 1. ID 유효성 확인
        if (groupId == null || groupId <= 0
                || memberId == null || memberId <= 0) {

            throw new IllegalArgumentException(
                    "모임 또는 회원 정보가 올바르지 않습니다."
            );
        }

        // 2. 모임 행 잠금
        Long lockedGroupId =
                mapper.lockGroupForAttempt(groupId);

        if (lockedGroupId == null) {
            throw new IllegalStateException(
                    "존재하지 않는 모임입니다."
            );
        }

        // 3. 현재 인증코드 버전 조회
        Integer codeVersion =
                mapper.findCurrentCodeVersion(groupId);

        if (codeVersion == null) {
            throw new IllegalStateException(
                    "인증코드가 발급된 비공개 모임이 아닙니다."
            );
        }

        // 4. 암호화된 인증코드 존재 확인
        Map<String, Object> encryptedCode =
                mapper.findEncryptedJoinCode(groupId);

        if (encryptedCode == null
                || encryptedCode.get("JOIN_CODE_ENC") == null) {

            throw new IllegalStateException(
                    "인증코드 정보가 없습니다."
            );
        }

        // 5. 회원별 인증 시도 기록 조회
        Map<String, Object> attempt =
                mapper.findAttempt(groupId, memberId);

        // 6. 최초 인증 시도 기록 생성
        if (attempt == null) {

            int inserted = mapper.insertAttempt(
                    groupId,
                    memberId,
                    codeVersion
            );

            if (inserted != 1) {
                throw new IllegalStateException(
                        "인증 시도 기록 생성에 실패했습니다."
                );
            }

            attempt = mapper.findAttempt(
                    groupId,
                    memberId
            );
        }

        if (attempt == null) {
            throw new IllegalStateException(
                    "인증 시도 기록을 확인할 수 없습니다."
            );
        }

        // 7. 인증코드 재발급 버전 확인
        Number storedVersion =
                (Number) attempt.get("CODE_VERSION");

        if (storedVersion == null
                || storedVersion.intValue()
                        != codeVersion.intValue()) {

            // 새 인증코드 버전에서는 실패 횟수 및 인증 상태 초기화
            int updated = mapper.resetForNewVersion(
                    groupId,
                    memberId,
                    codeVersion
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "인증코드 버전 동기화에 실패했습니다."
                );
            }

            attempt = mapper.findAttempt(
                    groupId,
                    memberId
            );
        }

        if (attempt == null) {
            throw new IllegalStateException(
                    "인증 시도 기록을 확인할 수 없습니다."
            );
        }

        // 시간 경과에 따른 잠금 해제는 하지 않는다.
        return attempt;
    }

    /**
     * 현재 인증 가능 상태 확인
     */
    private JoinCodeVerifyResponse buildVerificationState(
            Map<String, Object> attempt
    ) {

        if (attempt == null) {
            throw new IllegalStateException(
                    "인증 시도 정보가 없습니다."
            );
        }

        boolean verified = "Y".equalsIgnoreCase(
                String.valueOf(attempt.get("VERIFIED_YN"))
        );

        Number failCount =
                (Number) attempt.get("FAIL_COUNT");

        int failures = failCount == null
                ? 0
                : failCount.intValue();

        int remaining =
                Math.max(0, MAX_ATTEMPTS - failures);

        // 3회 이상 실패하면 코드 재발급 전까지 차단
        if (failures >= MAX_ATTEMPTS) {

            return new JoinCodeVerifyResponse(
                    false,
                    JoinCodeVerifyStatus.LOCKED.name(),
                    LOCKED_MESSAGE,
                    0
            );
        }

        // 기존 인증 완료 상태
        if (verified) {

            return new JoinCodeVerifyResponse(
                    true,
                    JoinCodeVerifyStatus.ALREADY_VERIFIED.name(),
                    "이미 인증이 완료되었습니다.",
                    remaining
            );
        }

        return new JoinCodeVerifyResponse(
                false,
                "READY",
                "인증코드를 입력할 수 있습니다.",
                remaining
        );
    }

    /**
     * 인증 실패 기록
     *
     * 실패 결과를 정상 응답으로 반환하여
     * FAIL_COUNT 변경이 트랜잭션에 반영되도록 한다.
     *
     * 인증 실패 및 3회 실패 잠금 감사 로그도
     * 동일한 트랜잭션에서 저장한다.
     */
    private JoinCodeVerifyResponse recordInvalidCode(
            Long groupId,
            Long memberId
    ) {

        // 1. 실패 횟수 증가
        int updated = mapper.increaseFailCount(
                groupId,
                memberId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "인증 실패 횟수 갱신에 실패했습니다."
            );
        }

        // 2. 변경된 인증 시도 기록 조회
        Map<String, Object> attempt =
                mapper.findAttempt(groupId, memberId);

        if (attempt == null) {
            throw new IllegalStateException(
                    "인증 시도 기록을 확인할 수 없습니다."
            );
        }

        Number failCount =
                (Number) attempt.get("FAIL_COUNT");

        int failures = failCount == null
                ? 0
                : failCount.intValue();

        int remaining =
                Math.max(0, MAX_ATTEMPTS - failures);

        // 3. 인증 실패 감사 로그 저장
        // 실제 입력한 인증코드는 기록하지 않는다.
        saveVerificationAuditLog(
                groupId,
                memberId,
                "JOIN_CODE_VERIFY",
                "비공개 모임 인증코드 검증 실패"
                        + " (버전: "
                        + attempt.get("CODE_VERSION")
                        + ", 실패 횟수: "
                        + failures
                        + "/"
                        + MAX_ATTEMPTS
                        + ")"
        );

        // 4. 최대 실패 횟수 도달
        if (remaining == 0) {

            // 3회 실패 잠금 감사 로그 저장
            saveVerificationAuditLog(
                    groupId,
                    memberId,
                    "JOIN_CODE_LOCK",
                    "인증코드 3회 실패로 회원별 인증 잠금"
                            + " (버전: "
                            + attempt.get("CODE_VERSION")
                            + ")"
            );

            return new JoinCodeVerifyResponse(
                    false,
                    JoinCodeVerifyStatus.LOCKED.name(),
                    LOCKED_MESSAGE,
                    0
            );
        }

        // 5. 일반 인증 실패
        return new JoinCodeVerifyResponse(
                false,
                JoinCodeVerifyStatus.INVALID_CODE.name(),
                "인증코드가 일치하지 않습니다.",
                remaining
        );
    }
}
