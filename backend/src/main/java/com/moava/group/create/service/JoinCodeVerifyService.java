
package com.moava.group.create.service;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.create.dto.JoinCodeVerifyResponse;
import com.moava.group.create.mapper.JoinCodeAttemptMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class JoinCodeVerifyService {

    private final JoinCodeAttemptMapper mapper;
    private final CryptoKeyService cryptoKeyService;

    public JoinCodeVerifyService(
            JoinCodeAttemptMapper mapper,
            CryptoKeyService cryptoKeyService
    ) {
        this.mapper = mapper;
        this.cryptoKeyService = cryptoKeyService;
    }

    /**
     * F18 - 인증 요청 사전 상태 확인
     *
     * 1. 모임 및 회원 ID 확인
     * 2. 비공개 모임의 인증코드 존재 여부 확인
     * 3. 회원별 인증 시도 기록 생성
     * 4. 인증코드 재발급 버전 동기화
     * 5. 잠금 여부 확인
     * 6. 기존 인증 성공 여부 확인
     *
     * 이 메서드는 실제 인증코드를 비교하지 않는다.
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
     * 이미 인증한 회원도 입력한 코드를 다시 비교한다.
     * - 올바른 코드 재입력: ALREADY_VERIFIED
     * - 잘못된 코드 재입력: INVALID_CODE
     * - 기존 인증 성공 상태는 유지
     * - 미인증 회원의 5회 실패 / 15분 잠금 유지
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

        // 잠금 중이면 코드 비교 없이 차단
        if ("LOCKED".equals(state.status())) {
            return state;
        }

        boolean alreadyVerified =
                "ALREADY_VERIFIED".equals(state.status());


        // 3. 입력 형식 검증
        if (inputCode == null || !inputCode.matches("[0-9]{6}")) {
        return recordInvalidCode(groupId, memberId);
        }


        // 4. 암호화된 현재 인증코드 조회
        Map<String, Object> codeRow =
                mapper.findEncryptedJoinCode(groupId);

        if (codeRow == null || codeRow.get("JOIN_CODE_ENC") == null) {
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

        if (savedCode == null || !savedCode.matches("[0-9]{6}")) {
            throw new IllegalStateException(
                    "저장된 인증코드 형식이 올바르지 않습니다."
            );
        }

        // 6. 실제 입력 코드 비교
        boolean matched = MessageDigest.isEqual(
                savedCode.getBytes(StandardCharsets.UTF_8),
                inputCode.getBytes(StandardCharsets.UTF_8)
        );


        // 7. 이미 인증한 회원의 재인증 처리
        if (alreadyVerified) {
        if (!matched) {
                return recordInvalidCode(groupId, memberId);
        }

        return new JoinCodeVerifyResponse(
                true,
                JoinCodeVerifyStatus.ALREADY_VERIFIED.name(),
                "이미 인증이 완료되었습니다.",
                state.remainingAttempts()
        );
        }


        // 8. 최초 인증 성공
        if (matched) {
            int updated =
                    mapper.markVerified(groupId, memberId);

            if (updated != 1) {
                throw new IllegalStateException(
                        "인증 성공 처리에 실패했습니다."
                );
            }

            return new JoinCodeVerifyResponse(
                    true,
                    JoinCodeVerifyStatus.VERIFIED.name(),
                    "인증코드 확인이 완료되었습니다.",
                    5
            );
        }

        // 9. 최초 인증 실패
        return recordInvalidCode(groupId, memberId);
    }



    /**
     * 인증 준비 공통 처리
     *
     * 같은 모임의 인증 요청을 직렬화하여
     * 최초 INSERT 및 코드 재발급과의 충돌을 방지한다.
     *
     * 호출한 public 메서드의 트랜잭션 안에서 실행된다.
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

        // 3. DB의 현재 인증코드 버전 조회
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

            if (attempt == null) {
                throw new IllegalStateException(
                        "인증 시도 기록을 확인할 수 없습니다."
                );
            }
        }

        // 7. 인증코드 재발급 버전 확인
        Number storedVersion =
                (Number) attempt.get("CODE_VERSION");

        if (storedVersion == null
                || storedVersion.intValue()
                        != codeVersion.intValue()) {

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

        // 8. 잠금 시간이 만료되었으면 초기화
        if (attempt.get("LOCKED_UNTIL") != null) {

            mapper.resetExpiredLock(
                    groupId,
                    memberId
            );

            attempt = mapper.findAttempt(
                    groupId,
                    memberId
            );
        }

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

        Number failCount = (Number) attempt.get("FAIL_COUNT");

        int failures = failCount == null
                ? 0
                : failCount.intValue();

        int remaining = Math.max(0, 5 - failures);

        // 잠금 중에도 기존 인증 성공 여부는 유지
        if (attempt.get("LOCKED_UNTIL") != null) {
                return new JoinCodeVerifyResponse(
                        verified,
                        JoinCodeVerifyStatus.LOCKED.name(),
                        "인증 시도가 일시적으로 제한되었습니다.",
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
     * 실패 결과를 예외가 아닌 정상 응답으로 반환하여
     * FAIL_COUNT 변경이 트랜잭션에 반영되도록 한다.
     */
    private JoinCodeVerifyResponse recordInvalidCode(
            Long groupId,
            Long memberId
    ) {

        // 1. 실패 횟수 증가
        int updated =
                mapper.increaseFailCount(
                        groupId,
                        memberId
                );

        if (updated != 1) {
            throw new IllegalStateException(
                    "인증 실패 횟수 갱신에 실패했습니다."
            );
        }

        // 2. 변경된 시도 기록 조회
        Map<String, Object> attempt =
                mapper.findAttempt(
                        groupId,
                        memberId
                );

        if (attempt == null) {
            throw new IllegalStateException(
                    "인증 시도 기록을 확인할 수 없습니다."
            );
        }

        Number failCount =
                (Number) attempt.get("FAIL_COUNT");

        int failures =
                failCount == null
                        ? 0
                        : failCount.intValue();

        int remaining =
                Math.max(0, 5 - failures);

        // 3. 5회 실패 시 잠금

        if (remaining == 0) {
        boolean verified = "Y".equalsIgnoreCase(
                String.valueOf(attempt.get("VERIFIED_YN"))
        );

        return new JoinCodeVerifyResponse(
                verified,
                JoinCodeVerifyStatus.LOCKED.name(),
                "인증에 5회 실패하여 15분간 잠금 처리되었습니다.",
                0
        );
        }


        // 4. 일반 인증 실패
        return new JoinCodeVerifyResponse(
                false,
                JoinCodeVerifyStatus.INVALID_CODE.name(),
                "인증코드가 일치하지 않습니다.",
                remaining
        );
    }
}
