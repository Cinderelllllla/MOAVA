package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.dto.GroupAccountTransferContext;
import com.moava.group.account.dto.MockAccountLookupRow;
import com.moava.group.account.dto.MockBalanceTransferPlan;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.account.mapper.GroupAccountMapper;
import com.moava.group.account.mapper.GroupAccountMockLinkMapper;
import com.moava.group.account.mapper.GroupAccountTransferLockMapper;
import com.moava.group.create.service.GroupAccountCrypto;


import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupAccountTransferPreflightService {

    private final GroupAccountChangeMapper changeMapper;
    private final GroupAccountMapper accountMapper;
    private final GroupAccountMockLinkMapper linkMapper;
    private final GroupAccountTransferLockMapper lockMapper;
    private final GroupAccountCrypto crypto;
    private final MockBalanceTransferPlanner planner;
    private final GroupAccountBankCodeResolver bankCodeResolver;

    public GroupAccountTransferPreflightService(
            GroupAccountChangeMapper changeMapper,
            GroupAccountMapper accountMapper,
            GroupAccountMockLinkMapper linkMapper,
            GroupAccountTransferLockMapper lockMapper,
            GroupAccountCrypto crypto,
            MockBalanceTransferPlanner planner,
            GroupAccountBankCodeResolver bankCodeResolver
    ) {
        this.changeMapper = changeMapper;
        this.accountMapper = accountMapper;
        this.linkMapper = linkMapper;
        this.lockMapper = lockMapper;
        this.crypto = crypto;
        this.planner = planner;
        this.bankCodeResolver = bankCodeResolver;
    }

    // 내부 전용: 아직 Controller에 연결하지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupAccountTransferContext prepare(
            Long groupId,
            Long accountId,
            Long memberId
    ) {
        if (groupId == null || groupId <= 0
                || accountId == null || accountId <= 0
                || memberId == null || memberId <= 0) {
            throw new IllegalArgumentException(
                    "요청 정보가 올바르지 않습니다."
            );
        }

        // 1. 모임 행 잠금
        if (changeMapper.lockGroup(groupId) == null) {
            throw new IllegalStateException(
                    "모임을 찾을 수 없습니다."
            );
        }

        // 2. 활성 모임장 권한
        String owner =
                changeMapper.findActiveOwnerName(groupId, memberId);

        if (owner == null || owner.isBlank()) {
            throw new SecurityException(
                    "모임장만 잔액 이전을 진행할 수 있습니다."
            );
        }

        // 3. 현재 활성 회원 전원 동의 재확인
        int total = changeMapper.countActiveGroupMembers(groupId);
        int agreed = changeMapper.countAgreedMembers(
                groupId, accountId
        );
        int rejected = changeMapper.countRejectedMembers(
                groupId, accountId
        );

        if (total <= 0 || agreed != total || rejected != 0) {
            throw new IllegalStateException(
                    "현재 활성 회원 전원 동의가 필요합니다."
            );
        }

        // 4. 현재 계좌와 신청 계좌 재조회
        GroupAccountVerificationTarget current =
                accountMapper.findVerificationTarget(
                        groupId, memberId
                );

        GroupAccountVerificationTarget pending =
                changeMapper.findPendingVerificationTarget(
                        groupId, accountId
                );

        if (current == null || pending == null
                || !"CURRENT".equals(current.accountStatus())
                || !"PENDING".equals(pending.accountStatus())
                || !"VERIFIED".equals(pending.verifyStatus())
                || !Objects.equals(current.groupId(), groupId)
                || !Objects.equals(pending.groupId(), groupId)
                || !Objects.equals(current.ownerId(), memberId)
                || !Objects.equals(pending.ownerId(), memberId)
                || !Objects.equals(pending.gyeAccountId(), accountId)
                || Objects.equals(
                        current.gyeAccountId(), pending.gyeAccountId())) {
            throw new IllegalStateException(
                    "현재 계좌 또는 인증된 변경 신청이 유효하지 않습니다."
            );
        }

        // 5. 기존 거래 기록 확인
        String bankTranId =
                GroupAccountTransferId.fromAccountId(accountId);

        if (lockMapper.countTransferRecords(bankTranId) != 0) {
            throw new IllegalStateException(
                    "이미 처리된 이체 기록이 있습니다."
            );
        }

        // 6. 기존·신규 계좌번호 복호화
        String oldNumber = decrypt(current);
        String newNumber = decrypt(pending);

        // 7. 정확하게 연결된 모의 계좌 식별
        MockAccountLookupRow oldAccount =
                findUnique(current.bankName(), oldNumber);

        MockAccountLookupRow newAccount =
                findUnique(pending.bankName(), newNumber);

        if (Objects.equals(
                oldAccount.fintechUseNum(),
                newAccount.fintechUseNum())) {
            throw new IllegalStateException(
                    "동일한 모의 계좌 간 이체는 불가합니다."
            );
        }

        // 8. 교착 상태를 줄이기 위해 항상 식별번호 순서로 잠금
        String first = oldAccount.fintechUseNum()
                .compareTo(newAccount.fintechUseNum()) <= 0
                ? oldAccount.fintechUseNum()
                : newAccount.fintechUseNum();

        String second = first.equals(oldAccount.fintechUseNum())
                ? newAccount.fintechUseNum()
                : oldAccount.fintechUseNum();

        MockAccountLookupRow firstLocked =
                lockMapper.lockMockAccount(first);

        MockAccountLookupRow secondLocked =
                lockMapper.lockMockAccount(second);

        if (firstLocked == null || secondLocked == null) {
            throw new IllegalStateException(
                    "모의 계좌 행 잠금에 실패했습니다."
            );
        }

        MockAccountLookupRow lockedOld =
                oldAccount.fintechUseNum().equals(first)
                ? firstLocked : secondLocked;

        MockAccountLookupRow lockedNew =
                newAccount.fintechUseNum().equals(first)
                ? firstLocked : secondLocked;

        // 9. 잠금 중에도 원장 연결 정보가 바뀌지 않았는지 확인
        verifyLocked(lockedOld, oldAccount);
        verifyLocked(lockedNew, newAccount);

        // 10. 잠긴 원장의 최신 잔액으로 다시 계산
        MockBalanceTransferPlan plan =
        planner.plan(lockedOld, lockedNew);

        return new GroupAccountTransferContext(
                current.gyeAccountId(),
                plan
        );
    }

    private String decrypt(GroupAccountVerificationTarget account) {
        if (account.keyVer() == null
                || account.keyVer() != crypto.keyVersion()) {
            throw new IllegalStateException(
                    "계좌 암호화 키 버전이 일치하지 않습니다."
            );
        }

        String number;
        try {
            number = crypto.decrypt(account.accountNoEnc());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "계좌번호 복호화에 실패했습니다."
            );
        }

        if (number == null
                || !number.matches("[0-9]{8,30}")
                || account.accountNoLast4() == null
                || !number.endsWith(account.accountNoLast4())) {
            throw new IllegalStateException(
                    "계좌번호 정보가 올바르지 않습니다."
            );
        }

        return number;
    }

    private MockAccountLookupRow findUnique(
        String bankName,
        String accountNumber
    ) {
        String bankCode = bankCodeResolver.resolve(bankName);

        List<MockAccountLookupRow> matches =
                linkMapper.findMockAccounts(
                        bankCode,
                        accountNumber
                );

        if (matches == null || matches.size() != 1) {
            throw new IllegalStateException(
                    "모의 계좌를 유일하게 식별할 수 없습니다."
            );
        }

        MockAccountLookupRow account = matches.get(0);

        if (!bankCode.equals(account.bankCode())
                || !accountNumber.equals(account.accountNo())) {
            throw new IllegalStateException(
                    "모의 계좌 연결 정보가 일치하지 않습니다."
            );
        }

        return account;
    }

    private void verifyLocked(
            MockAccountLookupRow locked,
            MockAccountLookupRow original
    ) {
        if (!Objects.equals(
                    locked.fintechUseNum(),
                    original.fintechUseNum())
                || !Objects.equals(
                    locked.bankCode(), original.bankCode())
                || !Objects.equals(
                    locked.bankName(), original.bankName())
                || !Objects.equals(
                    locked.accountNo(), original.accountNo())) {
            throw new IllegalStateException(
                    "잠금 중 모의 계좌 정보가 변경되었습니다."
            );
        }
    }
}
