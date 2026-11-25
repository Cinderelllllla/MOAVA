package com.moava.notification.common.sms;

/**
 * 문자 발송 창구. 실제 구현은 SolapiSmsSender.
 * 테스트에서는 가짜 구현으로 바꿔 끼운다.
 */
public interface SmsSender {

    /**
     * @param to   수신 번호 (숫자만, 예: 01012345678)
     * @param text 본문 (90바이트를 넘으면 SOLAPI가 LMS로 자동 전환)
     * @return 발송 요청 성공 여부. 실패 원인은 구현체가 로그로 남긴다 (번호는 남기지 않는다)
     */
    boolean send(String to, String text);

    /** 키·발신번호가 설정돼 있어 실제 발송이 가능한지 */
    boolean isConfigured();
}
