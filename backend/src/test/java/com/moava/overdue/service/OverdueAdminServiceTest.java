package com.moava.overdue.service;

import com.moava.common.util.NameMasker;
import com.moava.overdue.dto.OverdueCaseDto;
import com.moava.overdue.mapper.OverdueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OverdueAdminService 단위 테스트 (F26 관리자 미납 목록 A06)
 */
@ExtendWith(MockitoExtension.class)
class OverdueAdminServiceTest {

    @Mock
    OverdueMapper overdueMapper;

    @InjectMocks
    OverdueAdminService overdueAdminService;

    static final Long ADMIN_ID = 1L;

    @Test
    @DisplayName("목록: ACTIVE 관리자면 조회하고 회원 이름은 마스킹")
    void list_masksName() {
        when(overdueMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);
        OverdueCaseDto row = new OverdueCaseDto();
        row.setMemberName("홍길동");
        when(overdueMapper.findOverdueCases("OPEN")).thenReturn(new ArrayList<>(List.of(row)));

        List<OverdueCaseDto> res = overdueAdminService.getCases(ADMIN_ID, "OPEN");

        assertEquals(NameMasker.mask("홍길동"), res.get(0).getMemberName());
    }

    @Test
    @DisplayName("ACTIVE 관리자가 아니면 403, 조회 안 함")
    void notAdmin_403() {
        when(overdueMapper.countActiveAdmin(ADMIN_ID)).thenReturn(0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.getCases(ADMIN_ID, "OPEN"));

        assertEquals(403, e.getStatusCode().value());
        verify(overdueMapper, never()).findOverdueCases(any());
    }

    @Test
    @DisplayName("status가 OPEN/RESOLVED/ALL이 아니면 400")
    void wrongStatus_400() {
        when(overdueMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> overdueAdminService.getCases(ADMIN_ID, "DETECTED"));

        assertEquals(400, e.getStatusCode().value());
        verify(overdueMapper, never()).findOverdueCases(any());
    }
}