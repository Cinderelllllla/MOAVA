package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountTransferContext;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.dto.MockAccountLookupRow;
import com.moava.group.account.dto.MockBalanceTransferPlan;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.account.mapper.GroupAccountMapper;
import com.moava.group.account.mapper.GroupAccountMockLinkMapper;
import com.moava.group.account.mapper.GroupAccountTransferLockMapper;
import com.moava.group.create.service.GroupAccountCrypto;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupAccountTransferPreflightServiceTest {

    @Mock
    GroupAccountChangeMapper changeMapper;

    @Mock
    GroupAccountMapper accountMapper;

    @Mock
    GroupAccountMockLinkMapper linkMapper;

    @Mock
    GroupAccountTransferLockMapper lockMapper;

    @Mock
    GroupAccountCrypto crypto;

    @Mock
    MockBalanceTransferPlanner planner;

    @Mock
    GroupAccountBankCodeResolver bankCodeResolver;

    @InjectMocks
    GroupAccountTransferPreflightService service;

    private static final Long GROUP_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    private static final Long CURRENT_ACCOUNT_ID = 50L;
    private static final Long PENDING_ACCOUNT_ID = 100L;

    private static final String BANK_NAME = "테스트은행";
    private static final String BANK_CODE = "004";

    private static final String CURRENT_NUMBER = "123412341234";
    private static final String PENDING_NUMBER = "567856785678";

    private static final String OLD_FINTECH =
            "111111111111111111111111";

    private static final String NEW_FINTECH =
            "222222222222222222222222";

    // =========================================================
    // 테스트용 인증 대상 계좌
    // =========================================================
    private GroupAccountVerificationTarget account(
            Long accountId,
            String accountStatus,
            String encrypted,
            String last4
    ) {
        return new GroupAccountVerificationTarget(
                accountId,
                GROUP_ID,
                MEMBER_ID,
                BANK_NAME,
                encrypted,
                last4,
                1,
                "VERIFIED",
                accountStatus
        );
    }

    // =========================================================
    // 테스트용 모의 은행 계좌
    // =========================================================
    private MockAccountLookupRow mockAccount(
            String fintechUseNum,
            String accountNumber,
            String balance
    ) {
        return new MockAccountLookupRow(
                fintechUseNum,
                BANK_CODE,
                BANK_NAME,
                accountNumber,
                new BigDecimal(balance),
                "ACTIVE",
                "Y"
        );
    }

    // =========================================================
    // 모임장·전원 동의 Mock
    // =========================================================
    private void mockValidGroup() {

        when(changeMapper.lockGroup(GROUP_ID))
                .thenReturn(GROUP_ID);

        when(changeMapper.findActiveOwnerName(
                GROUP_ID,
                MEMBER_ID
        )).thenReturn("모임장");

        when(changeMapper.countActiveGroupMembers(GROUP_ID))
                .thenReturn(3);

        when(changeMapper.countAgreedMembers(
                GROUP_ID,
                PENDING_ACCOUNT_ID
        )).thenReturn(3);
    }

    // =========================================================
    // 현재 및 신규 계좌 Mock
    // =========================================================
    private void mockValidAccounts() {

        when(accountMapper.findVerificationTarget(
                GROUP_ID,
                MEMBER_ID
        )).thenReturn(
                account(
                        CURRENT_ACCOUNT_ID,
                        "CURRENT",
                        "old-enc",
                        "1234"
                )
        );

        when(changeMapper.findPendingVerificationTarget(
                GROUP_ID,
                PENDING_ACCOUNT_ID
        )).thenReturn(
                account(
                        PENDING_ACCOUNT_ID,
                        "PENDING",
                        "new-enc",
                        "5678"
                )
        );
    }

    // =========================================================
    // 테스트 1. 은행코드 + 잠금 후 최신 잔액으로 이체 계획
    // =========================================================
    @Test
    void 정상조건에서_은행코드확인후_이체계획생성() {

        mockValidGroup();
        mockValidAccounts();

        when(crypto.keyVersion()).thenReturn(1);

        when(crypto.decrypt("old-enc"))
                .thenReturn(CURRENT_NUMBER);

        when(crypto.decrypt("new-enc"))
                .thenReturn(PENDING_NUMBER);

        // 은행명 -> 은행코드 변환
        when(bankCodeResolver.resolve(BANK_NAME))
                .thenReturn(BANK_CODE);

        MockAccountLookupRow oldAccount =
                mockAccount(
                        OLD_FINTECH,
                        CURRENT_NUMBER,
                        "500000"
                );

        MockAccountLookupRow newAccount =
                mockAccount(
                        NEW_FINTECH,
                        PENDING_NUMBER,
                        "0"
                );

        // 유일한 은행코드 + 계좌번호로 조회
        when(linkMapper.findMockAccounts(
                BANK_CODE,
                CURRENT_NUMBER
        )).thenReturn(List.of(oldAccount));

        when(linkMapper.findMockAccounts(
                BANK_CODE,
                PENDING_NUMBER
        )).thenReturn(List.of(newAccount));

        // 식별번호 순서에 따른 행 잠금
        when(lockMapper.lockMockAccount(OLD_FINTECH))
                .thenReturn(oldAccount);

        when(lockMapper.lockMockAccount(NEW_FINTECH))
                .thenReturn(newAccount);

        MockBalanceTransferPlan expected =
                new MockBalanceTransferPlan(
                        OLD_FINTECH,
                        NEW_FINTECH,
                        new BigDecimal("500000"),
                        BigDecimal.ZERO,
                        new BigDecimal("500000"),
                        true
                );

        when(planner.plan(
                oldAccount,
                newAccount
        )).thenReturn(expected);

        GroupAccountTransferContext result =
                service.prepare(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                );

        // 검증한 기존 대표 계좌 ID 확인
        assertEquals(
                CURRENT_ACCOUNT_ID,
                result.currentAccountId()
        );

        // 잔액 이전 계획 확인
        assertEquals(
                expected,
                result.plan()
        );

        // 은행코드 기준 조회 확인
        verify(linkMapper).findMockAccounts(
                BANK_CODE,
                CURRENT_NUMBER
        );

        verify(linkMapper).findMockAccounts(
                BANK_CODE,
                PENDING_NUMBER
        );

        // 기존·신규 계좌 잠금 순서 확인
        var order = inOrder(lockMapper);

        order.verify(lockMapper)
                .lockMockAccount(OLD_FINTECH);

        order.verify(lockMapper)
                .lockMockAccount(NEW_FINTECH);

        // 잠긴 원장의 잔액으로 계산했는지 확인
        verify(planner).plan(
                oldAccount,
                newAccount
        );
    }

    // =========================================================
    // 테스트 2. 활성 회원 전원 동의가 없으면 차단
    // =========================================================
    @Test
    void 활성회원_미동의면_원장잠금불가() {

        when(changeMapper.lockGroup(GROUP_ID))
                .thenReturn(GROUP_ID);

        when(changeMapper.findActiveOwnerName(
                GROUP_ID,
                MEMBER_ID
        )).thenReturn("모임장");

        when(changeMapper.countActiveGroupMembers(GROUP_ID))
                .thenReturn(4);

        when(changeMapper.countAgreedMembers(
                GROUP_ID,
                PENDING_ACCOUNT_ID
        )).thenReturn(3);

        assertThrows(
                IllegalStateException.class,
                () -> service.prepare(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                )
        );

        // 전원 동의 미충족 시 계좌 잠금 및 이체 계획 생성 차단
        verifyNoInteractions(
                lockMapper,
                planner,
                bankCodeResolver
        );
    }

    // =========================================================
    // 테스트 3. 기존 이체 기록이 있으면 재이체 차단
    // =========================================================
    @Test
    void 기존이체기록이_있으면_중복계획차단() {

        mockValidGroup();
        mockValidAccounts();

        String bankTranId =
                GroupAccountTransferId.fromAccountId(
                        PENDING_ACCOUNT_ID
                );

        when(lockMapper.countTransferRecords(bankTranId))
                .thenReturn(2);

        assertThrows(
                IllegalStateException.class,
                () -> service.prepare(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                )
        );

        verify(lockMapper).countTransferRecords(
                bankTranId
        );

        // 거래 기록이 있으면 모의 계좌 잠금 금지
        verify(lockMapper, never())
                .lockMockAccount(anyString());

        // 은행코드 조회 및 이체 계획 생성 금지
        verifyNoInteractions(
                bankCodeResolver,
                planner
        );
    }
}
