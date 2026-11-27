package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountResponse;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.dto.GroupAccountVerifyResponse;
import com.moava.group.account.mapper.GroupAccountMapper;
import com.moava.group.create.service.GroupAccountCrypto;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroupAccountService {

    private final GroupAccountMapper mapper;

    private final GroupAccountCrypto crypto;

    private final ObjectProvider<AccountVerificationProvider>
            verificationProvider;

    private final TransactionTemplate transactionTemplate;

    private final NotificationService notificationService;

    public GroupAccountService(
            GroupAccountMapper mapper,
            GroupAccountCrypto crypto,
            ObjectProvider<AccountVerificationProvider>
                    verificationProvider,
            PlatformTransactionManager transactionManager,
            NotificationService notificationService
    ) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.verificationProvider = verificationProvider;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.notificationService = notificationService;
    }

    // F19 - 모임 ID 검증
    private void validateGroupId(Long groupId) {

        if (groupId == null || groupId <= 0) {

            throw new IllegalArgumentException(
                    "유효하지 않은 모임 ID입니다."
            );
        }
    }

    // F19 - 로그인 회원 및 활성 상태 검증
    private void validateActiveMember(Long memberId) {

        if (memberId == null || memberId <= 0) {

            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        int activeMemberCount =
                mapper.countActiveMember(memberId);

        if (activeMemberCount != 1) {

            throw new SecurityException(
                    "이용 가능한 회원 계정이 아닙니다."
            );
        }
    }

    // F19 - 모임 존재 여부 확인
    private void validateGroupExists(Long groupId) {

        int groupCount = mapper.countGroup(groupId);

        if (groupCount != 1) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 모임입니다."
            );
        }
    }

    // F19 - 현재 모임장 권한 확인
    private void validateGroupOwner(
            Long groupId,
            Long memberId
    ) {

        int ownerCount = mapper.countGroupOwner(
                groupId,
                memberId
        );

        if (ownerCount != 1) {

            throw new SecurityException(
                    "모임장만 계좌 관리 기능을 이용할 수 있습니다."
            );
        }
    }

    // F19 - 현재 모임 계좌 조회
    @Transactional(readOnly = true)
    public GroupAccountResponse getCurrentAccount(
            Long groupId,
            Long authenticatedMemberId
    ) {

        // 1. 모임 ID 검증
        validateGroupId(groupId);

        // 2. 로그인 및 활성 회원 검증
        validateActiveMember(authenticatedMemberId);

        // 3. 모임 존재 여부 확인
        validateGroupExists(groupId);

        // 4. 현재 모임장 권한 확인
        validateGroupOwner(
                groupId,
                authenticatedMemberId
        );

        // 5. 현재 사용 중인 모임 계좌 조회
        GroupAccountResponse account =
                mapper.findCurrentAccount(
                        groupId,
                        authenticatedMemberId
                );

        if (account == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "등록된 현재 모임 계좌가 없습니다."
            );
        }

        // 6. 마스킹된 계좌 정보 반환
        return account;
    }

    // F19 - 모임 계좌 소유 인증
    // 외부 API 호출 동안 DB 행 잠금을 유지하지 않는다.
    public GroupAccountVerifyResponse verifyCurrentAccount(
            Long groupId,
            Long authenticatedMemberId
    ) {

        // 1. 모임 및 회원 권한 검증
        validateGroupId(groupId);
        validateActiveMember(authenticatedMemberId);
        validateGroupExists(groupId);

        validateGroupOwner(
                groupId,
                authenticatedMemberId
        );

        // 2. 서버에 등록된 현재 계좌 조회
        GroupAccountVerificationTarget target =
                mapper.findVerificationTarget(
                        groupId,
                        authenticatedMemberId
                );

        if (target == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "인증할 모임 계좌가 없습니다."
            );
        }

        // 3. 이미 인증된 계좌는 중복 인증하지 않는다.
        if ("VERIFIED".equals(target.verifyStatus())) {

            return buildVerifyResponse(
                    requireSameCurrentAccount(
                            target,
                            authenticatedMemberId
                    ),
                    "이미 인증이 완료된 계좌입니다."
            );
        }

        // 4. 인증 가능한 상태 확인
        if (!"PENDING".equals(target.verifyStatus())
                && !"REVERIFY_REQUIRED".equals(
                        target.verifyStatus()
                )) {

            throw new IllegalStateException(
                    "현재 계좌 상태에서는 인증할 수 없습니다."
            );
        }

        // 5. 실제 계좌 인증 제공자 확인
        AccountVerificationProvider provider =
                verificationProvider.getIfAvailable();

        if (provider == null) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "계좌 소유 인증 서비스가 아직 연결되지 않았습니다."
            );
        }

        // 6. 암호화 키 버전 확인 (키 로테이션 후 이전 RETIRED 버전도 복호화 허용)
        if (target.keyVer() == null || target.keyVer() < 1) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "계좌 암호화 키를 확인할 수 없습니다."
            );
        }

        // 7. 등록된 계좌번호 복호화
        String accountNumber =
                decryptAccountNumber(target);

        // 8. CODEF 계좌 실존 및 소유 확인
        boolean verified;

        try {

            verified = provider.verifyAccount(
                    authenticatedMemberId,
                    target.bankName(),
                    accountNumber
            );

        } catch (RuntimeException e) {

            // 외부 API 예외 및 민감정보를 응답에 노출하지 않는다.
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "계좌 소유 인증 서비스 요청에 실패했습니다."
            );
        }

        if (!verified) {

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "계좌 소유 확인에 실패했습니다."
            );
        }

        // 9. 인증 성공 결과를 별도 트랜잭션에서 반영
        return transactionTemplate.execute(status ->
                completeVerification(
                        target,
                        authenticatedMemberId
                )
        );
    }

    // F19 - 등록 계좌번호 복호화 및 형식 검증
    private String decryptAccountNumber(
            GroupAccountVerificationTarget target
    ) {

        String accountNumber;

        try {

            accountNumber =
                    crypto.decrypt(target.accountNoEnc(), target.keyVer());

        } catch (RuntimeException e) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "등록된 계좌 정보를 확인할 수 없습니다."
            );
        }

        if (accountNumber == null
                || !accountNumber.matches("[0-9]{8,20}")
                || target.accountNoLast4() == null
                || !target.accountNoLast4().matches("[0-9]{4}")
                || !accountNumber.endsWith(
                        target.accountNoLast4()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "등록된 계좌 정보가 올바르지 않습니다."
            );
        }

        return accountNumber;
    }

    // F19 - 인증 결과 반영
    // TransactionTemplate 내부에서 실행된다.
    // 인증 상태, 감사 로그, 알림을 하나의 트랜잭션으로 처리한다.
    private GroupAccountVerifyResponse completeVerification(
            GroupAccountVerificationTarget original,
            Long authenticatedMemberId
    ) {

        Long groupId = original.groupId();

        // 1. 모임 행 잠금
        Long lockedGroupId =
                mapper.lockGroup(groupId);

        if (lockedGroupId == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 모임입니다."
            );
        }

        // 2. 인증 진행 중 회원 상태 및 권한 변경 여부 확인
        validateActiveMember(authenticatedMemberId);

        validateGroupOwner(
                groupId,
                authenticatedMemberId
        );

        // 3. 인증 요청 당시 계좌와 현재 계좌 비교
        GroupAccountVerificationTarget current =
                mapper.findVerificationTarget(
                        groupId,
                        authenticatedMemberId
                );

        if (!isSameAccount(original, current)) {

            throw new IllegalStateException(
                    "인증 중 계좌 정보가 변경되었습니다. 다시 시도해 주세요."
            );
        }

        // 4. 다른 요청에서 이미 인증을 완료한 경우
        // 감사 로그 및 알림을 중복 생성하지 않는다.
        if ("VERIFIED".equals(current.verifyStatus())) {

            return buildVerifyResponse(
                    requireSameCurrentAccount(
                            original,
                            authenticatedMemberId
                    ),
                    "이미 인증이 완료된 계좌입니다."
            );
        }

        // 5. 계좌 인증 상태 변경
        int updated = mapper.updateAccountVerified(
                original.gyeAccountId(),
                groupId,
                authenticatedMemberId,
                original.accountNoEnc(),
                original.bankName(),
                original.keyVer()
        );

        if (updated != 1) {

            throw new IllegalStateException(
                    "계좌 인증 상태를 변경할 수 없습니다."
            );
        }

        // 6. 감사 로그 저장
        int auditInserted =
                mapper.insertAccountVerifyAudit(
                        groupId,
                        authenticatedMemberId,
                        "CODEF 모임 계좌 소유 인증 완료"
                );

        if (auditInserted != 1) {

            throw new IllegalStateException(
                    "계좌 인증 감사 로그 저장에 실패했습니다."
            );
        }

        // 7. 인증 완료된 현재 계좌 조회
        GroupAccountResponse account =
                requireSameCurrentAccount(
                        original,
                        authenticatedMemberId
                );

        if (!"VERIFIED".equals(account.verifyStatus())) {

            throw new IllegalStateException(
                    "계좌 인증 완료 상태를 확인할 수 없습니다."
            );
        }

        // 8. 인증 완료 알림에 사용할 모임명 조회
        String groupTitle =
                mapper.findGroupTitle(groupId);

        if (groupTitle == null || groupTitle.isBlank()) {

            throw new IllegalStateException(
                    "계좌 인증 완료 알림에 필요한 모임 정보를 확인할 수 없습니다."
            );
        }

        // 9. F19 - 모임장에게 계좌 소유 인증 완료 알림
        // 실제 계좌번호, 예금주명, 암호문, 해시값 전달 금지
        // 인증 상태 및 감사 로그와 동일한 트랜잭션으로 처리
        notificationService.notifyMember(
                authenticatedMemberId,
                NotifType.ACCOUNT_VERIFIED,
                groupId,
                Map.of(
                        "group", groupTitle,
                        "bank", account.bankName(),
                        "last4", account.accountLast4(),
                        "groupId", groupId
                )
        );

        // 10. 마스킹된 인증 결과 반환
        return buildVerifyResponse(
                account,
                "계좌 소유 인증이 완료되었습니다."
        );
    }

    // F19 - 인증 요청 당시 계좌와 현재 계좌 비교
    private boolean isSameAccount(
            GroupAccountVerificationTarget original,
            GroupAccountVerificationTarget current
    ) {

        return current != null

                && Objects.equals(
                        original.gyeAccountId(),
                        current.gyeAccountId()
                )

                && Objects.equals(
                        original.groupId(),
                        current.groupId()
                )

                && Objects.equals(
                        original.ownerId(),
                        current.ownerId()
                )

                && Objects.equals(
                        original.bankName(),
                        current.bankName()
                )

                && Objects.equals(
                        original.accountNoEnc(),
                        current.accountNoEnc()
                )

                && Objects.equals(
                        original.accountNoLast4(),
                        current.accountNoLast4()
                )

                && Objects.equals(
                        original.keyVer(),
                        current.keyVer()
                )

                && "CURRENT".equals(
                        current.accountStatus()
                );
    }

    // F19 - 응답 생성 전 동일 계좌 여부 확인
    private GroupAccountResponse requireSameCurrentAccount(
            GroupAccountVerificationTarget target,
            Long memberId
    ) {

        GroupAccountResponse account =
                mapper.findCurrentAccount(
                        target.groupId(),
                        memberId
                );

        if (account == null
                || !Objects.equals(
                        target.gyeAccountId(),
                        account.gyeAccountId()
                )) {

            throw new IllegalStateException(
                    "현재 모임 계좌가 변경되었습니다."
            );
        }

        return account;
    }

    // F19 - 인증 결과 응답 생성
    private GroupAccountVerifyResponse buildVerifyResponse(
            GroupAccountResponse account,
            String message
    ) {

        return new GroupAccountVerifyResponse(
                account.gyeAccountId(),
                account.groupId(),
                account.bankName(),
                account.accountMasked(),
                account.verifyStatus(),
                account.verifiedAt(),
                message
        );
    }
}
