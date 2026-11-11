package com.moava.notification.common.domain;

/**
 * 알림 코드표 v3 (MOAVA_알림코드표_v3.xlsx, 2026-11-09 프론트 대조 반영: 21개).
 *
 * <p>이동 경로는 프론트 라우트 기준. 모임 화면은 ?groupId= 로 선택 모임을 넘긴다.
 *
 * <p>제목·본문·이동 경로의 {키}는 호출 시 넘기는 params 값으로 바뀐다.
 * 다른 회원 이름(member)은 넘기기 전에 NameMasker.mask()로 가린다.
 *
 * <p>모임 전체 공지성 정보(공개 설정·규칙 변경, 곗돈 수령 기록 등)는 알림이 아니라 채팅방 시스템 메시지로 남긴다.
 */
public enum NotifType {

    // ── 유저 ──────────────────────────────────────────────────
    /** result: 승인 / 거절 / 만료 */
    JOIN_RESULT(Receiver.MEMBER, "가입 신청 결과 안내", "{group} 가입 신청이 {result}됐어요.", "/my-groups", false),
    /** item: 계좌 변경 / 모임장 승계 / 모임장 승계 수락 / 수령 순서 변경 / 탈퇴 정산안 */
    CONSENT_REQ(Receiver.MEMBER, "동의가 필요해요", "{group} {item} 동의 요청이 왔어요. {deadline}까지 선택해 주세요.", "/procedures?groupId={groupId}", false),
    /** result: 확정 / 부결 / 만료, detail: 추가 안내 (없으면 "") */
    CONSENT_RESULT(Receiver.MEMBER, "{item} 결과 안내", "{group} {item} 요청이 {result}됐어요. {detail}", "/procedures?groupId={groupId}", false),
    PROOF_REVISION(Receiver.MEMBER, "증빙 보완이 필요해요", "{group} {round}회차 증빙 보완 요청: {reason}", "/evidence?groupId={groupId}", false),
    PAY_DUE_3D(Receiver.MEMBER, "납입일이 3일 남았어요", "{group} {month} 납입일은 {payDate}이에요. 약정액 {amount}원.", "/evidence?groupId={groupId}", true),
    /** 미납 1일 차·5일 차 두 번만. guide: 단계별 안내 문구 */
    PAY_OVERDUE(Receiver.MEMBER, "납입이 확인되지 않았어요", "{group} {month} 납입이 {days}일째 확인되지 않았어요. {guide}", "/evidence?groupId={groupId}", true),
    /** scope: 모임명 또는 "MOAVA", action: 조치 내용 + 사유 */
    SANCTION(Receiver.MEMBER, "이용 제한 안내", "[{scope}] {action} 문의·이의 신청은 고객센터로 해 주세요.", "/support", false),
    WITHDRAW_DONE(Receiver.MEMBER, "탈퇴가 처리됐어요", "{group} 탈퇴가 정산 확인 후 처리됐어요.", "/my-groups", false),
    DISSOLVED(Receiver.MEMBER, "모임이 해산됐어요", "{group} 해산이 승인됐어요. 정산 안내를 확인해 주세요.", "/my-groups", false),
    REPORT_STATUS(Receiver.MEMBER, "신고 처리 상태가 바뀌었어요", "접수하신 신고가 '{status}'(으)로 변경됐어요.", "/support", false),
    RISK_UP(Receiver.MEMBER, "납입 부담 신호가 높아졌어요", "최근 금융 정보 기준으로 납입 부담이 커졌어요. 추천 납입액을 다시 확인해 보세요.", "/finance", true),

    // ── 유저(모임장) ──────────────────────────────────────────
    JOIN_REQUEST(Receiver.LEADER, "새 가입 신청이 있어요", "{member}님이 {group} 가입을 신청했어요. 7일 안에 처리하지 않으면 자동 만료돼요.", "/operations?groupId={groupId}", false),
    /** 같은 모임 하루 1건 */
    PROOF_PENDING(Receiver.LEADER, "확인할 납입 증빙이 있어요", "{group}에 새 납입 증빙이 올라왔어요. 증빙 화면에서 확인해 주세요.", "/evidence?groupId={groupId}", false),
    /** stage: 신청 / 수정 */
    ORDER_REQUEST(Receiver.LEADER, "수령 순서 신청이 왔어요", "{member}님이 {group} {round}회차 수령을 {stage}했어요. 검토 후 동의 요청을 보내 주세요.", "/procedures?groupId={groupId}", false),
    /** 관리자가 계좌 소유 확인을 마침 → 모임장 */
    ACCOUNT_VERIFIED(Receiver.LEADER, "계좌 소유 확인이 끝났어요", "{group} 모임 계좌({bank} ****{last4}) 소유 확인이 완료됐어요.", "/operations?groupId={groupId}", false),
    /** 해산 신청 반려 → 모임장 */
    DISSOLVE_REJECTED(Receiver.LEADER, "해산 신청이 반려됐어요", "{group} 해산 신청이 반려됐어요. 사유: {reason}", "/operations?groupId={groupId}", false),
    /** stage: 신청 / 재확인 요청(2회째) */
    WITHDRAW_REQUEST(Receiver.LEADER, "탈퇴 신청이 왔어요", "{member}님의 {group} 탈퇴 {stage}이(가) 있어요. 정산안을 등록해 주세요.", "/procedures?groupId={groupId}", false),

    // ── 관리자 ────────────────────────────────────────────────
    ACCOUNT_VERIFY_REQ(Receiver.ADMIN, "계좌 소유 확인 요청", "{group} 모임 계좌({bank} ****{last4})의 소유 확인이 필요해요.", "/admin/groups", false),
    OVERDUE_5D(Receiver.ADMIN, "미납 회원이 감지됐어요", "{group} · {member} · {month} 미납 {days}일 차예요.", "/admin/overdue", false),
    DISSOLVE_REQUEST(Receiver.ADMIN, "해산 승인 검토 요청", "{group} 해산 신청이 접수됐어요. 사유: {reason}", "/admin/dissolve", false),
    REPORT_RECEIVED(Receiver.ADMIN, "새 신고가 접수됐어요", "[{category}] {group} 관련 신고가 접수됐어요.", "/admin/support", false);

    /** 수신 구분: 유저 / 유저(모임장) / 관리자 */
    public enum Receiver { MEMBER, LEADER, ADMIN }

    private final Receiver receiver;
    private final String titleTemplate;
    private final String bodyTemplate;
    private final String linkTemplate;
    private final boolean sms;

    NotifType(Receiver receiver, String titleTemplate, String bodyTemplate, String linkTemplate, boolean sms) {
        this.receiver = receiver;
        this.titleTemplate = titleTemplate;
        this.bodyTemplate = bodyTemplate;
        this.linkTemplate = linkTemplate;
        this.sms = sms;
    }

    public Receiver receiver() { return receiver; }
    public String titleTemplate() { return titleTemplate; }
    public String bodyTemplate() { return bodyTemplate; }
    public String linkTemplate() { return linkTemplate; }
    /** F36 SMS 동시 발송 대상 여부 */
    public boolean sms() { return sms; }
}
