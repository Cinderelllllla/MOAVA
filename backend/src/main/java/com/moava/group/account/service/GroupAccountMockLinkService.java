package com.moava.group.account.service;

import com.moava.group.account.dto.GroupAccountMockLinkResponse;
import com.moava.group.account.dto.GroupAccountVerificationTarget;
import com.moava.group.account.dto.MockAccountLookupRow;
import com.moava.group.account.mapper.GroupAccountChangeMapper;
import com.moava.group.account.mapper.GroupAccountMapper;
import com.moava.group.account.mapper.GroupAccountMockLinkMapper;
import com.moava.group.create.service.GroupAccountCrypto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroupAccountMockLinkService {

    private final GroupAccountChangeMapper changeMapper;
    private final GroupAccountMapper accountMapper;
    private final GroupAccountMockLinkMapper mockMapper;
    private final GroupAccountCrypto crypto;

    // F20: 은행명 -> 은행코드 변환기
    private final GroupAccountBankCodeResolver bankCodeResolver;

    public GroupAccountMockLinkService(
            GroupAccountChangeMapper changeMapper,
            GroupAccountMapper accountMapper,
            GroupAccountMockLinkMapper mockMapper,
            GroupAccountCrypto crypto,
            GroupAccountBankCodeResolver bankCodeResolver
    ) {
        this.changeMapper = changeMapper;
        this.accountMapper = accountMapper;
        this.mockMapper = mockMapper;
        this.crypto = crypto;
        this.bankCodeResolver = bankCodeResolver;
    }

    // =========================================================
    // F20: 기존·신규 모의 계좌 연결 상태 확인
    // =========================================================
    @Transactional(readOnly = true)
    public GroupAccountMockLinkResponse checkLinks(
            Long groupId,
            Long accountId,
            Long memberId
    ) {

        // 1. 요청값 검증
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

        // 2. 현재 모임장 권한 확인
        String ownerName =
                changeMapper.findActiveOwnerName(
                        groupId,
                        memberId
                );

        if (ownerName == null || ownerName.isBlank()) {
            throw new SecurityException(
                    "모임장만 모의 계좌 연결 상태를 조회할 수 있습니다."
            );
        }

        // 3. 현재 계좌 조회
        GroupAccountVerificationTarget current =
                accountMapper.findVerificationTarget(
                        groupId,
                        memberId
                );

        // 4. 변경 신청 계좌 조회
        GroupAccountVerificationTarget pending =
                changeMapper.findPendingVerificationTarget(
                        groupId,
                        accountId
                );

        // 5. 모임·계좌 소유·상태 확인
        if (current == null
                || pending == null
                || !Objects.equals(
                        current.groupId(), groupId)
                || !Objects.equals(
                        pending.groupId(), groupId)
                || !Objects.equals(
                        current.ownerId(), memberId)
                || !Objects.equals(
                        pending.ownerId(), memberId)
                || !Objects.equals(
                        pending.gyeAccountId(), accountId)
                || !"CURRENT".equals(
                        current.accountStatus())
                || !"PENDING".equals(
                        pending.accountStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "현재 계좌 또는 변경 신청 계좌를 찾을 수 없습니다."
            );
        }

        // 6. 기존 계좌와 신규 계좌 동일 여부
        if (Objects.equals(
                current.gyeAccountId(),
                pending.gyeAccountId())) {

            throw new IllegalStateException(
                    "기존 계좌와 변경 신청 계좌가 동일합니다."
            );
        }

        // 7. 암호화된 계좌번호 복호화
        String currentNumber =
                decryptAccountNumber(current);

        String pendingNumber =
                decryptAccountNumber(pending);

        List<String> blockers = new ArrayList<>();

        // 8. 은행코드 + 계좌번호로 현재 모의 계좌 조회
        MockAccountLookupRow currentMock =
                findUsableMockAccount(
                        current.bankName(),
                        currentNumber,
                        "기존 계좌",
                        blockers
                );

        // 9. 은행코드 + 계좌번호로 신규 모의 계좌 조회
        MockAccountLookupRow pendingMock =
                findUsableMockAccount(
                        pending.bankName(),
                        pendingNumber,
                        "신규 계좌",
                        blockers
                );

        // 10. 동일한 모의 계좌로 연결되는지 확인
        if (currentMock != null
                && pendingMock != null
                && Objects.equals(
                        currentMock.fintechUseNum(),
                        pendingMock.fintechUseNum())) {

            blockers.add(
                    "기존 계좌와 신규 계좌가 같은 모의 계좌입니다."
            );
        }

        // 11. 차단 사유가 없을 때만 준비 완료
        boolean ready = blockers.isEmpty();

        // 12. 계좌번호·핀테크 이용번호 노출 없이 반환
        return new GroupAccountMockLinkResponse(
                groupId,
                accountId,
                currentMock != null,
                pendingMock != null,
                currentMock == null
                        ? null
                        : currentMock.balance(),
                pendingMock == null
                        ? null
                        : pendingMock.balance(),
                ready,
                List.copyOf(blockers)
        );
    }

    // =========================================================
    // F20: 암호화된 계좌번호 복호화
    // =========================================================
    private String decryptAccountNumber(
            GroupAccountVerificationTarget account
    ) {

        // 암호화 키 버전 확인
        if (account.keyVer() == null
                || account.keyVer() != crypto.keyVersion()) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "계좌 암호화 키 버전을 확인할 수 없습니다."
            );
        }

        String number;

        try {
            number = crypto.decrypt(
                    account.accountNoEnc()
            );

        } catch (RuntimeException e) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "등록된 계좌 정보를 확인할 수 없습니다."
            );
        }

        // 계좌번호 형식 및 마지막 4자리 검증
        if (number == null
                || !number.matches("[0-9]{8,30}")
                || account.accountNoLast4() == null
                || !account.accountNoLast4()
                        .matches("[0-9]{4}")
                || !number.endsWith(
                        account.accountNoLast4())) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "등록된 계좌 정보가 올바르지 않습니다."
            );
        }

        return number;
    }

    // =========================================================
    // F20: 은행코드 기반 모의 계좌 연결 확인
    // =========================================================
    private MockAccountLookupRow findUsableMockAccount(
            String bankName,
            String accountNumber,
            String label,
            List<String> blockers
    ) {

        // 1. 은행명 -> 은행코드 변환
        String bankCode;

        try {
            bankCode = bankCodeResolver.resolve(
                    bankName
            );

        } catch (IllegalStateException e) {

            // 매핑되지 않은 은행은 연결 불가
            blockers.add(
                    label + "의 은행코드가 확인되지 않았습니다."
            );

            return null;
        }

        // 2. 은행코드 + 계좌번호로 모의 계좌 조회
        List<MockAccountLookupRow> matches =
                mockMapper.findMockAccounts(
                        bankCode,
                        accountNumber
                );

        // 3. 조회 결과 존재 및 상태 검증
        MockAccountLookupRow account =
                requireUsable(
                        matches,
                        label,
                        blockers
                );

        if (account == null) {
            return null;
        }

        // 4. 모의 은행 원장의 은행코드 일치 확인
        if (!Objects.equals(
                bankCode,
                account.bankCode())) {

            blockers.add(
                    label + "의 은행코드가 일치하지 않습니다."
            );

            return null;
        }

        // 5. 모의 은행 원장의 계좌번호 일치 확인
        if (!Objects.equals(
                accountNumber,
                account.accountNo())) {

            blockers.add(
                    label + "의 계좌번호가 일치하지 않습니다."
            );

            return null;
        }

        return account;
    }

    // =========================================================
    // F20: 모의 계좌 사용 가능 여부 검증
    // =========================================================
    private MockAccountLookupRow requireUsable(
            List<MockAccountLookupRow> matches,
            String label,
            List<String> blockers
    ) {

        // 1. 연결 계좌 없음
        if (matches == null || matches.isEmpty()) {

            blockers.add(
                    label + "가 모의 은행 원장에 없습니다."
            );

            return null;
        }

        // 2. 여러 계좌 조회 시 차단
        if (matches.size() != 1) {

            blockers.add(
                    label + "의 모의 계좌 식별이 모호합니다."
            );

            return null;
        }

        MockAccountLookupRow account = matches.get(0);

        // 3. 모의 계좌 상태 확인
        if (account == null
                || account.fintechUseNum() == null
                || account.fintechUseNum().isBlank()
                || account.fintechUseNum().length() > 24
                || account.bankCode() == null
                || account.bankCode().isBlank()
                || account.bankName() == null
                || account.bankName().isBlank()
                || account.accountNo() == null
                || account.accountNo().isBlank()
                || account.balance() == null
                || account.balance()
                        .compareTo(BigDecimal.ZERO) < 0
                || !"ACTIVE".equals(account.status())
                || !"Y".equals(account.leaderYn())) {

            blockers.add(
                    label + "의 모의 계좌 상태가 유효하지 않습니다."
            );

            return null;
        }

        return account;
    }
}
