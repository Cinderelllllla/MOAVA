package com.moava.group.account.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroupAccountBankCodeResolverTest {

    @Test
    void 설정된_은행명은_정확한_은행코드로_변환() {

        // 테스트 전용 가상 은행 설정
        GroupAccountBankCodeResolver resolver =
                new GroupAccountBankCodeResolver(
                        "테스트은행=001,다른테스트은행=002"
                );

        assertEquals(
                "001",
                resolver.resolve("테스트은행")
        );

        assertEquals(
                "002",
                resolver.resolve("다른테스트은행")
        );
    }

    @Test
    void 등록되지_않은_은행은_차단() {

        GroupAccountBankCodeResolver resolver =
                new GroupAccountBankCodeResolver(
                        "테스트은행=001"
                );

        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve("미등록은행")
        );
    }

    @Test
    void 설정이_없으면_은행코드조회_차단() {

        GroupAccountBankCodeResolver resolver =
                new GroupAccountBankCodeResolver("");

        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve("테스트은행")
        );
    }

    @Test
    void 잘못된_은행코드설정은_거부() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new GroupAccountBankCodeResolver(
                        "테스트은행=INVALID"
                )
        );
    }

    @Test
    void 중복된_은행명설정은_거부() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new GroupAccountBankCodeResolver(
                        "테스트은행=001,테스트은행=002"
                )
        );
    }
}
