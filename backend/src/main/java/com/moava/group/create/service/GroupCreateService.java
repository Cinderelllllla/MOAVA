
package com.moava.group.create.service;

import com.moava.group.create.dto.GroupCreateRequest;
import com.moava.group.create.dto.GroupCreateResponse;
import com.moava.group.create.mapper.GroupCreateMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupCreateService {

    private final GroupCreateMapper mapper;
    private final GroupAccountCrypto crypto;

    public GroupCreateService(
            GroupCreateMapper mapper,
            GroupAccountCrypto crypto
    ) {
        this.mapper = mapper;
        this.crypto = crypto;
    }

    public void validateCreateRequest(GroupCreateRequest request) {
        GroupCreateValidator.validate(request);
    }

    @Transactional
    //@Transactional이 핵심
    //모임, 모임장, 계좌, 감사 로그 저장을 하나의 트랜잭션으로 처리 -> 오류 시 롤백
   
    public GroupCreateResponse createGroup(
            GroupCreateRequest request,
            Long authenticatedMemberId
    ) {
        // 1. 입력값 검증
        validateCreateRequest(request);

        // 2. 인증된 회원 확인
        if (authenticatedMemberId == null) {
            throw new SecurityException("로그인이 필요합니다.");
        }

        String memberName =
                mapper.findActiveMemberName(authenticatedMemberId);

        if (memberName == null) {
            throw new SecurityException(
                    "모임을 생성할 수 없는 회원입니다."
            );
        }

        // 3. 모임 ID 및 초대 코드 생성
        Long groupId = mapper.nextGroupId();
        String inviteCode = GroupInviteCodeGenerator.generate();

        // 4. 모임 저장
        mapper.insertGroup(
                groupId,
                authenticatedMemberId,
                request.title().strip(),
                request.purpose(),
                request.regionSido().strip(),
                request.regionSigungu().strip(),
                request.amount(),
                request.maxMembers(),
                request.months(),
                request.payDay(),
                request.assumedRate(),
                request.visibility(),
                inviteCode,
                request.rulesTitle().strip(),
                request.rulesBody().strip(),
                request.description().strip(),
                request.overduePolicy()
        );

        // 5. 모임장 자동 등록
        mapper.insertOwnerMember(
                groupId,
                authenticatedMemberId
        );

        // 6. 계좌번호 암호화 및 해시
        String accountNumber = request.accountNumber();

        String holderNameEnc = crypto.encrypt(memberName);
        String accountNoEnc = crypto.encrypt(accountNumber);
        String accountNoHash = crypto.hashAccountNumber(accountNumber);

        String accountNoLast4 =
                accountNumber.substring(accountNumber.length() - 4);
        if (holderNameEnc.getBytes(StandardCharsets.UTF_8).length > 80) {
                throw new IllegalArgumentException(
                         "예금주 이름이 암호화 저장 길이 제한을 초과합니다."
                );
        }

        if (accountNoEnc.getBytes(StandardCharsets.UTF_8).length > 100) {
                throw new IllegalArgumentException(
                        "계좌번호가 암호화 저장 길이 제한을 초과합니다."
                );
        }        

        // 7. 모임 계좌 등록 (인증 대기)
        mapper.insertGroupAccount(
                groupId,
                request.bankName().strip(),
                holderNameEnc,
                accountNoEnc,
                accountNoLast4,
                accountNoHash,
                crypto.keyVersion(),
                authenticatedMemberId
        );

        // 8. 감사 로그 저장
        mapper.insertGroupCreateAudit(
                groupId,
                authenticatedMemberId,
                "모임 생성 및 모임장 자동 등록"
        );

        // 9. 결과 반환
        return new GroupCreateResponse(
                groupId,
                authenticatedMemberId,
                "RECRUITING",
                "모임이 생성되었습니다."
        );
    }
}
