package com.moava.finance.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KOSIS 통계자료 응답(JSON 배열)을 [{필드명: 값}] 목록으로 바꾼다. 외부 라이브러리 없이 동작.
 * - KOSIS 는 키에 따옴표가 없는 JSON 을 줄 때가 있어서, 따옴표 없는 키·값도 받아 준다.
 * - 오류일 때 KOSIS 는 {"err":"20","errMsg":"..."} 객체를 주므로 예외로 바꾼다.
 */
public final class KosisJsonParser {

    private final String s;
    private int pos;

    private KosisJsonParser(String s) {
        this.s = s;
    }

    public static List<Map<String, String>> parseRows(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("KOSIS 응답이 비어 있습니다.");
        }
        String body = text.strip();
        if (body.startsWith("﻿")) {
            body = body.substring(1);
        }
        if (body.startsWith("{")) {
            Map<String, String> obj = new KosisJsonParser(body).object();
            throw new IllegalStateException("KOSIS 오류 응답 err=" + obj.get("err") + ", errMsg=" + obj.get("errMsg"));
        }
        if (!body.startsWith("[")) {
            throw new IllegalStateException("KOSIS 응답이 JSON 배열이 아닙니다: " + preview(body));
        }
        return new KosisJsonParser(body).array();
    }

    private List<Map<String, String>> array() {
        expect('[');
        List<Map<String, String>> rows = new ArrayList<>();
        skipWs();
        if (peek() == ']') {
            pos++;
            return rows;
        }
        while (true) {
            rows.add(object());
            skipWs();
            char c = next();
            if (c == ']') {
                return rows;
            }
            if (c != ',') {
                throw error("',' 또는 ']' 가 와야 합니다");
            }
        }
    }

    private Map<String, String> object() {
        expect('{');
        Map<String, String> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            String key = token();
            expect(':');
            String value = token();
            map.put(key, value);
            skipWs();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("',' 또는 '}' 가 와야 합니다");
            }
        }
    }

    /** 따옴표 문자열 또는 따옴표 없는 값(숫자·null·키). */
    private String token() {
        skipWs();
        char c = peek();
        if (c == '"' || c == '\'') {
            return quoted();
        }
        if (c == '{' || c == '[') {
            throw error("중첩된 값은 지원하지 않습니다");
        }
        int start = pos;
        while (pos < s.length() && ",:}]".indexOf(s.charAt(pos)) < 0 && !Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        String raw = s.substring(start, pos);
        if (raw.isEmpty()) {
            throw error("값이 없습니다");
        }
        return "null".equals(raw) ? null : raw;
    }

    private String quoted() {
        char quote = next();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length()) {
                throw error("문자열이 닫히지 않았습니다");
            }
            char c = s.charAt(pos++);
            if (c == quote) {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (pos + 4 > s.length()) {
                        throw error("잘못된 \\u 이스케이프");
                    }
                    sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> sb.append(e); // \" \\ \/ \'
            }
        }
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= s.length()) {
            throw error("응답이 중간에 끝났습니다");
        }
        return s.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        skipWs();
        if (next() != c) {
            throw error("'" + c + "' 가 와야 합니다");
        }
    }

    private IllegalStateException error(String message) {
        return new IllegalStateException("KOSIS 응답 형식 오류(" + pos + "번째 글자): " + message);
    }

    private static String preview(String body) {
        return body.length() > 80 ? body.substring(0, 80) + "..." : body;
    }
}
