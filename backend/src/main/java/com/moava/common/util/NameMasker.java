package com.moava.common.util;

/** F03 이름 마스킹: 3자 이상 가운데 ○, 2자 둘째 글자 ○, 1자 이름+○ */
public final class NameMasker {

    private NameMasker() {}

    public static String mask(String name) {
        if (name == null || name.isBlank()) return "회원";
        int n = name.codePointCount(0, name.length());
        int[] cps = name.codePoints().toArray();
        String first = new String(cps, 0, 1);
        if (n == 1) return first + "○";
        if (n == 2) return first + "○";
        String last = new String(cps, n - 1, 1);
        return first + "○".repeat(n - 2) + last;
    }
}
