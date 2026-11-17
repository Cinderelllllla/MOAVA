
package com.moava.group.create.service;

import com.moava.group.create.mapper.JoinCodeAttemptMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

@Service
public class JoinCodeAttemptService {

    private final JoinCodeAttemptMapper mapper;

    public JoinCodeAttemptService(JoinCodeAttemptMapper mapper) {
        this.mapper = mapper;
    }

    // 인증 시도 전 잠금 여부 확인
    @Transactional
    public boolean isLocked(Long groupId, Long memberId) {

        Map<String, Object> attempt =
                mapper.findAttempt(groupId, memberId);

        if (attempt == null) {
            return false;
        }

        Object lockedUntil = attempt.get("LOCKED_UNTIL");

        if (lockedUntil == null) {
            return false;
        }

        LocalDateTime lockEnd = toLocalDateTime(lockedUntil);

        if (lockEnd.isAfter(LocalDateTime.now())) {
            return true;
        }

        // 잠금 시간이 지났으면 실패 횟수 초기화
        mapper.resetExpiredLock(groupId, memberId);

        return false;
    }

    // 실제 인증코드 검증 결과가 실패일 때 호출
    @Transactional
    public void recordFailure(Long groupId, Long memberId) {

        Map<String, Object> attempt =
                mapper.findAttempt(groupId, memberId);

        if (attempt == null) {
            throw new IllegalStateException(
                    "인증 시도 기록이 없습니다."
            );
        }

        Object lockedUntil = attempt.get("LOCKED_UNTIL");

        if (lockedUntil != null) {
            LocalDateTime lockEnd = toLocalDateTime(lockedUntil);

            if (lockEnd.isAfter(LocalDateTime.now())) {
                throw new IllegalStateException(
                        "인증 시도가 잠금 상태입니다."
                );
            }

            mapper.resetExpiredLock(groupId, memberId);
        }

        int updated = mapper.increaseFailCount(groupId, memberId);

        if (updated != 1) {
            throw new IllegalStateException(
                    "인증 실패 횟수 갱신에 실패했습니다."
            );
        }
    }

    // 실제 인증코드 검증 결과가 성공일 때 호출
    @Transactional
    public void recordSuccess(Long groupId, Long memberId) {

        Map<String, Object> attempt =
                mapper.findAttempt(groupId, memberId);

        if (attempt == null) {
            throw new IllegalStateException(
                    "인증 시도 기록이 없습니다."
            );
        }

        Object lockedUntil = attempt.get("LOCKED_UNTIL");

        if (lockedUntil != null) {
            LocalDateTime lockEnd = toLocalDateTime(lockedUntil);

            if (lockEnd.isAfter(LocalDateTime.now())) {
                throw new IllegalStateException(
                        "잠금 중에는 인증할 수 없습니다."
                );
            }

            mapper.resetExpiredLock(groupId, memberId);
        }

        int updated = mapper.markVerified(groupId, memberId);

        if (updated != 1) {
            throw new IllegalStateException(
                    "인증 성공 상태 갱신에 실패했습니다."
            );
        }
    }

    // Oracle DATE / JDBC Timestamp 변환
    private LocalDateTime toLocalDateTime(Object value) {

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(
                    date.toInstant(),
                    ZoneId.systemDefault()
            );
        }

        throw new IllegalStateException(
                "지원하지 않는 날짜 형식입니다."
        );
    }




// 서버 DB 기준으로 인증 시도 기록 생성 또는 버전 동기화
@Transactional
public void prepareAttempt(
        Long groupId,
        Long memberId
) {
    if (groupId == null || memberId == null) {
        throw new IllegalArgumentException(
                "인증 시도 정보가 올바르지 않습니다."
        );
    }

    // 모임 행 잠금: 최초 인증 시도 INSERT 충돌 방지
    Long lockedGroupId =
            mapper.lockGroupForAttempt(groupId);

    if (lockedGroupId == null) {
        throw new IllegalStateException(
                "존재하지 않는 모임입니다."
        );
    }

    // 클라이언트 값이 아닌 서버 DB의 현재 버전 사용
    Integer codeVersion =
            mapper.findCurrentCodeVersion(groupId);

    if (codeVersion == null) {
        throw new IllegalStateException(
                "인증코드가 발급된 비공개 모임이 아닙니다."
        );
    }

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

        return;
    }

    Object currentVersion =
            attempt.get("CODE_VERSION");

    if (currentVersion == null
            || ((Number) currentVersion).intValue()
                != codeVersion.intValue()) {

        int updated = mapper.resetForNewVersion(
                groupId, memberId, codeVersion
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "인증코드 버전 갱신에 실패했습니다."
            );
        }
    }
}


    // 서버에 저장된 현재 인증코드 버전 조회
    @Transactional(readOnly = true)
    public Integer getCurrentCodeVersion(Long groupId) {

        if (groupId == null) {
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
