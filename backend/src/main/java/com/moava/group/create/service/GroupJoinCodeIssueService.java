
package com.moava.group.create.service;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.create.mapper.GroupJoinCodeIssueMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.moava.group.create.dto.GroupJoinCodeIssueResponse;

import java.util.Map;

@Service
public class GroupJoinCodeIssueService {

        private final GroupJoinCodeIssueMapper mapper;
        private final CryptoKeyService cryptoKeyService;

        public GroupJoinCodeIssueService(
                GroupJoinCodeIssueMapper mapper,
                CryptoKeyService cryptoKeyService
        ) {
        this.mapper = mapper;
        this.cryptoKeyService = cryptoKeyService;
        }
    /**
     * 인증코드 발급 전 모임장 권한 및 모임 상태 확인
     *
     * 실제 발급은 보안팀 암호화 기능 연결 후 구현
     */
    @Transactional
    public int validateIssuePermission(
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

        // 모임장 확인
        Number ownerId =
                (Number) group.get("OWNER_ID");

        if (ownerId == null ||
                ownerId.longValue() != memberId.longValue()) {

            throw new SecurityException(
                    "모임장만 인증코드를 발급할 수 있습니다."
            );
        }

        // 비공개 모임 확인
        String visibility =
                String.valueOf(group.get("VISIBILITY"));

        if (!"PRIVATE".equalsIgnoreCase(visibility)) {
            throw new IllegalStateException(
                    "비공개 모임에서만 인증코드를 발급할 수 있습니다."
            );
        }

        // 현재 인증코드 버전 반환
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
     * 인증코드 발급 준비
     *
     * 모임장 권한 확인 후 6자리 코드를 생성한다.
     * 암호화 및 DB 저장은 보안팀 키 관리 기능
     * 연동 후 하나의 트랜잭션에서 처리한다.
     *
     * 현재는 실제 발급 API에서 호출하지 않는다.
     */

        @Transactional
        public GroupJoinCodeIssueResponse prepareJoinCodeIssue(
                Long groupId,
                Long memberId
        ) {
        // 1. 모임장 권한 및 PRIVATE 여부 확인
        int currentVersion =
                validateIssuePermission(groupId, memberId);

        // DDL: JOIN_CODE_VERSION NUMBER(3)
        if (currentVersion >= 999) {
                throw new IllegalStateException(
                        "인증코드 재발급 가능 횟수를 초과했습니다."
                );
        }

        // 2. 6자리 인증코드 생성
        String joinCode = GroupJoinCodeGenerator.generate();

        if (!joinCode.matches("[0-9]{6}")) {
                throw new IllegalStateException(
                        "인증코드 생성에 실패했습니다."
                );
        }

        // 3. 보안팀 공통 암호화 키 버전 조회
        int keyVersion =
                cryptoKeyService.getCurrentKeyVersion();

        // 4. AES-256-GCM 암호화
        String encryptedCode =
                cryptoKeyService.encrypt(joinCode, keyVersion);

        // 5. 암호문 및 키 버전 저장
        // JOIN_CODE_VERSION은 Mapper SQL에서 +1
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

        // 6. 모임장에게 발급 결과 반환
        return new GroupJoinCodeIssueResponse(
                groupId,
                joinCode,
                currentVersion + 1,
                null,
                "인증코드가 발급되었습니다."
        );
        }


}
