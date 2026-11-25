package com.moava.group.create.service;

import com.moava.group.create.mapper.JoinCodeAttemptMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class JoinCodeAttemptService {

    // F18 - 회원별 최대 인증 실패 횟수
    private static final int MAX_ATTEMPTS = 3;

    private static final String LOCKED_MESSAGE =
            "인증에 3회 실패했습니다. 모임장에게 인증코드 재발급을 요청해 주세요.";

    private final JoinCodeAttemptMapper mapper;

    public JoinCodeAttemptService(
            JoinCodeAttemptMapper mapper
    ) {
        this.mapper = mapper;
    }

    /**
     * 인증 시도 전 잠금 여부 확인
     *
     * 회원별 실패 횟수가 3회 이상이면 잠금 상태이다.
     * 인증코드 재발급 전까지 자동 해제하지 않는다.
     */
    @Transactional
    public boolean isLocked(
            Long groupId,
            Long memberId
    ) {

        Map<String, Object> attempt =
                prepareAndFindAttempt(groupId, memberId);

        return getFailCount(attempt) >= MAX_ATTEMPTS;
    }

    /**
     * 실제 인증코드 검증 결과가 실패일 때 호출
     */
    @Transactional
    public void recordFailure(
            Long groupId,
            Long memberId
    ) {

        Map<String, Object> attempt =
                prepareAndFindAttempt(groupId, memberId);

        // 이미 3회 실패한 회원은 추가 인증 시도 차단
        if (getFailCount(attempt) >= MAX_ATTEMPTS) {

            throw new IllegalStateException(
                    LOCKED_MESSAGE
            );
        }

        int updated = mapper.increaseFailCount(
                groupId,
                memberId
        );

        if (updated != 1) {

            throw new IllegalStateException(
                    "인증 실패 횟수 갱신에 실패했습니다."
            );
        }
    }

    /**
     * 실제 인증코드 검증 결과가 성공일 때 호출
     */
    @Transactional
    public void recordSuccess(
            Long groupId,
            Long memberId
    ) {

        Map<String, Object> attempt =
                prepareAndFindAttempt(groupId, memberId);

        // 잠금 상태에서는 인증 성공 처리 불가
        if (getFailCount(attempt) >= MAX_ATTEMPTS) {

            throw new IllegalStateException(
                    LOCKED_MESSAGE
            );
        }

        // 이미 현재 버전의 인증을 완료한 경우
        if ("Y".equalsIgnoreCase(
                String.valueOf(attempt.get("VERIFIED_YN"))
        )) {
            return;
        }

        int updated = mapper.markVerified(
                groupId,
                memberId
        );

        if (updated != 1) {

            throw new IllegalStateException(
                    "인증 성공 상태 갱신에 실패했습니다."
            );
        }
    }

    /**
     * 서버 DB 기준 인증 시도 기록 생성 및 버전 동기화
     */
    @Transactional
    public void prepareAttempt(
            Long groupId,
            Long memberId
    ) {

        prepareAndFindAttempt(groupId, memberId);
    }

    /**
     * 인증 시도 기록 생성 및 현재 코드 버전 동기화
     *
     * 모임 행을 먼저 잠가 동시 인증 요청 및
     * 인증코드 재발급 사이의 충돌을 방지한다.
     */
    private Map<String, Object> prepareAndFindAttempt(
            Long groupId,
            Long memberId
    ) {

        // 1. ID 유효성 확인
        if (groupId == null || groupId <= 0
                || memberId == null || memberId <= 0) {

            throw new IllegalArgumentException(
                    "인증 시도 정보가 올바르지 않습니다."
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

        // 3. 서버 DB의 현재 인증코드 버전 조회
        Integer codeVersion =
                mapper.findCurrentCodeVersion(groupId);

        if (codeVersion == null) {

            throw new IllegalStateException(
                    "인증코드가 발급된 비공개 모임이 아닙니다."
            );
        }

        // 4. 회원별 인증 시도 기록 조회
        Map<String, Object> attempt =
                mapper.findAttempt(groupId, memberId);

        // 5. 최초 인증 시도 기록 생성
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

        // 6. 인증코드 재발급 여부 확인
        Number currentVersion =
                (Number) attempt.get("CODE_VERSION");

        if (currentVersion == null
                || currentVersion.intValue()
                        != codeVersion.intValue()) {

            // 재발급된 경우 실패 횟수 및 인증 상태 초기화
            int updated = mapper.resetForNewVersion(
                    groupId,
                    memberId,
                    codeVersion
            );

            if (updated != 1) {

                throw new IllegalStateException(
                        "인증코드 버전 갱신에 실패했습니다."
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

        return attempt;
    }

    /**
     * 회원별 인증 실패 횟수 조회
     */
    private int getFailCount(
            Map<String, Object> attempt
    ) {

        Number failCount =
                (Number) attempt.get("FAIL_COUNT");

        return failCount == null
                ? 0
                : failCount.intValue();
    }

    /**
     * 서버에 저장된 현재 인증코드 버전 조회
     */
    @Transactional(readOnly = true)
    public Integer getCurrentCodeVersion(
            Long groupId
    ) {

        if (groupId == null || groupId <= 0) {

            throw new IllegalArgumentException(
                    "모임 ID가 필요합니다."
            );
        }

        Integer version =
                mapper.findCurrentCodeVersion(groupId);

        if (version == null) {

            throw new IllegalStateException(
                    "인증코드가 발급된 비공개 모임을 찾을 수 없습니다."
            );
        }

        return version;
    }
}