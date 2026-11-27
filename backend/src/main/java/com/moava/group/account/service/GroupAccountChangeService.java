package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountChangeCloseResponse;
import com.moava.group.account.dto.GroupAccountChangeReadinessResponse;
import com.moava.group.account.dto.GroupAccountChangeRequest;
import com.moava.group.account.dto.GroupAccountChangeResponse;
import com.moava.group.account.dto.GroupAccountConsentRequest;
import com.moava.group.account.dto.GroupAccountConsentResponse;
import com.moava.group.account.dto.GroupAccountConsentStatusResponse;
import com.moava.group.account.dto.GroupAccountConsentTargetInfo;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.create.service.GroupAccountCrypto;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroupAccountChangeService {

    private static final String ACCOUNT_UNIQUE_INDEX =
            "UX_GYE_ACCOUNT_ACTIVE_HASH";

    private final GroupAccountChangeMapper mapper;
    private final GroupAccountCrypto crypto;

    public GroupAccountChangeService(
            GroupAccountChangeMapper mapper,
            GroupAccountCrypto crypto
    ) {
        this.mapper = mapper;
        this.crypto = crypto;
    }

    // =====================================================
    // F20 - 계좌 변경 신청
    // =====================================================

    @Transactional
    public GroupAccountChangeResponse requestAccountChange(
            Long groupId,
            Long memberId,
            GroupAccountChangeRequest request
    ) {
        // 1. 입력값 검증
        validateRequest(groupId, memberId, request);

        // 2. 모임 행 잠금: 동시 변경 신청 직렬화
        Long lockedGroupId = mapper.lockGroup(groupId);

        if (lockedGroupId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 모임입니다."
            );
        }

        // 3. 활성 회원 및 모임장 권한 검증
        String ownerName =
                mapper.findActiveOwnerName(groupId, memberId);

        if (ownerName == null || ownerName.isBlank()) {
            throw new SecurityException(
                    "활성 상태의 모임장만 계좌를 변경할 수 있습니다."
            );
        }

        // 4. 기존 CURRENT 계좌 확인
        if (mapper.countCurrentAccount(groupId) != 1) {
            throw new IllegalStateException(
                    "현재 모임 계좌 상태를 확인할 수 없습니다."
            );
        }

        // 5. 진행 중인 변경 신청 확인
        if (mapper.countPendingChange(groupId) > 0) {
            throw new IllegalStateException(
                    "이미 진행 중인 계좌 변경 신청이 있습니다."
            );
        }

        String bankName = request.bankName().strip();
        String accountNumber = request.accountNumber();

        // 6. F19와 동일한 계좌 해시 방식 재사용
        String accountHash =
                crypto.hashAccountNumber(accountNumber);

        if (mapper.countDuplicateAccount(accountHash) > 0) {
            throw new IllegalStateException(
                    "이미 등록된 계좌입니다."
            );
        }

        // 7. 예금주 및 계좌번호 암호화
        String holderNameEnc = crypto.encrypt(ownerName);
        String accountNoEnc = crypto.encrypt(accountNumber);

        validateEncryptedLength(holderNameEnc, accountNoEnc);

        String last4 = accountNumber.substring(
                accountNumber.length() - 4
        );

        // 8. 변경 신청 ID 생성
        Long accountId = mapper.nextAccountId();

        if (accountId == null || accountId <= 0) {
            throw new IllegalStateException(
                    "계좌 변경 신청 ID를 생성하지 못했습니다."
            );
        }

        // 9. PENDING 계좌 추가
        int inserted;

        try {
            inserted = mapper.insertPendingAccount(
                    accountId,
                    groupId,
                    bankName,
                    holderNameEnc,
                    accountNoEnc,
                    last4,
                    accountHash,
                    crypto.keyVersion(),
                    memberId
            );
        } catch (DuplicateKeyException e) {
            if (isAccountDuplicateViolation(e)) {
                throw new IllegalStateException(
                        "이미 등록된 계좌입니다."
                );
            }

            throw e;
        }

        if (inserted != 1) {
            throw new IllegalStateException(
                    "계좌 변경 신청 저장에 실패했습니다."
            );
        }

        // 10. 감사 로그 저장
        int auditInserted = mapper.insertChangeAudit(
                groupId,
                memberId,
                "모임 계좌 변경 신청 접수"
        );

        if (auditInserted != 1) {
            throw new IllegalStateException(
                    "계좌 변경 신청 감사 로그 저장에 실패했습니다."
            );
        }

        // 11. 민감정보 제외한 응답
        return new GroupAccountChangeResponse(
                accountId,
                groupId,
                bankName,
                "*****" + last4,
                "PENDING",
                "PENDING",
                "계좌 변경 신청이 접수되었습니다."
        );
    }

    // =====================================================
    // 계좌 변경 신청 입력값 검증
    // =====================================================

    private void validateRequest(
            Long groupId,
            Long memberId,
            GroupAccountChangeRequest request
    ) {
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException(
                    "유효하지 않은 모임 ID입니다."
            );
        }

        if (memberId == null || memberId <= 0) {
            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        if (request == null
                || request.bankName() == null
                || request.bankName().isBlank()
                || request.bankName().strip()
                    .getBytes(StandardCharsets.UTF_8).length > 50
                || request.accountNumber() == null
                || !request.accountNumber()
                    .matches("[0-9]{8,30}")) {

            throw new IllegalArgumentException(
                    "모임 계좌 정보를 확인해 주세요."
            );
        }
    }

    // =====================================================
    // 암호화 데이터 저장 길이 검증
    // =====================================================

    private void validateEncryptedLength(
            String holderNameEnc,
            String accountNoEnc
    ) {
        if (holderNameEnc == null
                || holderNameEnc.getBytes(
                        StandardCharsets.UTF_8
                ).length > 120) {

            throw new IllegalArgumentException(
                    "예금주 암호화 저장 길이 제한을 초과했습니다."
            );
        }

        if (accountNoEnc == null
                || accountNoEnc.getBytes(
                        StandardCharsets.UTF_8
                ).length > 100) {

            throw new IllegalArgumentException(
                    "계좌번호 암호화 저장 길이 제한을 초과했습니다."
            );
        }
    }

    // =====================================================
    // 계좌 중복 제약 위반 확인
    // =====================================================

    private boolean isAccountDuplicateViolation(
            DuplicateKeyException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                String message = sqlException.getMessage();

                if (sqlException.getErrorCode() == 1
                        && message != null
                        && message.toUpperCase(Locale.ROOT)
                            .contains(ACCOUNT_UNIQUE_INDEX)) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }

    // =====================================================
    // F20 - 계좌 변경 회원 동의 현황 조회
    // =====================================================

    @Transactional(readOnly = true)
    public GroupAccountConsentStatusResponse getConsentStatus(
            Long groupId,
            Long memberId
    ) {
        // 1. 모임 ID 검증
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException(
                    "유효하지 않은 모임 ID입니다."
            );
        }

        // 2. 로그인 회원 검증
        if (memberId == null || memberId <= 0) {
            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        // 3. 해당 모임의 ACTIVE 회원인지 확인
        if (mapper.countActiveGroupMember(
                groupId, memberId) != 1) {

            throw new SecurityException(
                    "해당 모임의 활성 회원만 조회할 수 있습니다."
            );
        }

        // 4. 현재 진행 중인 계좌 변경 신청 조회
        Long accountId = mapper.findPendingAccountId(groupId);

        if (accountId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "진행 중인 계좌 변경 신청이 없습니다."
            );
        }

        // 5. 신규 계좌 표시 정보와 본인 동의 상태 조회
        GroupAccountConsentTargetInfo targetInfo =
                mapper.findConsentTargetInfo(
                        groupId,
                        accountId,
                        memberId
                );

        if (targetInfo == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "유효한 계좌 변경 신청 정보를 찾을 수 없습니다."
            );
        }

        // 6. 기존 회원 동의 집계
        int total = mapper.countActiveGroupMembers(groupId);
        int agreed = mapper.countAgreedMembers(
                groupId,
                accountId
        );
        int rejected = mapper.countRejectedMembers(
                groupId,
                accountId
        );

        // 7. 동의 데이터 정합성 확인
        if (total <= 0
                || agreed < 0
                || rejected < 0
                || agreed + rejected > total) {

            throw new IllegalStateException(
                    "회원 동의 현황을 확인할 수 없습니다."
            );
        }

        int pending = total - agreed - rejected;
        boolean allAgreed =
                pending == 0 && rejected == 0;

        // 8. 현재 로그인 회원의 동의 상태
        String myAgreedYn = targetInfo.myAgreedYn();

        // ACTIVE 회원 및 유효한 PENDING 신청 검증 후
        // 아직 동의·거절하지 않은 회원만 응답 가능
        boolean canRespond = myAgreedYn == null;

        // 9. 기존 집계 + 신규 정보 반환
        return new GroupAccountConsentStatusResponse(
                groupId,
                accountId,
                total,
                agreed,
                rejected,
                pending,
                allAgreed,
                myAgreedYn,
                canRespond,
                targetInfo.bankName(),
                targetInfo.accountMasked()
        );
    }

    // =====================================================
    // F20 - 회원 계좌 변경 동의·거절
    // =====================================================

    @Transactional
    public GroupAccountConsentResponse respondToConsent(
            Long groupId,
            Long accountId,
            Long memberId,
            GroupAccountConsentRequest request
    ) {
        // 1. 입력값 검증
        if (groupId == null || groupId <= 0
                || accountId == null || accountId <= 0) {

            throw new IllegalArgumentException(
                    "유효하지 않은 계좌 변경 신청입니다."
            );
        }

        if (memberId == null || memberId <= 0) {
            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        if (request == null || request.agreed() == null) {
            throw new IllegalArgumentException(
                    "동의 또는 거절 여부를 선택해 주세요."
            );
        }

        // 2. 모임 행 잠금
        Long lockedGroupId = mapper.lockGroup(groupId);

        if (lockedGroupId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 모임입니다."
            );
        }

        // 3. 해당 모임의 활성 회원 검증
        if (mapper.countActiveGroupMember(
                groupId, memberId) != 1) {

            throw new SecurityException(
                    "해당 모임의 활성 회원만 동의할 수 있습니다."
            );
        }

        // 4. 현재 진행 중인 변경 신청 검증
        Long pendingAccountId =
                mapper.findPendingAccountId(groupId);

        if (pendingAccountId == null
                || !pendingAccountId.equals(accountId)) {

            throw new IllegalStateException(
                    "진행 중인 계좌 변경 신청이 아닙니다."
            );
        }

        // 5. 회원 중복 응답 방지
        if (mapper.countExistingAccountConsent(
                groupId,
                accountId,
                memberId
        ) > 0) {

            throw new IllegalStateException(
                    "이미 동의 또는 거절한 신청입니다."
            );
        }

        String agreedYn =
                request.agreed() ? "Y" : "N";

        // 6. CONSENT 저장
        int inserted;

        try {
            inserted = mapper.insertAccountConsent(
                    groupId,
                    accountId,
                    memberId,
                    agreedYn
            );
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException(
                    "이미 동의 또는 거절한 신청입니다."
            );
        }

        if (inserted != 1) {
            throw new IllegalStateException(
                    "계좌 변경 동의 기록 저장에 실패했습니다."
            );
        }

        // 회원이 거절하면 변경 신청을 종료한다.
        // 기존 CURRENT 계좌는 유지한다.
        if (!request.agreed()) {
            int closed = mapper.retirePendingAccount(
                    groupId,
                    accountId
            );

            if (closed != 1) {
                throw new IllegalStateException(
                        "거절된 계좌 변경 신청 종료에 실패했습니다."
                );
            }
        }

        // 7. 감사 로그 저장
        String description =
                request.agreed()
                        ? "모임 계좌 변경 동의"
                        : "모임 계좌 변경 거절 및 신청 종료";

        int auditInserted =
                mapper.insertConsentAudit(
                        groupId,
                        memberId,
                        description
                );

        if (auditInserted != 1) {
            throw new IllegalStateException(
                    "계좌 변경 동의 감사 로그 저장에 실패했습니다."
            );
        }

        // 8. 결과 반환
        return new GroupAccountConsentResponse(
                groupId,
                accountId,
                memberId,
                agreedYn,
                request.agreed()
                        ? "계좌 변경에 동의했습니다."
                        : "계좌 변경을 거절했습니다."
        );
    }

    // =====================================================
    // F20 - 모임장의 계좌 변경 신청 취소
    // =====================================================

    @Transactional
    public GroupAccountChangeCloseResponse cancelChange(
            Long groupId,
            Long accountId,
            Long memberId
    ) {
        // 1. 입력값 확인
        if (groupId == null || groupId <= 0
                || accountId == null || accountId <= 0) {

            throw new IllegalArgumentException(
                    "유효하지 않은 계좌 변경 신청입니다."
            );
        }

        if (memberId == null || memberId <= 0) {
            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        // 2. 모임 행 잠금
        Long lockedGroupId = mapper.lockGroup(groupId);

        if (lockedGroupId == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 모임입니다."
            );
        }

        // 3. 현재 활성 모임장 권한 확인
        String ownerName =
                mapper.findActiveOwnerName(groupId, memberId);

        if (ownerName == null || ownerName.isBlank()) {
            throw new SecurityException(
                    "모임장만 계좌 변경 신청을 취소할 수 있습니다."
            );
        }

        // 4. 진행 중 신청 ID 검증
        Long pendingAccountId =
                mapper.findPendingAccountId(groupId);

        if (pendingAccountId == null
                || !pendingAccountId.equals(accountId)) {

            throw new IllegalStateException(
                    "취소할 수 있는 계좌 변경 신청이 없습니다."
            );
        }

        // 5. 신청 종료
        int updated = mapper.retirePendingAccount(
                groupId,
                accountId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "계좌 변경 신청 취소에 실패했습니다."
            );
        }

        // 6. 감사 로그
        int auditInserted = mapper.insertChangeAudit(
                groupId,
                memberId,
                "모임장 계좌 변경 신청 취소"
        );

        if (auditInserted != 1) {
            throw new IllegalStateException(
                    "계좌 변경 신청 감사 로그 저장에 실패했습니다."
            );
        }

        // 7. 업무상 취소 결과 반환
        return new GroupAccountChangeCloseResponse(
                groupId,
                accountId,
                "CANCELLED",
                "계좌 변경 신청이 취소되었습니다."
        );
    }

    // =====================================================
    // F20 - 최종 계좌 변경 가능 여부 사전 검증
    // 읽기 전용: 실제 잔액 이전이나 계좌 교체 없음
    // =====================================================

    @Transactional(readOnly = true)
    public GroupAccountChangeReadinessResponse checkReadiness(
            Long groupId,
            Long accountId,
            Long memberId
    ) {
        if (groupId == null || groupId <= 0
                || accountId == null || accountId <= 0) {

            throw new IllegalArgumentException(
                    "유효하지 않은 계좌 변경 신청입니다."
            );
        }

        if (memberId == null || memberId <= 0) {
            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        // 활성 모임장만 준비 상태 조회 가능
        String ownerName =
                mapper.findActiveOwnerName(groupId, memberId);

        if (ownerName == null || ownerName.isBlank()) {
            throw new SecurityException(
                    "모임장만 변경 확정 조건을 확인할 수 있습니다."
            );
        }

        GroupAccountVerificationTarget target =
                mapper.findPendingVerificationTarget(
                        groupId,
                        accountId
                );

        if (target == null
                || !memberId.equals(target.ownerId())
                || !groupId.equals(target.groupId())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "진행 중인 변경 신청을 찾을 수 없습니다."
            );
        }

        Long pendingId =
                mapper.findPendingAccountId(groupId);

        if (!accountId.equals(pendingId)) {
            throw new IllegalStateException(
                    "진행 중인 변경 신청이 일치하지 않습니다."
            );
        }

        int total = mapper.countActiveGroupMembers(groupId);
        int agreed = mapper.countAgreedMembers(
                groupId,
                accountId
        );
        int rejected = mapper.countRejectedMembers(
                groupId,
                accountId
        );

        if (total <= 0
                || agreed < 0
                || rejected < 0
                || agreed + rejected > total) {

            throw new IllegalStateException(
                    "회원 동의 현황을 확인할 수 없습니다."
            );
        }

        int pending = total - agreed - rejected;

        boolean consentReady =
                agreed == total && rejected == 0;

        boolean ownershipVerified =
                "VERIFIED".equals(target.verifyStatus());

        List<String> blockers = new ArrayList<>();

        if (!consentReady) {
            blockers.add("현재 활성 회원 전원 동의 미완료");
        }

        if (rejected > 0) {
            blockers.add("거절 기록이 존재합니다.");
        }

        if (!ownershipVerified) {
            blockers.add("신규 계좌 소유 인증 미완료");
        }

        // 모의 은행 잔액 이전 검증은 아직 연결되지 않음.
        boolean mockTransferReady = false;

        blockers.add("모의 은행 잔액 이전 검증 미구현");

        // 실제 이체 및 원장 검증 전에는 반드시 false
        boolean finalizeReady =
                consentReady
                && ownershipVerified
                && mockTransferReady;

        return new GroupAccountChangeReadinessResponse(
                groupId,
                accountId,
                total,
                agreed,
                rejected,
                pending,
                consentReady,
                ownershipVerified,
                mockTransferReady,
                finalizeReady,
                List.copyOf(blockers)
        );
    }
}
