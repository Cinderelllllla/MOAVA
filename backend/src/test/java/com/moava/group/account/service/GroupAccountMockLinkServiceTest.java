package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountMockLinkResponse;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.dto.MockAccountLookupRow;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.account.mapper.GroupAccountMapper;
import com.moava.group.account.mapper.GroupAccountMockLinkMapper;
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
class GroupAccountMockLinkServiceTest {

    @Mock
    GroupAccountChangeMapper changeMapper;

    @Mock
    GroupAccountMapper accountMapper;

    @Mock
    GroupAccountMockLinkMapper mockMapper;

    @Mock
    GroupAccountCrypto crypto;

    @Mock
    GroupAccountBankCodeResolver bankCodeResolver;

    @InjectMocks
    GroupAccountMockLinkService service;

    private static final Long GROUP_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long CURRENT_ACCOUNT_ID = 50L;
    private static final Long PENDING_ACCOUNT_ID = 100L;

    private static final String BANK_NAME = "테스트은행";
    private static final String BANK_CODE = "004";

    private static final String CURRENT_NUMBER = "123412341234";
    private static final String PENDING_NUMBER = "567856785678";

    private static final String CURRENT_FINTECH =
            "111111111111111111111111";

    private static final String PENDING_FINTECH =
            "222222222222222222222222";

    // =========================================================
    // 테스트용 GYE_ACCOUNT 인증 대상
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
    // 테스트용 MOCK_KFTC_ACCOUNT 조회 결과
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
    // 정상 모임장 및 현재·신규 계좌 Mock
    // =========================================================
    private void mockValidAccounts() {

        when(changeMapper.findActiveOwnerName(
                GROUP_ID, MEMBER_ID
        )).thenReturn("모임장");

        when(accountMapper.findVerificationTarget(
                GROUP_ID, MEMBER_ID
        )).thenReturn(
                account(
                        CURRENT_ACCOUNT_ID,
                        "CURRENT",
                        "current-enc",
                        "1234"
                )
        );

        when(changeMapper.findPendingVerificationTarget(
                GROUP_ID, PENDING_ACCOUNT_ID
        )).thenReturn(
                account(
                        PENDING_ACCOUNT_ID,
                        "PENDING",
                        "pending-enc",
                        "5678"
                )
        );
    }

    // =========================================================
    // 계좌번호 복호화 Mock
    // =========================================================
    private void mockDecryptAccounts() {

        when(crypto.keyVersion()).thenReturn(1);

        when(crypto.decrypt("current-enc"))
                .thenReturn(CURRENT_NUMBER);

        when(crypto.decrypt("pending-enc"))
                .thenReturn(PENDING_NUMBER);
    }

    // =========================================================
    // 테스트 1. 은행코드 기준 정상 연결 확인
    // =========================================================
    @Test
    void 은행코드기준_기존신규계좌_정상연결() {

        mockValidAccounts();
        mockDecryptAccounts();

        when(bankCodeResolver.resolve(BANK_NAME))
                .thenReturn(BANK_CODE);

        MockAccountLookupRow currentMock =
                mockAccount(
                        CURRENT_FINTECH,
                        CURRENT_NUMBER,
                        "500000"
                );

        MockAccountLookupRow pendingMock =
                mockAccount(
                        PENDING_FINTECH,
                        PENDING_NUMBER,
                        "0"
                );

        // 은행명 대신 BANK_CODE로 조회
        when(mockMapper.findMockAccounts(
                BANK_CODE,
                CURRENT_NUMBER
        )).thenReturn(List.of(currentMock));

        when(mockMapper.findMockAccounts(
                BANK_CODE,
                PENDING_NUMBER
        )).thenReturn(List.of(pendingMock));

        GroupAccountMockLinkResponse result =
                service.checkLinks(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                );

        assertEquals(GROUP_ID, result.groupId());

        assertEquals(
                PENDING_ACCOUNT_ID,
                result.gyeAccountId()
        );

        assertTrue(result.currentLinked());
        assertTrue(result.pendingLinked());

        assertTrue(result.transferPrerequisitesReady());

        assertTrue(result.blockers().isEmpty());

        assertEquals(
                0,
                result.currentBalance()
                        .compareTo(new BigDecimal("500000"))
        );

        assertEquals(
                0,
                result.pendingBalance()
                        .compareTo(BigDecimal.ZERO)
        );

        // 정확한 은행코드 + 계좌번호 조회 검증
        verify(mockMapper).findMockAccounts(
                BANK_CODE,
                CURRENT_NUMBER
        );

        verify(mockMapper).findMockAccounts(
                BANK_CODE,
                PENDING_NUMBER
        );

        verify(bankCodeResolver, times(2))
                .resolve(BANK_NAME);
    }

    // =========================================================
    // 테스트 2. 미등록 은행코드면 연결 차단
    // =========================================================
    @Test
    void 은행코드가_미등록이면_연결차단() {

        mockValidAccounts();
        mockDecryptAccounts();

        when(bankCodeResolver.resolve(BANK_NAME))
                .thenThrow(
                        new IllegalStateException(
                                "등록되지 않은 은행입니다."
                        )
                );

        GroupAccountMockLinkResponse result =
                service.checkLinks(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                );

        assertFalse(result.currentLinked());
        assertFalse(result.pendingLinked());

        assertFalse(result.transferPrerequisitesReady());

        assertEquals(2, result.blockers().size());

        assertTrue(
                result.blockers().stream()
                        .allMatch(
                                message ->
                                        message.contains("은행코드")
                        )
        );

        // 은행코드가 없으면 모의 은행 원장 조회 금지
        verifyNoInteractions(mockMapper);
    }

    // =========================================================
    // 테스트 3. 모의 원장 은행코드 불일치 차단
    // =========================================================
    @Test
    void 모의원장_은행코드불일치면_연결차단() {

        mockValidAccounts();
        mockDecryptAccounts();

        when(bankCodeResolver.resolve(BANK_NAME))
                .thenReturn(BANK_CODE);

        // 기존 계좌는 올바르게 연결됨
        MockAccountLookupRow currentMock =
                mockAccount(
                        CURRENT_FINTECH,
                        CURRENT_NUMBER,
                        "500000"
                );

        // 신규 계좌는 조회됐지만 은행코드 불일치
        MockAccountLookupRow invalidPending =
                new MockAccountLookupRow(
                        PENDING_FINTECH,
                        "999",
                        BANK_NAME,
                        PENDING_NUMBER,
                        BigDecimal.ZERO,
                        "ACTIVE",
                        "Y"
                );

        when(mockMapper.findMockAccounts(
                BANK_CODE,
                CURRENT_NUMBER
        )).thenReturn(List.of(currentMock));

        when(mockMapper.findMockAccounts(
                BANK_CODE,
                PENDING_NUMBER
        )).thenReturn(List.of(invalidPending));

        GroupAccountMockLinkResponse result =
                service.checkLinks(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                );

        assertTrue(result.currentLinked());

        assertFalse(result.pendingLinked());

        assertFalse(result.transferPrerequisitesReady());

        assertTrue(
                result.blockers().stream()
                        .anyMatch(
                                message ->
                                        message.contains("은행코드가 일치하지")
                        )
        );
    }
}
