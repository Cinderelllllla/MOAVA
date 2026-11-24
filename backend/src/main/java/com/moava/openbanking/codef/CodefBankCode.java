package com.moava.openbanking.codef;

import java.util.List;
import java.util.Locale;

/**
 * CODEF 국내 은행 4자리 기관코드 ↔ 은행명 ↔ KFTC 3자리 코드 매핑
 */
public enum CodefBankCode {

    KDB("0002", "002", "산업은행", List.of("산업", "kdb")),
    IBK("0003", "003", "기업은행", List.of("기업", "ibk", "ibk기업은행")),
    KB("0004", "004", "국민은행", List.of("국민", "kb", "kb국민은행", "kb국민")),
    SH("0007", "007", "수협은행", List.of("수협", "sh수협은행")),
    NH("0011", "011", "농협은행", List.of("농협", "nh", "nh농협", "nh농협은행")),
    WOORI("0020", "020", "우리은행", List.of("우리")),
    SC("0023", "023", "SC제일은행", List.of("sc", "sc제일", "제일은행")),
    CITI("0027", "027", "씨티은행", List.of("씨티", "한국씨티은행")),
    IM("0031", "031", "iM뱅크(대구)", List.of("대구은행", "대구", "im뱅크", "dgb")),
    BUSAN("0032", "032", "부산은행", List.of("부산", "bnk부산은행")),
    GWANGJU("0034", "034", "광주은행", List.of("광주")),
    JEJU("0035", "035", "제주은행", List.of("제주")),
    JEONBUK("0037", "037", "전북은행", List.of("전북")),
    KYONGNAM("0039", "039", "경남은행", List.of("경남", "bnk경남은행")),
    MG("0045", "045", "새마을금고", List.of("새마을", "mg새마을금고")),
    CU("0048", "048", "신협", List.of("신협은행", "신용협동조합")),
    POST("0071", "071", "우체국", List.of("우체국은행")),
    HANA("0081", "081", "하나은행", List.of("하나", "keb하나은행")),
    SHINHAN("0088", "088", "신한은행", List.of("신한")),
    KBANK("0089", "089", "케이뱅크", List.of("k뱅크", "kbank")),
    KAKAOBANK("0090", "090", "카카오뱅크", List.of("카카오", "kakaobank")),
    TOSSBANK("0092", "092", "토스뱅크", List.of("토스", "tossbank"));

    private final String codefOrgCode;
    private final String kftcCode;
    private final String bankName;
    private final List<String> aliases;

    CodefBankCode(String codefOrgCode, String kftcCode, String bankName, List<String> aliases) {
        this.codefOrgCode = codefOrgCode;
        this.kftcCode = kftcCode;
        this.bankName = bankName;
        this.aliases = aliases;
    }

    public String getCodefOrgCode() {
        return codefOrgCode;
    }

    public String getKftcCode() {
        return kftcCode;
    }

    public String getBankName() {
        return bankName;
    }

    /**
     * 4자리 CODEF 기관코드, 3자리 KFTC 은행코드, 또는 은행명(별칭 포함)으로 매핑
     */
    public static CodefBankCode resolve(String rawCodeOrName) {
        if (rawCodeOrName == null || rawCodeOrName.isBlank()) {
            throw CodefException.badRequest("CODEF_INVALID_BANK", "은행 코드(organization) 또는 은행명은 필수입니다.");
        }
        String trimmed = rawCodeOrName.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        for (CodefBankCode bank : values()) {
            if (bank.codefOrgCode.equals(trimmed)
                    || bank.kftcCode.equals(trimmed)
                    || bank.bankName.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").equals(lower)) {
                return bank;
            }
            for (String alias : bank.aliases) {
                if (alias.equals(lower)) {
                    return bank;
                }
            }
        }
        throw CodefException.badRequest("CODEF_UNSUPPORTED_BANK", "지원하지 않는 은행입니다: " + trimmed);
    }
}
