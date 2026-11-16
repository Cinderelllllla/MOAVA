# DDL 기준 Mapper 검토 및 수정 제안

- 기준 DDL: `infra/sql/01_moava_ddl.sql` v1.6
- 검토 대상: 모임 생성, 수령순서, 증빙, 알림 MyBatis Mapper와 호출부
- 목적: 팀 검토·승인용 제안. **아래 After 코드는 제안이며 아직 소스에 반영하지 않았습니다.**

## 먼저 확인된 변경

`backend/src/main/resources/mapper/group/GroupCreateMapper.xml`의 SQL 내부 `//` 주석은 XML 주석으로 변경되어 있습니다. 이전에 우려했던 주석이 SQL로 실행되는 문제는 이 변경으로 해소됐습니다.

다만 현재 모임 생성 기능은 여전히 컴파일 계약이 맞지 않습니다. 서비스가 호출하는 `findActiveMemberName()`은 Mapper 인터페이스와 XML에 없고, 서비스·검증기는 `regionSido`/`regionSigungu`를 참조하지만 요청 DTO에는 `region`만 있습니다. 이전 Maven 컴파일에서도 이 오류가 확인됐습니다.

## 1. 모임 생성 DTO와 Mapper 계약 통일 — 우선순위 높음

**문제:** JSON 요청 DTO, 서비스 호출, Mapper 인터페이스/XML의 메서드·필드가 다릅니다. DDL의 `GYE_GROUP`은 `REGION_SIDO`, `REGION_SIGUNGU` 두 컬럼을 요구합니다. 이와 별개로 DDL에는 `GYE_GROUP_OWNER` 테이블이 없고, 모임장은 `GYE_GROUP.OWNER_ID`로 표현하므로 별도 테이블 INSERT를 추가할 필요가 없습니다.

**Before**

```java
// GroupCreateRequest.java
String region,
```

```java
// GroupCreateService.java에서 호출하지만 Mapper/XML에 정의가 없음
mapper.findActiveMemberName(authenticatedMemberId);
request.regionSido();
request.regionSigungu();
```

```java
// GroupCreateMapper.java에 선언돼 있지만 XML statement가 없음
int countActiveMember(@Param("memberId") Long memberId);
```

**After 제안**

```java
// GroupCreateRequest.java — DDL의 두 지역 컬럼과 서비스 사용에 맞춤
String regionSido,
String regionSigungu,
```

```java
// GroupCreateMapper.java — 실제 회원 이름 조회를 계약에 추가
String findActiveMemberName(@Param("memberId") Long memberId);
```

```xml
<!-- GroupCreateMapper.xml -->
<select id="findActiveMemberName" resultType="string">
    SELECT NAME
    FROM MEMBER
    WHERE MEMBER_ID = #{memberId}
      AND STATUS = 'ACTIVE'
</select>
```

`countActiveMember`는 사용 목적을 정한 뒤, 필요 없다면 인터페이스 선언을 제거하고 필요하다면 대응 XML을 추가하세요. `GroupCreateRequest` 변경에 맞춰 프론트 요청 JSON과 API 문서도 함께 맞춰야 합니다.

**장점 / 현업에서 하는 이유:** 요청 DTO → 서비스 → Mapper 인터페이스 → XML → DB 컬럼을 같은 계약으로 유지하면 컴파일·통합 테스트에서 문제를 조기에 찾을 수 있습니다. DB에 없는 테이블을 문서만 보고 구현하는 일도 예방합니다.

## 2. 수령 신청 수정의 상태·소유자 조건 강화 — 우선순위 높음

**문제:** 현재 수정 SQL은 `PAYOUT_REQUEST_ID`만으로 갱신합니다. 서비스에서 먼저 `REQUESTED` 상태를 읽더라도, 조회 후 동의 요청 등으로 상태가 바뀌면 그 사이에 수정될 수 있습니다.

**Before**

```xml
<update id="updatePayoutRequest">
    UPDATE PAYOUT_REQUEST
    SET ROUND_NO = #{roundNo},
        REASON = #{reason},
        AI_SUGGESTED_ROUND = #{aiSuggestedRound},
        CREATED_AT = SYSDATE
    WHERE PAYOUT_REQUEST_ID = #{payoutRequestId}
</update>
```

**After 제안**

```xml
<update id="updatePayoutRequest">
    UPDATE PAYOUT_REQUEST
    SET ROUND_NO = #{roundNo},
        REASON = #{reason},
        AI_SUGGESTED_ROUND = #{aiSuggestedRound},
        CREATED_AT = SYSDATE
    WHERE PAYOUT_REQUEST_ID = #{payoutRequestId}
      AND GROUP_ID = #{groupId}
      AND MEMBER_ID = #{memberId}
      AND STATUS = 'REQUESTED'
</update>
```

Mapper 인터페이스에도 `groupId`, `memberId` 파라미터를 추가하고, 서비스에서 변경 건수가 0이면 상태가 이미 바뀐 것으로 보고 충돌 응답을 반환하도록 연결합니다.

**추가로 확인할 점:** `selectActiveRequestByMember`는 진행 중인 신청이 여러 건이면 `ROWNUM = 1`로 임의의 한 건을 반환합니다. `PAYOUT_REQUEST`에는 `(GROUP_ID, MEMBER_ID)`의 진행 중 신청을 하나로 제한하는 제약이 없습니다. 정렬을 추가하면 조회 결과는 결정적이지만 중복 생성 자체는 막지 못합니다. 기존 중복 데이터를 정리한 후, 진행 상태에만 적용되는 함수 기반 유니크 인덱스 등 DB 차원의 제한도 검토하세요.

```sql
-- 개념 예시: 기존 중복 정리 및 Oracle 버전/실행계획 검토 후 적용
CREATE UNIQUE INDEX UX_PAYOUT_REQUEST_ACTIVE
ON PAYOUT_REQUEST (
    CASE WHEN STATUS IN ('REQUESTED', 'CONSENT_PENDING') THEN GROUP_ID END,
    CASE WHEN STATUS IN ('REQUESTED', 'CONSENT_PENDING') THEN MEMBER_ID END
);
```

**장점 / 현업에서 하는 이유:** 상태 확인과 갱신을 한 SQL의 조건으로 묶는 낙관적 동시성 제어는 중복 요청·경합 상황에서 잘못된 상태 변경을 방지합니다. 금융 업무에서는 애플리케이션의 사전 조회만으로 무결성을 보장하지 않고 DB 제약조건도 함께 사용합니다.

## 3. 전원 동의 검증을 “현재 ACTIVE 회원 각각의 동의”로 변경 — 우선순위 높음

**문제:** 현재 서비스는 ACTIVE 대상 인원 수와 `AGREED_YN='Y'`인 동의 행 수를 비교합니다. 탈퇴·제외된 회원의 과거 동의 행이 남으면 그 동의가 현재 ACTIVE 회원의 미응답을 대신할 수 있습니다.

**Before**

```java
int totalTargetMembers =
        payoutOrderMapper.countTotalActiveOtherMembers(groupId, applicantId);
int agreedMembers =
        payoutOrderMapper.countAgreedConsents(payoutRequestId);

if (agreedMembers < totalTargetMembers) {
    throw new IllegalStateException("영향받는 회원 전원의 동의가 필요해요.");
}
```

```xml
<select id="countAgreedConsents" resultType="int">
    SELECT COUNT(*)
    FROM CONSENT
    WHERE CONSENT_TYPE = 'ORDER'
      AND TARGET_ID = #{targetId}
      AND AGREED_YN = 'Y'
</select>
```

**After 제안:** 현재 ACTIVE인 대상 중 동의 행이 없는 사람 수를 계산해 0인지 확인합니다.

```xml
<select id="countMissingActiveConsents" resultType="int">
    SELECT COUNT(*)
    FROM GYE_MEMBER gm
    WHERE gm.GROUP_ID = #{groupId}
      AND gm.STATUS = 'ACTIVE'
      AND gm.MEMBER_ID != #{applicantId}
      AND NOT EXISTS (
          SELECT 1
          FROM CONSENT c
          WHERE c.CONSENT_TYPE = 'ORDER'
            AND c.TARGET_ID = #{payoutRequestId}
            AND c.GROUP_ID = gm.GROUP_ID
            AND c.MEMBER_ID = gm.MEMBER_ID
            AND c.AGREED_YN = 'Y'
      )
</select>
```

```java
int missing = payoutOrderMapper.countMissingActiveConsents(
        groupId, payoutRequestId, applicantId);
if (missing > 0) {
    throw new IllegalStateException("영향받는 회원 전원의 동의가 필요해요.");
}
```

Mapper 인터페이스 파라미터도 위 SQL의 `groupId`, `payoutRequestId`, `applicantId`와 일치시켜야 합니다. 동의 요청 후 ACTIVE 회원 구성이 달라질 수 있다면, 그 변경을 제한하거나 확정 시점에 대상 집합을 고정하는 정책도 결정해야 합니다.

**장점 / 현업에서 하는 이유:** 단순 건수 비교가 아니라 동의해야 할 주체 각각을 확인하므로, 탈퇴 회원의 과거 데이터나 중복 데이터가 현재 동의로 오인되는 일을 방지합니다. 동의는 수량보다 “누가 어떤 안건에 응답했는가”를 검증해야 합니다.

## 4. CONSENT의 미응답·거부·응답 시각 구분 — 우선순위 중간

**문제:** DDL에서 `AGREED_YN`은 `NOT NULL`이고 Y/N만 허용합니다. 그런데 동의 요청 생성 Mapper도 미응답 상태를 `'N'`으로 저장하고 `AGREED_AT=SYSDATE`를 기록합니다. 실제 거부와 미응답이 구분되지 않으며, `AGREED_AT`도 실제 응답 시각처럼 보이지만 요청 생성 시점에 채워집니다.

**Before**

```xml
INSERT INTO CONSENT (
    CONSENT_ID, CONSENT_TYPE, TARGET_ID, GROUP_ID, MEMBER_ID, AGREED_YN, AGREED_AT
) VALUES (
    SEQ_CONSENT.NEXTVAL, 'ORDER', #{targetId}, #{groupId}, #{memberId}, 'N', SYSDATE
)
```

**After 제안:** 미응답은 NULL, 응답 결과는 Y(동의)/N(거부)로 구분하고 요청 시각과 응답 시각을 분리합니다. 이는 DDL 변경을 포함하므로 팀 합의와 기존 데이터 이관 계획이 먼저 필요합니다.

```sql
-- 신규 DDL 기준 예시. 기존 DB는 데이터 의미를 확인한 후 마이그레이션
AGREED_YN   CHAR(1),
REQUESTED_AT DATE DEFAULT SYSDATE NOT NULL,
RESPONDED_AT DATE,
CONSTRAINT CK_CONSENT_2 CHECK (AGREED_YN IN ('Y','N'))
```

```xml
<!-- 동의 요청 시: 아직 응답하지 않음 -->
INSERT INTO CONSENT (
    CONSENT_ID, CONSENT_TYPE, TARGET_ID, GROUP_ID, MEMBER_ID, AGREED_YN, REQUESTED_AT
) VALUES (
    SEQ_CONSENT.NEXTVAL, 'ORDER', #{targetId}, #{groupId}, #{memberId}, NULL, SYSDATE
)
```

```xml
<!-- 실제 응답 시각에만 RESPONDED_AT을 기록 -->
UPDATE CONSENT
SET AGREED_YN = #{agreedYn},
    RESPONDED_AT = SYSDATE
WHERE CONSENT_TYPE = 'ORDER'
  AND TARGET_ID = #{targetId}
  AND GROUP_ID = #{groupId}
  AND MEMBER_ID = #{memberId}
  AND AGREED_YN IS NULL
```

**장점 / 현업에서 하는 이유:** 감사·분쟁 대응에서 요청 시각과 실제 응답 결과를 구별할 수 있습니다. 기존 데이터의 N이 거부인지 미응답인지 판별할 수 없다면 일괄 변환하지 말고 이관 규칙부터 정해야 합니다.

## 5. 증빙 납입월 포맷과 파일 암호화 상태 명시 — 우선순위 중간

**납입월:** DDL의 `PAYMENT_PROOF.PAY_MONTH`는 `CHAR(6)`이며 주석상 `YYYYMM`입니다. 백엔드 서비스도 `YYYYMM`을 검증하므로 백엔드와 DB는 일치합니다. 다만 프론트 mock은 `YYYY-MM` 포맷을 사용하므로, 실제 API 연결 시 요청 변환을 명시해야 합니다.

**Before**

```javascript
// frontend mock의 기간 예시: YYYY-MM
const period = String(f.period);
```

**After 제안 — API 경계에서만 변환**

```javascript
const payMonth = String(f.period).replace('-', '');
// 서버 전송 예: 2026-11 -> 202611
```

서버 응답을 화면에 표시할 때는 반대로 변환하거나, API DTO와 화면 모델의 날짜 형식을 명확히 분리하세요.

**파일 암호화:** `PaymentProofService`는 파일 본문을 평문으로 저장하면서 메타에 `ENCRYPTED_YN='N'`을 기록합니다. DDL은 N을 허용하므로 INSERT 오류는 아니지만, 컬럼 설명은 입금 증빙 파일의 AES-GCM 암호화 저장을 전제로 합니다.

```java
// 현재: 실제 파일은 평문 저장
meta.setEncryptedYn("N");
meta.setKeyVer(1);
```

```java
// 권장 방향(개념): 실제 암호화 결과에서 메타를 설정
EncryptedFile encrypted = fileEncryption.encrypt(fileBytes);
writeFile(encrypted.ciphertext());
meta.setEncryptedYn("Y");
meta.setKeyVer(encrypted.keyVersion());
```

`EncryptedFile`은 설명을 위한 개념 타입이며 저장소에 이미 구현돼 있다는 뜻은 아닙니다. 공용 암호화 모듈이 준비되기 전까지는 암호화됐다고 표기하지 말고, 업로드 파일의 접근 통제·보존·삭제 정책도 별도로 정해야 합니다.

**장점 / 현업에서 하는 이유:** 화면 포맷과 DB 포맷을 API 경계에서 변환하면 날짜 문자열 차이가 저장 오류로 번지는 것을 막습니다. 암호화 여부는 설정값이 아니라 실제 저장된 바이트와 일치해야 운영·감사에서 신뢰할 수 있습니다.

## 6. KEY_VER 외래키의 초기 키 데이터 준비 — 우선순위 높음(환경 설정)

**문제:** DDL은 `GYE_ACCOUNT.KEY_VER`와 `PAYMENT_PROOF_FILE.KEY_VER` 등을 `CRYPTO_KEY(KEY_VER)`에 FK로 연결합니다. DDL의 `CRYPTO_KEY` 시드 INSERT는 주석 처리돼 있으므로, 키 버전 1 데이터가 없는 DB에서는 해당 Mapper의 INSERT가 FK 오류로 실패합니다. Mapper가 `KEY_VER=1`을 전달하는 것만으로는 키 행이 생성되지 않습니다.

**Before**

```sql
-- DDL 시드 예시는 주석 처리 상태
-- INSERT INTO CRYPTO_KEY (...) VALUES (1, ...);
```

```java
// 서비스는 버전 1을 기록하지만 DB에 해당 키 행이 있다는 보장은 없음
meta.setKeyVer(1);
```

**After 제안:** 비밀값을 DDL이나 Git에 넣지 말고, 승인된 개발 환경 초기화 절차에서 키를 안전하게 등록한 뒤 버전 1이 존재하는지 확인합니다.

```sql
-- 값은 안전한 키 관리 절차에서 공급. 실제 키/암호문을 저장소에 커밋하지 않음
INSERT INTO CRYPTO_KEY (KEY_VER, ALGORITHM, ENCRYPTED_DEK, KEY_HASH, STATUS)
VALUES (1, 'AES-256-GCM', :encrypted_dek, :key_hash, 'ACTIVE');
```

`CRYPTO_KEY` DDL 설명은 KEK로 래핑한 DEK를 저장하도록 되어 있으므로, 현재 Java 설정에서 직접 Base64 키를 읽는 방식과 키 보관 구조도 팀 공용 설계로 정리해야 합니다.

**장점 / 현업에서 하는 이유:** DB 제약조건과 애플리케이션 키 설정을 함께 준비하면 배포 후 첫 금융정보 저장에서 실패하는 일을 예방할 수 있습니다. 키 원문을 코드·DDL·공유 문서에 넣지 않는 것도 필수입니다.

## 검토 순서 제안

1. 1번 DTO·Mapper 계약을 정리해 백엔드 컴파일을 통과시킵니다.
2. 2~4번 수령순서의 상태 전이와 전원 동의 의미를 확정하고 SQL·DDL·서비스 테스트를 함께 보완합니다.
3. 5~6번 프론트 API 변환, 파일 보호, 키 초기화 절차를 합의합니다.
4. Oracle 통합 테스트에서 모임 생성·증빙 등록·재제출·동의 확정의 정상/중복/경합 케이스를 확인합니다.

이 문서는 DDL과 저장소 소스를 정적으로 대조한 검토입니다. Oracle DB에 연결해 SQL을 실행한 결과는 포함하지 않습니다.
