package com.moava.overdue.service;

import com.moava.common.util.NameMasker;
import com.moava.overdue.dto.OverdueCaseDto;
import com.moava.overdue.mapper.OverdueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * F26 관리자 미납 관리 (화면 A06 /admin/overdue) — 목록. 조치(경고·채팅 제한·해제·후순위 검토)는 다음 단계
 */
@Service
@RequiredArgsConstructor
public class OverdueAdminService {

    private static final Set<String> STATUS_FILTERS = Set.of("OPEN", "RESOLVED", "ALL");

    private final OverdueMapper overdueMapper;

    /** A06 목록 — status: OPEN(미해소) / RESOLVED(해소) / ALL */
    @Transactional(readOnly = true)
    public List<OverdueCaseDto> getCases(Long adminId, String status) {
        checkAdmin(adminId);
        if (status == null || !STATUS_FILTERS.contains(status)) {   // Set.of는 null을 넣으면 예외라 먼저 확인
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status는 OPEN / RESOLVED / ALL 중 하나여야 합니다.");
        }
        List<OverdueCaseDto> cases = overdueMapper.findOverdueCases(status);
        cases.forEach(c -> c.setMemberName(NameMasker.mask(c.getMemberName())));
        return cases;
    }

    // ACTIVE 관리자만 (비활성 관리자의 토큰이 만료 전이어도 차단, F41-4)
    private void checkAdmin(Long adminId) {
        if (adminId == null || overdueMapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 이용할 수 있습니다.");
        }
    }
}