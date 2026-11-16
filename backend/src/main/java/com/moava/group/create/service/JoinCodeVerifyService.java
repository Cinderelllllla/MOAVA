
package com.moava.group.create.service;

import com.moava.group.create.dto.JoinCodeVerifyResponse;
import com.moava.group.create.mapper.JoinCodeAttemptMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class JoinCodeVerifyService {

    private final JoinCodeAttemptMapper mapper;

    public JoinCodeVerifyService(
            JoinCodeAttemptMapper mapper
    ) {
        this.mapper = mapper;
    }

    /**
     * 인증 요청의 사전 상태 확인
     *
     * 현재 단계에서는 AES 검증을 수행하지 않는다.
     * 실제 인증 API에 연결하기 전까지 내부 준비용으로만 사용한다.
     */
    @Transactional
    public JoinCodeVerifyResponse checkVerificationState(
            Long groupId,
            Long memberId
    ) {
        if (groupId == null || memberId == null
                || groupId <= 0 || memberId <= 0) {
            throw new IllegalArgumentException(
                    "모임 또는 회원 정보가 올바르지 않습니다."
            );
        }

        // 같은 모임의 인증 요청을 직렬화
        Long lockedGroupId =
                mapper.lockGroupForAttempt(groupId);

        if (lockedGroupId == null) {
            throw new IllegalStateException(
                    "존재하지 않는 모임입니다."
            );
        }

        // 서버 DB의 인증코드 버전 조회
        Integer codeVersion =
                mapper.findCurrentCodeVersion(groupId);

        if (codeVersion == null) {
            throw new IllegalStateException(
                    "인증코드가 발급된 비공개 모임이 아닙니다."
            );
        }

        // 암호화된 코드 존재 여부 확인
        // 실제 복호화는 공통 보안 서비스 연동 후 진행
        Map<String, Object> encryptedCode =
                mapper.findEncryptedJoinCode(groupId);

        if (encryptedCode == null
                || encryptedCode.get("JOIN_CODE_ENC") == null) {
            throw new IllegalStateException(
                    "인증코드 정보가 없습니다."
            );
        }

        // 회원별 인증 시도 기록 조회
        Map<String, Object> attempt =
                mapper.findAttempt(groupId, memberId);

        if (attempt == null) {
            int inserted = mapper.insertAttempt(
                    groupId, memberId, codeVersion
            );

            if (inserted != 1) {
                throw new IllegalStateException(
                        "인증 시도 기록 생성에 실패했습니다."
                );
            }

            attempt = mapper.findAttempt(groupId, memberId);

            if (attempt == null) {
                throw new IllegalStateException(
                        "인증 시도 기록을 확인할 수 없습니다."
                );
            }
        }

        // 코드 재발급 시 버전 동기화
        Number storedVersion =
                (Number) attempt.get("CODE_VERSION");

        if (storedVersion == null
                || storedVersion.intValue() != codeVersion) {

            int updated = mapper.resetForNewVersion(
                    groupId, memberId, codeVersion
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "인증코드 버전 동기화에 실패했습니다."
                );
            }

            attempt = mapper.findAttempt(groupId, memberId);
        }

        // DB 기준 잠금 만료 여부 확인
        if (attempt.get("LOCKED_UNTIL") != null) {

            // 만료된 잠금만 초기화
            mapper.resetExpiredLock(groupId, memberId);

            attempt = mapper.findAttempt(groupId, memberId);

            // 아직 잠금 중이라면 LOCKED_UNTIL이 남아 있음
            if (attempt.get("LOCKED_UNTIL") != null) {
                return new JoinCodeVerifyResponse(
                        false,
                        JoinCodeVerifyStatus.LOCKED.name(),
                        "인증 시도가 일시적으로 제한되었습니다.",
                        0
                );
            }
        }

        String verifiedYn =
                String.valueOf(attempt.get("VERIFIED_YN"));

        if ("Y".equalsIgnoreCase(verifiedYn)) {
            return new JoinCodeVerifyResponse(
                    true,
                    JoinCodeVerifyStatus.ALREADY_VERIFIED.name(),
                    "이미 인증이 완료되었습니다.",
                    5
            );
        }

        Number failCount =
                (Number) attempt.get("FAIL_COUNT");

        int failures =
                failCount == null ? 0 : failCount.intValue();

        // AES 검증이 아직 연결되지 않았으므로
        // 이 메서드는 인증 성공이나 실패를 기록하지 않는다.
        return new JoinCodeVerifyResponse(
                false,
                "READY",
                "인증코드 검증을 진행할 수 있는 상태입니다.",
                Math.max(0, 5 - failures)
        );
    }
}
