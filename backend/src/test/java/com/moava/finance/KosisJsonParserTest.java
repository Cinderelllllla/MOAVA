package com.moava.finance;

import com.moava.finance.service.KosisJsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KosisJsonParserTest {

    @Test
    void 정상_JSON_배열() {
        var rows = KosisJsonParser.parseRows("[{\"C1_NM\":\"1분위\",\"ITM_NM\":\"소득\",\"DT\":\"1234\"},{\"C1_NM\":\"2분위\",\"DT\":\"5\"}]");
        assertEquals(2, rows.size());
        assertEquals("1분위", rows.get(0).get("C1_NM"));
        assertEquals("1234", rows.get(0).get("DT"));
    }

    @Test
    void 응답_맨_앞에_BOM이_있어도_읽는다() {
        var rows = KosisJsonParser.parseRows("﻿[{\"C1_NM\":\"1분위\",\"DT\":\"7\"}]");
        assertEquals(1, rows.size());
        assertEquals("7", rows.get(0).get("DT"));
    }

    @Test
    void 키에_따옴표가_없어도_읽는다() {
        var rows = KosisJsonParser.parseRows("[{C1_NM:\"3분위\",DT:\"10\",LST:null}]");
        assertEquals("3분위", rows.get(0).get("C1_NM"));
        assertNull(rows.get(0).get("LST"));
    }

    @Test
    void 이스케이프_문자() {
        var rows = KosisJsonParser.parseRows("[{\"A\":\"a\\\"b\\u0041\"}]");
        assertEquals("a\"bA", rows.get(0).get("A"));
    }

    @Test
    void 빈_배열() {
        assertTrue(KosisJsonParser.parseRows(" [ ] ").isEmpty());
    }

    @Test
    void KOSIS_오류_객체는_예외() {
        var e = assertThrows(IllegalStateException.class,
                () -> KosisJsonParser.parseRows("{\"err\":\"20\",\"errMsg\":\"필수요청변수값이 누락되었습니다.\"}"));
        assertTrue(e.getMessage().contains("err=20"));
    }

    @Test
    void 깨진_응답은_예외() {
        assertThrows(IllegalStateException.class, () -> KosisJsonParser.parseRows("<html>error</html>"));
        assertThrows(IllegalStateException.class, () -> KosisJsonParser.parseRows("[{\"A\":\"x\""));
        assertThrows(IllegalStateException.class, () -> KosisJsonParser.parseRows(""));
    }
}
