
package com.moava.group.create.service;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.create.mapper.GroupJoinCodeIssueMapper;
import com.moava.group.create.mapper.GroupJoinRequestMapper;
import com.moava.group.create.dto.GroupJoinCodeIssueResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class GroupJoinCodeIssueService {

    private final GroupJoinCodeIssueMapper mapper;
    private final CryptoKeyService cryptoKeyService;
    private final GroupJoinRequestMapper auditMapper;

    public GroupJoinCodeIssueService(
            GroupJoinCodeIssueMapper mapper,
            CryptoKeyService cryptoKeyService,
            GroupJoinRequestMapper auditMapper
    ) {
        this.mapper = mapper;
        this.cryptoKeyService = cryptoKeyService;
        this.auditMapper = auditMapper;
    }

    /**
     * 인증코드 발급 전 모임장 권한 및 비공개 모임 여부 확인
     */
    @Transactional
    public int validateIssuePermission(
            Long groupId,
            Long memberId
    ) {

        Map<String, Object> group =
                findAuthorizedGroup(groupId, memberId);

        return getCodeVersion(group);
    }

    /**
     * 모임장 권한 확인 및 모임 행 잠금
     */
    private Map<String, Object> findAuthorizedGroup(
            Long groupId,
            Long memberId
    ) {

        if (groupId == null || groupId <= 0) {

            throw new IllegalArgumentException(
                    "올바르지 않은 모임 ID입니다."
            );
        }

        if (memberId == null || memberId <= 0) {

            throw new IllegalArgumentException(
                    "올바르지 않은 회원 ID입니다."
            );
        }

        // SELECT ... FOR UPDATE
        Map<String, Object> group =
                mapper.findGroupForIssue(groupId);

        if (group == null) {

            throw new IllegalStateException(
                    "존재하지 않는 모임입니다."
            );
        }

        // 모임장 권한 확인
        Number ownerId =
                (Number) group.get("OWNER_ID");

        if (ownerId == null
                || ownerId.longValue() != memberId.longValue()) {

            throw new SecurityException(
                    "모임장만 인증코드를 발급할 수 있습니다."
            );
        }

        // 비공개 모임 여부 확인
        String visibility =
                String.valueOf(group.get("VISIBILITY"));

        if (!"PRIVATE".equalsIgnoreCase(visibility)) {

            throw new IllegalStateException(
                    "비공개 모임에서만 인증코드를 발급할 수 있습니다."
            );
        }

        return group;
    }

    /**
     * 현재 인증코드 버전 확인
     */
    private int getCodeVersion(
            Map<String, Object> group
    ) {

        Number version =
                (Number) group.get("JOIN_CODE_VERSION");

        if (version == null) {

            throw new IllegalStateException(
                    "인증코드 버전 정보가 없습니다."
            );
        }

        return version.intValue();
    }

    /**
     * F18 - 인증코드 발급 및 재발급 감사 로그
     *
     * 인증코드 원문, 암호문, 암호화 키는 기록하지 않는다.
     */
    private void saveIssueAuditLog(
            Long groupId,
            Long memberId,
            boolean firstIssue,
            int previousVersion,
            int newVersion
    ) {

        String action = firstIssue
                ? "최초 발급"
                : "재발급";

        String description =
                "비공개 모임 인증코드 " + action
                        + " (버전: "
                        + previousVersion
                        + " -> "
                        + newVersion
                        + ")";

        int inserted = auditMapper.insertAuditLog(
                groupId,
                memberId,
                null,
                "JOIN_CODE_REISSUE",
                description
        );

        if (inserted != 1) {

            throw new IllegalStateException(
                    "인증코드 발급 감사 로그 저장에 실패했습니다."
            );
        }
    }

    /**
     * F18 - 비공개 모임 인증코드 발급 및 재발급
     *
     * 1. 모임장 권한 확인
     * 2. 기존 인증코드 조회 및 복호화
     * 3. 기존 코드와 다른 6자리 코드 생성
     * 4. AES-256-GCM 암호화
     * 5. 인증코드 저장 및 버전 증가
     * 6. 감사 로그 저장
     *
     * 회원별 인증 시도 기록은 다음 인증 상태 조회 또는
     * 인증 시도 시 새로운 버전에 맞춰 초기화된다.
     */
    @Transactional
    public GroupJoinCodeIssueResponse prepareJoinCodeIssue(
            Long groupId,
            Long memberId
    ) {

        // 1. 모임장 권한 및 비공개 모임 확인
        Map<String, Object> group =
                findAuthorizedGroup(groupId, memberId);

        int currentVersion =
                getCodeVersion(group);

        // DDL: JOIN_CODE_VERSION NUMBER(3)
        if (currentVersion >= 999) {

            throw new IllegalStateException(
                    "인증코드 재발급 가능 횟수를 초과했습니다."
            );
        }

        // 2. 기존 인증코드 조회
        String previousCode = null;

        Object previousEncryptedCode =
                group.get("JOIN_CODE_ENC");

        // 기존 인증코드 존재 여부로 최초 발급과 재발급 구분
        boolean firstIssue =
                previousEncryptedCode == null;

        if (previousEncryptedCode != null) {

            Number previousKeyVersion =
                    (Number) group.get("JOIN_CODE_KEY_VER");

            if (previousKeyVersion == null) {

                throw new IllegalStateException(
                        "기존 인증코드 암호화 키 버전이 없습니다."
                );
            }

            previousCode = cryptoKeyService.decrypt(
                    String.valueOf(previousEncryptedCode),
                    previousKeyVersion.intValue()
            );

            if (previousCode == null
                    || !previousCode.matches("[0-9]{6}")) {

                throw new IllegalStateException(
                        "기존 인증코드 정보가 올바르지 않습니다."
                );
            }
        }

        // 3. 기존 코드와 다른 6자리 인증코드 생성
        String joinCode = null;

        for (int i = 0; i < 10; i++) {

            String candidate =
                    GroupJoinCodeGenerator.generate();

            if (candidate != null
                    && candidate.matches("[0-9]{6}")
                    && !candidate.equals(previousCode)) {

                joinCode = candidate;
                break;
            }
        }

        if (joinCode == null) {

            throw new IllegalStateException(
                    "새로운 인증코드 생성에 실패했습니다."
            );
        }

        // 4. 현재 암호화 키 버전 조회
        int keyVersion =
                cryptoKeyService.getCurrentKeyVersion();

        // 5. AES-256-GCM 암호화
        String encryptedCode =
                cryptoKeyService.encrypt(
                        joinCode,
                        keyVersion
                );

        // 6. 암호화된 인증코드 저장 및 버전 증가
        int updatedRows = mapper.updateJoinCode(
                groupId,
                memberId,
                encryptedCode,
                keyVersion
        );

        if (updatedRows != 1) {

            throw new IllegalStateException(
                    "인증코드 저장에 실패했습니다."
            );
        }

        // 7. 인증코드 발급 및 재발급 감사 로그 저장
        int newVersion = currentVersion + 1;

        saveIssueAuditLog(
                groupId,
                memberId,
                firstIssue,
                currentVersion,
                newVersion
        );

        // 8. 모임장에게 발급 결과 반환
        return new GroupJoinCodeIssueResponse(
                groupId,
                joinCode,
                newVersion,
                null,
                "인증코드가 발급되었습니다."
        );
    }
}
