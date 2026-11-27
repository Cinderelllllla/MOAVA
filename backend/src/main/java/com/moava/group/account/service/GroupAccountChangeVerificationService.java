package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountChangeVerifyResponse;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.create.service.GroupAccountCrypto;

import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroupAccountChangeVerificationService {

    private final GroupAccountChangeMapper mapper;
    private final GroupAccountCrypto crypto;
    private final ObjectProvider<AccountVerificationProvider> providers;
    private final TransactionTemplate transactions;

    public GroupAccountChangeVerificationService(
            GroupAccountChangeMapper mapper,
            GroupAccountCrypto crypto,
            ObjectProvider<AccountVerificationProvider> providers,
            PlatformTransactionManager transactionManager
    ) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.providers = providers;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public GroupAccountChangeVerifyResponse verify(
            Long groupId, Long accountId, Long memberId
    ) {
        if (groupId == null || groupId <= 0
                || accountId == null || accountId <= 0) {
            throw new IllegalArgumentException(
                    "유효하지 않은 계좌 변경 신청입니다."
            );
        }

        if (memberId == null || memberId <= 0) {
            throw new SecurityException("로그인이 필요합니다.");
        }

        // 기존 F20 권한 검증과 같은 활성 모임장 기준
        if (mapper.findActiveOwnerName(groupId, memberId) == null) {
            throw new SecurityException(
                    "모임장만 계좌 소유 인증을 진행할 수 있습니다."
            );
        }

        GroupAccountVerificationTarget original =
                mapper.findPendingVerificationTarget(groupId, accountId);

        if (original == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "인증할 변경 신청 계좌가 없습니다."
            );
        }

        if (!Objects.equals(original.ownerId(), memberId)) {
            throw new SecurityException("모임장 정보가 일치하지 않습니다.");
        }

        // 이전에 인증됐다면 추가 외부 호출 없이 상태 재검증
        if ("VERIFIED".equals(original.verifyStatus())) {
            return transactions.execute(status ->
                    finish(original, memberId)
            );
        }

        if (!"PENDING".equals(original.verifyStatus())
                && !"REVERIFY_REQUIRED".equals(original.verifyStatus())) {
            throw new IllegalStateException(
                    "인증할 수 없는 계좌 상태입니다."
            );
        }

        AccountVerificationProvider provider =
                providers.getIfAvailable();

        if (provider == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "계좌 소유 인증 서비스가 연결되지 않았습니다."
            );
        }

        if (original.keyVer() == null
                || original.keyVer() != crypto.keyVersion()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "계좌 암호화 키 버전이 일치하지 않습니다."
            );
        }

        String accountNumber;
        try {
            accountNumber = crypto.decrypt(original.accountNoEnc());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "등록된 계좌 정보를 확인할 수 없습니다."
            );
        }

        if (accountNumber == null
                || !accountNumber.matches("[0-9]{8,30}")
                || original.accountNoLast4() == null
                || !original.accountNoLast4().matches("[0-9]{4}")
                || !accountNumber.endsWith(original.accountNoLast4())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "등록된 계좌 정보가 올바르지 않습니다."
            );
        }

        boolean verified;
        try {
            // CODEF 연결 시 실제 소유 확인
            // 외부 API 호출 중 DB 행 잠금을 유지하지 않음
            verified = provider.verifyAccount(
                    memberId, original.bankName(), accountNumber
            );
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "계좌 소유 인증 요청에 실패했습니다."
            );
        }

        if (!verified) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "계좌 소유 확인에 실패했습니다."
            );
        }

        return transactions.execute(status ->
                finish(original, memberId)
        );
    }

    private GroupAccountChangeVerifyResponse finish(
            GroupAccountVerificationTarget original,
            Long memberId
    ) {
        Long groupId = original.groupId();
        Long accountId = original.gyeAccountId();

        if (mapper.lockGroup(groupId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "모임을 찾을 수 없습니다."
            );
        }

        if (mapper.findActiveOwnerName(groupId, memberId) == null) {
            throw new SecurityException(
                    "모임장 권한이 변경되었습니다."
            );
        }

        GroupAccountVerificationTarget current =
                mapper.findPendingVerificationTarget(groupId, accountId);

        if (current == null
                || !Objects.equals(original.ownerId(), current.ownerId())
                || !Objects.equals(original.gyeAccountId(), current.gyeAccountId())
                || !Objects.equals(original.bankName(), current.bankName())
                || !Objects.equals(original.accountNoEnc(), current.accountNoEnc())
                || !Objects.equals(original.accountNoLast4(), current.accountNoLast4())
                || !Objects.equals(original.keyVer(), current.keyVer())
                || !"PENDING".equals(current.accountStatus())) {
            throw new IllegalStateException(
                    "인증 중 계좌 변경 신청 상태가 달라졌습니다."
            );
        }

        if ("VERIFIED".equals(current.verifyStatus())) {
            return response(current, "이미 인증이 완료된 계좌입니다.");
        }

        if ("VERIFIED".equals(original.verifyStatus())) {
            throw new IllegalStateException(
                    "인증 상태가 변경되었습니다. 다시 확인해 주세요."
            );
        }

        int updated = mapper.updatePendingAccountVerified(
                groupId, accountId,
                original.accountNoEnc(),
                original.bankName(),
                original.keyVer()
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "새 계좌 인증 결과 저장에 실패했습니다."
            );
        }

        int audited = mapper.insertChangeAudit(
                groupId, memberId, "변경 신청 계좌 소유 인증 완료"
        );

        if (audited != 1) {
            throw new IllegalStateException(
                    "계좌 인증 감사 로그 저장에 실패했습니다."
            );
        }

        GroupAccountVerificationTarget saved =
                mapper.findPendingVerificationTarget(groupId, accountId);

        if (saved == null || !"VERIFIED".equals(saved.verifyStatus())) {
            throw new IllegalStateException(
                    "계좌 인증 상태 확인에 실패했습니다."
            );
        }

        return response(saved, "새 계좌 소유 인증이 완료되었습니다.");
    }

    private GroupAccountChangeVerifyResponse response(
            GroupAccountVerificationTarget account, String message
    ) {
        return new GroupAccountChangeVerifyResponse(
                account.groupId(),
                account.gyeAccountId(),
                account.bankName(),
                "*****" + account.accountNoLast4(),
                account.verifyStatus(),
                account.accountStatus(),
                message
        );
    }
}
