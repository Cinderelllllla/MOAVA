package com.moava.group.create.service;

import com.moava.group.create.dto.GroupCreateRequest;
import com.moava.group.create.dto.GroupCreateResponse;
import com.moava.group.create.mapper.GroupCreateMapper;
import com.moava.group.map.GeoPoint;
import com.moava.group.map.KakaoGeocodingClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupCreateService {

    // F19 - 계좌 암호문 저장 길이 제한
    private static final int HOLDER_NAME_ENC_MAX_BYTES = 120;
    private static final int ACCOUNT_NO_ENC_MAX_BYTES = 100;

    // DDL v2.0 계좌 중복 방지 인덱스
    private static final String ACCOUNT_UNIQUE_INDEX =
            "UX_GYE_ACCOUNT_ACTIVE_HASH";

    private final GroupCreateMapper mapper;
    private final GroupAccountCrypto crypto;
    private final KakaoGeocodingClient kakaoGeocodingClient;

    public GroupCreateService(
            GroupCreateMapper mapper,
            GroupAccountCrypto crypto,
            KakaoGeocodingClient kakaoGeocodingClient
    ) {
        this.mapper = mapper;
        this.crypto = crypto;
        this.kakaoGeocodingClient = kakaoGeocodingClient;
    }

    public void validateCreateRequest(GroupCreateRequest request) {
        GroupCreateValidator.validate(request);
    }

    // F19 - 계좌 중복 등록 확인
    // 전체 모임의 CURRENT, PENDING 계좌를 대상으로 검사
    // RETIRED 계좌는 중복 검사 대상에서 제외
    private void validateDuplicateAccount(
            String accountNoHash
    ) {

        int duplicateCount =
                mapper.countDuplicateAccount(accountNoHash);

        if (duplicateCount > 0) {

            throw new IllegalStateException(
                    "이미 등록된 계좌입니다."
            );
        }
    }

    // F19 - DB 계좌 중복 방지 인덱스 위반 확인
    // 다른 PK, UNIQUE 제약 위반과 구분한다.
    private boolean isDuplicateAccountViolation(
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

    // F19 - 암호문 저장 길이 검증
    private void validateEncryptedAccountLength(
            String holderNameEnc,
            String accountNoEnc
    ) {

        if (holderNameEnc.getBytes(StandardCharsets.UTF_8).length
                > HOLDER_NAME_ENC_MAX_BYTES) {

            throw new IllegalArgumentException(
                    "예금주 이름이 암호화 저장 길이 제한을 초과합니다."
            );
        }

        if (accountNoEnc.getBytes(StandardCharsets.UTF_8).length
                > ACCOUNT_NO_ENC_MAX_BYTES) {

            throw new IllegalArgumentException(
                    "계좌번호가 암호화 저장 길이 제한을 초과합니다."
            );
        }
    }

    @Transactional
    // 모임, 모임장, 계좌, 감사 로그 저장을
    // 하나의 트랜잭션으로 처리한다.
    // 오류 발생 시 전체 롤백
    public GroupCreateResponse createGroup(
            GroupCreateRequest request,
            Long authenticatedMemberId
    ) {

        // 1. 입력값 검증
        validateCreateRequest(request);

        // 2. 인증된 회원 확인
        if (authenticatedMemberId == null) {

            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        String memberName =
                mapper.findActiveMemberName(
                        authenticatedMemberId
                );

        if (memberName == null) {

            throw new SecurityException(
                    "모임을 생성할 수 없는 회원입니다."
            );
        }

        // 3. 계좌 정보 준비
        String bankName =
                request.bankName().strip();

        String accountNumber =
                request.accountNumber();

        // 기존 계좌 해시 방식 유지
        String accountNoHash =
                crypto.hashAccountNumber(accountNumber);

        // 4. F19 - 동일 계좌 중복 등록 확인
        // DDL v2.0과 동일하게 계좌 해시만 비교
        // 모임 생성 전에 검증하여 불필요한 INSERT 방지
        validateDuplicateAccount(accountNoHash);

        // 5. 계좌번호 및 예금주 암호화
        String holderNameEnc =
                crypto.encrypt(memberName);

        String accountNoEnc =
                crypto.encrypt(accountNumber);

        String accountNoLast4 =
                accountNumber.substring(
                        accountNumber.length() - 4
                );

        // F19 - 암호문 저장 길이 검증
        validateEncryptedAccountLength(
                holderNameEnc,
                accountNoEnc
        );

        // 6. 모임 ID 및 초대 코드 생성
        Long groupId =
                mapper.nextGroupId();

        String inviteCode =
                GroupInviteCodeGenerator.generate();

        // 카카오맵 지역 좌표 조회
        // 최신 develop의 지오코딩 기능 유지
        String regionSido = request.regionSido().strip();
        String regionSigungu = request.regionSigungu().strip();

        Optional<GeoPoint> geoPoint =
                kakaoGeocodingClient.geocodeRegion(
                        regionSido,
                        regionSigungu
                );

        BigDecimal lat =
                geoPoint.map(GeoPoint::lat).orElse(null);

        BigDecimal lng =
                geoPoint.map(GeoPoint::lng).orElse(null);

        // 7. 모임 저장
        int groupInserted = mapper.insertGroup(
                groupId,
                authenticatedMemberId,
                request.title().strip(),
                request.purpose(),
                regionSido,
                regionSigungu,
                lat,
                lng,
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

        if (groupInserted != 1) {

            throw new IllegalStateException(
                    "모임 저장에 실패했습니다."
            );
        }

        // 8. 모임장 자동 등록
        int inserted = mapper.insertOwnerMember(
                groupId,
                authenticatedMemberId
        );

        if (inserted != 1) {

            throw new IllegalStateException(
                    "모임장 등록에 실패했습니다."
            );
        }

        // 9. 모임 계좌 등록
        // VERIFY_STATUS = PENDING
        // ACCOUNT_STATUS = CURRENT
        // 실제 계좌 소유 인증 완료 상태와 구분한다.
        int accountInserted;

        try {

            accountInserted = mapper.insertGroupAccount(
                    groupId,
                    bankName,
                    holderNameEnc,
                    accountNoEnc,
                    accountNoLast4,
                    accountNoHash,
                    crypto.keyVersion(),
                    authenticatedMemberId
            );

        } catch (DuplicateKeyException e) {

            // F19 - 동시 계좌 등록 요청에 대한 DB 중복 방어
            // 사전 조회를 동시에 통과한 경우에도
            // UNIQUE INDEX에서 중복 등록을 차단한다.
            if (isDuplicateAccountViolation(e)) {

                throw new IllegalStateException(
                        "이미 등록된 계좌입니다."
                );
            }

            // 다른 PK 또는 UNIQUE 제약 위반은
            // 계좌 중복 오류로 잘못 처리하지 않는다.
            throw e;
        }

        if (accountInserted != 1) {

            throw new IllegalStateException(
                    "모임 계좌 등록에 실패했습니다."
            );
        }

        // 10. 모임 생성 감사 로그 저장
        int auditInserted = mapper.insertGroupCreateAudit(
                groupId,
                authenticatedMemberId,
                "모임 생성 및 모임장 자동 등록"
        );

        if (auditInserted != 1) {

            throw new IllegalStateException(
                    "모임 생성 감사 로그 저장에 실패했습니다."
            );
        }

        // 11. F19 - 계좌 등록 감사 로그 저장
        // 계좌번호, 예금주명, 암호문, 해시값은 기록하지 않는다.
        int accountAuditInserted =
                mapper.insertAccountRegisterAudit(
                        groupId,
                        authenticatedMemberId,
                        "모임 계좌 등록 및 소유 인증 대기"
                );

        if (accountAuditInserted != 1) {

            throw new IllegalStateException(
                    "계좌 등록 감사 로그 저장에 실패했습니다."
            );
        }

        // 12. 결과 반환
        return new GroupCreateResponse(
                groupId,
                authenticatedMemberId,
                "RECRUITING",
                "모임이 생성되었습니다."
        );
    }
}
