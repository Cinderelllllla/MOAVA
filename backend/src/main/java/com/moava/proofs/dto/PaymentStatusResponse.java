package com.moava.proofs.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * F25 계원별 납입 현황 응답 (운영 홈 > 모임 정보 > 납입 현황)
 * - locked=true면 members에는 본인 행만 들어 있음 → 화면에 '🔒 본인 납입 후 열람'
 * - 증빙 파일은 넣지 않음 (이체 확인증의 계좌번호·예금주 노출 방지)
 * - 내 증빙 내역은 기존 F22 목록 API(GET /payment-proofs)가 본인 것만 주므로 여기 중복으로 넣지 않음
 */
@Getter
@AllArgsConstructor
public class PaymentStatusResponse {
    private String payMonth;                // 조회한 귀속 월 YYYYMM
    private boolean locked;                 // 다른 회원 현황 잠금 여부
    private List<MemberStatus> members;     // 회원별 그 달 납입 상태

    /** 납입 현황 표의 회원 1명 — 이 응답 안에서만 쓰여서 중첩 클래스로 둠 */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MemberStatus {
        @JsonIgnore
        private Long memberId;          // 서버 판단용 (본인 여부·잠금). 다른 회원 ID는 응답에 넣지 않음
        private String memberName;      // 본인 외에는 마스킹 (F03)
        private String status;          // CONFIRMED / PENDING / REVISION / NONE(미제출)
        private Integer warningCount;   // 경고 횟수 — 모임장에게만 (아니면 null → 응답에서 빠짐)
        private boolean me;             // 본인 행인지
    }
}