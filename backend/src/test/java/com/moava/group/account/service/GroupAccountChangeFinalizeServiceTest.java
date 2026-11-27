package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountFinalizeResponse;
import com.moava.group.account.dto.GroupAccountTransferContext;
import com.moava.group.account.dto.MockBalanceTransferPlan;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.account.mapper.GroupAccountTransferExecutionMapper;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupAccountChangeFinalizeServiceTest {

    @Mock
    GroupAccountTransferPreflightService preflight;

    @Mock
    GroupAccountTransferExecutionMapper executionMapper;

    @Mock
    GroupAccountChangeMapper changeMapper;

    @InjectMocks
    GroupAccountChangeFinalizeService service;

    // 기존 계좌 ID
    private static final Long CURRENT_ACCOUNT_ID = 50L;

    // 신규 계좌 변경 신청 ID
    private static final Long PENDING_ACCOUNT_ID = 100L;

    // 모임 ID와 모임장 회원 ID
    private static final Long GROUP_ID = 1L;
    private static final Long MEMBER_ID = 10L;

    // 모의 은행 계좌 식별번호
    private final String oldId = "111111111111111111111111";
    private final String newId = "222222222222222222222222";

    // 모의 잔액 이전 계획 생성
    private MockBalanceTransferPlan plan(String amount) {

        BigDecimal money = new BigDecimal(amount);

        return new MockBalanceTransferPlan(
                oldId,
                newId,
                money,
                BigDecimal.ZERO,
                money,
                money.signum() > 0
        );
    }

    // 기존 계좌 ID와 잔액 이전 계획을 함께 전달
    private GroupAccountTransferContext context(String amount) {

        return new GroupAccountTransferContext(
                CURRENT_ACCOUNT_ID,
                plan(amount)
        );
    }

    // 정상적인 DB 갱신 Mock
    private void mockSuccessUpdates() {

        when(executionMapper.retireCurrentAccount(
                GROUP_ID,
                CURRENT_ACCOUNT_ID
        )).thenReturn(1);

        when(executionMapper.activatePendingAccount(
                GROUP_ID,
                PENDING_ACCOUNT_ID
        )).thenReturn(1);

        when(changeMapper.insertChangeAudit(
                eq(GROUP_ID),
                eq(MEMBER_ID),
                anyString()
        )).thenReturn(1);
    }

    // ---------------------------------------------------------
    // 테스트 1. 정상 잔액 이전 및 대표 계좌 변경 성공
    // ---------------------------------------------------------
    @Test
    void 정상_잔액이전과_대표계좌변경_성공() {

        when(preflight.prepare(
                GROUP_ID,
                PENDING_ACCOUNT_ID,
                MEMBER_ID
        )).thenReturn(context("500000"));

        when(executionMapper.debit(
                eq(oldId),
                any(),
                any()
        )).thenReturn(1);

        when(executionMapper.credit(
                eq(newId),
                any(),
                any()
        )).thenReturn(1);

        when(executionMapper.insertTransferRecord(
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        )).thenReturn(1);

        mockSuccessUpdates();

        GroupAccountFinalizeResponse result =
                service.finalizeChange(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                );

        // 최종 계좌 상태 확인
        assertEquals(
                "CURRENT",
                result.accountStatus()
        );

        // 이전 금액 확인
        assertEquals(
                0,
                result.transferredAmount()
                        .compareTo(new BigDecimal("500000"))
        );

        // 기존 계좌 출금 확인
        verify(executionMapper).debit(
                eq(oldId),
                any(),
                any()
        );

        // 신규 계좌 입금 확인
        verify(executionMapper).credit(
                eq(newId),
                any(),
                any()
        );

        // 출금/입금 거래 원장 각각 1건
        verify(executionMapper, times(2))
                .insertTransferRecord(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyString()
                );

        // 실행 순서 검증
        var order = inOrder(executionMapper);

        order.verify(executionMapper).debit(
                eq(oldId),
                any(),
                any()
        );

        order.verify(executionMapper).credit(
                eq(newId),
                any(),
                any()
        );

        // 검증한 기존 계좌 ID만 RETIRED
        order.verify(executionMapper).retireCurrentAccount(
                GROUP_ID,
                CURRENT_ACCOUNT_ID
        );

        // 신규 계좌 CURRENT 전환
        order.verify(executionMapper).activatePendingAccount(
                GROUP_ID,
                PENDING_ACCOUNT_ID
        );
    }

    // ---------------------------------------------------------
    // 테스트 2. 잔액이 0원이면 거래 없이 대표 계좌 변경
    // ---------------------------------------------------------
    @Test
    void 잔액_0원이면_거래없이_대표계좌만변경() {

        when(preflight.prepare(
                GROUP_ID,
                PENDING_ACCOUNT_ID,
                MEMBER_ID
        )).thenReturn(context("0"));

        mockSuccessUpdates();

        GroupAccountFinalizeResponse result =
                service.finalizeChange(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                );

        assertEquals(
                0,
                result.transferredAmount()
                        .compareTo(BigDecimal.ZERO)
        );

        // 잔액이 0원이면 출금하지 않는다.
        verify(executionMapper, never()).debit(
                anyString(),
                any(),
                any()
        );

        // 잔액이 0원이면 입금하지 않는다.
        verify(executionMapper, never()).credit(
                anyString(),
                any(),
                any()
        );

        // 금액 0원 거래 원장은 생성하지 않는다.
        verify(executionMapper, never())
                .insertTransferRecord(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyString()
                );

        // 기존 대표 계좌 종료
        verify(executionMapper).retireCurrentAccount(
                GROUP_ID,
                CURRENT_ACCOUNT_ID
        );

        // 신규 대표 계좌 확정
        verify(executionMapper).activatePendingAccount(
                GROUP_ID,
                PENDING_ACCOUNT_ID
        );
    }

    // ---------------------------------------------------------
    // 테스트 3. 입금 실패 시 대표 계좌 변경 차단
    // ---------------------------------------------------------
    @Test
    void 입금실패면_대표계좌변경차단() {

        when(preflight.prepare(
                GROUP_ID,
                PENDING_ACCOUNT_ID,
                MEMBER_ID
        )).thenReturn(context("500000"));

        when(executionMapper.debit(
                eq(oldId),
                any(),
                any()
        )).thenReturn(1);

        // 입금 실패
        when(executionMapper.credit(
                eq(newId),
                any(),
                any()
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.finalizeChange(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                )
        );

        // 기존 대표 계좌 종료 금지
        verify(executionMapper, never())
                .retireCurrentAccount(
                        anyLong(),
                        anyLong()
                );

        // 신규 대표 계좌 전환 금지
        verify(executionMapper, never())
                .activatePendingAccount(
                        anyLong(),
                        anyLong()
                );

        // 거래 원장 기록 금지
        verify(executionMapper, never())
                .insertTransferRecord(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyString()
                );
    }

    // ---------------------------------------------------------
    // 테스트 4. 전원 동의 검증 실패 시 출금 차단
    // ---------------------------------------------------------
    @Test
    void 전원동의검증실패시_출금차단() {

        when(preflight.prepare(
                GROUP_ID,
                PENDING_ACCOUNT_ID,
                MEMBER_ID
        )).thenThrow(
                new IllegalStateException(
                        "현재 활성 회원 전원 동의가 필요합니다."
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.finalizeChange(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                )
        );

        // 검증 실패 시 DB 변경 Mapper 호출 없음
        verifyNoInteractions(executionMapper);
    }

    // ---------------------------------------------------------
    // 테스트 5. 감사 로그 실패 시 예외 발생
    // ---------------------------------------------------------
    @Test
    void 감사로그실패시_예외발생() {

        when(preflight.prepare(
                GROUP_ID,
                PENDING_ACCOUNT_ID,
                MEMBER_ID
        )).thenReturn(context("0"));

        when(executionMapper.retireCurrentAccount(
                GROUP_ID,
                CURRENT_ACCOUNT_ID
        )).thenReturn(1);

        when(executionMapper.activatePendingAccount(
                GROUP_ID,
                PENDING_ACCOUNT_ID
        )).thenReturn(1);

        // 감사 로그 저장 실패
        when(changeMapper.insertChangeAudit(
                eq(GROUP_ID),
                eq(MEMBER_ID),
                anyString()
        )).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.finalizeChange(
                        GROUP_ID,
                        PENDING_ACCOUNT_ID,
                        MEMBER_ID
                )
        );
    }
}
