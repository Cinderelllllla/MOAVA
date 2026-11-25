package com.moava.references.service;

import com.moava.common.util.NameMasker;
import com.moava.references.dto.RefNoRequest;
import com.moava.references.dto.RefNoResponse;
import com.moava.references.dto.RefProofDto;
import com.moava.references.mapper.RefNoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

/**
 * F24 거래 참조번호 지정 · 정정 (관리자, 화면 A05 /admin/references)
 * - CONFIRMED(모임장 승인) 증빙에만 'MOAVA-00001' 형식 번호 지정
 * - 잘못 지정한 번호는 사유를 남기고 정정 (이전 값·사유는 AUDIT_LOG에 보존)
 */
@Service
@RequiredArgsConstructor
public class RefNoService {

    private final RefNoMapper refNoMapper;

    private static final String REF_NO_PATTERN = "^MOAVA-\\d{5}$";
    private static final int MAX_REASON_BYTES = 600;     // 한글 약 200자 (AUDIT_LOG.DESCRIPTION 1000바이트 안에 들어가도록)
    private static final int MAX_NEXT_TRIES = 10;        // 수동 입력 번호와 겹치면 다음 번호로 넘어가는 최대 횟수
    // 계좌번호·연락처처럼 숫자 10개 이상 연속(사이 하이픈·공백 1개 허용) → 사유 입력 금지 (AUDIT_LOG.DESCRIPTION에 계좌·연락처 기록 금지)
    private static final Pattern LONG_NUMBER = Pattern.compile("(\\d[\\s-]?){10,}");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** A05 목록 — ref: UNASSIGNED(미지정) / ASSIGNED(지정 완료) */
    @Transactional(readOnly = true)
    public List<RefProofDto> getProofs(Long adminId, String ref) {
        checkAdmin(adminId);
        if (!"UNASSIGNED".equals(ref) && !"ASSIGNED".equals(ref)) {
            throw badRequest("ref는 UNASSIGNED 또는 ASSIGNED만 가능합니다.");
        }
        List<RefProofDto> list = refNoMapper.findProofs("ASSIGNED".equals(ref));
        for (RefProofDto p : list) {
            p.setMemberName(NameMasker.mask(p.getMemberName()));   // 타인 이름 마스킹 (F03)
        }
        return list;
    }

    /** 다음 참조번호 제안 — 시퀀스를 소모하지 않고 미리 보기만 함 */
    @Transactional(readOnly = true)
    public RefNoResponse suggestRefNo(Long adminId) {
        checkAdmin(adminId);
        return RefNoResponse.suggested(refNoMapper.peekNextRefNo());
    }

    /**
     * 참조번호 지정
     * - req.refNo가 비어 있으면 서버가 다음 번호(SEQ_REF_NO)로 지정, 있으면 그 번호(수동 입력)로 지정
     */
    @Transactional
    public RefNoResponse assignRefNo(Long adminId, Long proofId, RefNoRequest req) {
        checkAdmin(adminId);

        // 1. 증빙 존재 + 승인(CONFIRMED) 건만 + 아직 번호 없음
        RefProofDto proof = getProofOrThrow(proofId);
        if (!"CONFIRMED".equals(proof.getStatus())) {
            throw conflict("모임장이 승인한 증빙에만 참조번호를 지정할 수 있습니다.");
        }
        if (proof.getRefNo() != null) {
            throw conflict("이미 참조번호가 지정된 증빙입니다. 정정을 이용해 주세요.");
        }

        // 2. 번호 결정 (수동 입력이면 형식·중복 검사, 아니면 다음 번호 발급)
        String manual = req == null ? null : trimToNull(req.getRefNo());
        String refNo = manual != null ? checkNewRefNo(manual) : issueNextRefNo();

        // 3. 저장 (조건부 UPDATE → 0건이면 그 사이에 상태가 바뀌었거나 다른 요청이 먼저 지정한 것)
        LocalDateTime assignedAt = LocalDateTime.now(SEOUL).withNano(0);
        try {
            if (refNoMapper.assignRefNo(proofId, refNo, assignedAt) == 0) {
                throw conflict("이미 처리된 증빙입니다.");
            }
        } catch (DuplicateKeyException e) {
            throw conflict("이미 사용 중인 참조번호입니다.");   // 중복 검사 직후 같은 번호가 동시에 들어온 경우
        }

        // 4. 감사 로그
        refNoMapper.insertAdminAuditLog(proof.getGroupId(), adminId, proof.getMemberId(), "REF_ASSIGN",
                "참조번호 지정 #" + proofId + " (" + refNo + ")");

        return RefNoResponse.assigned(proofId, refNo, assignedAt.atZone(SEOUL).toOffsetDateTime());
    }

    /** 참조번호 정정 — 새 번호·사유 필수, 이전 번호와 사유는 감사 로그(REF_CORRECT)에 보존 */
    @Transactional
    public RefNoResponse correctRefNo(Long adminId, Long proofId, RefNoRequest req) {
        checkAdmin(adminId);

        // 1. 입력값: 새 번호 + 사유
        String newRefNo = req == null ? null : trimToNull(req.getRefNo());
        String reason = req == null ? null : trimToNull(req.getReason());
        if (newRefNo == null) {
            throw badRequest("새 참조번호를 입력해 주세요.");
        }
        if (reason == null) {
            throw badRequest("정정 사유를 입력해 주세요.");
        }
        if (reason.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES) {
            throw badRequest("정정 사유는 한글 기준 약 200자까지 입력할 수 있습니다.");
        }
        if (LONG_NUMBER.matcher(reason).find()) {                                         // ← 추가
            throw badRequest("정정 사유에 계좌번호·연락처 같은 긴 숫자는 입력할 수 없습니다.");
        }

        // 2. 증빙 존재 + 번호가 지정된 건만 + 현재 번호와 달라야 함
        RefProofDto proof = getProofOrThrow(proofId);
        String oldRefNo = proof.getRefNo();
        if (oldRefNo == null) {
            throw conflict("참조번호가 지정되지 않은 증빙입니다.");
        }
        if (oldRefNo.equals(newRefNo)) {
            throw badRequest("현재 참조번호와 다른 번호를 입력해 주세요.");
        }
        checkNewRefNo(newRefNo);

        // 3. 저장 (현재 번호 조건 UPDATE → 0건이면 다른 요청이 먼저 정정한 것)
        try {
            if (refNoMapper.correctRefNo(proofId, oldRefNo, newRefNo) == 0) {
                throw conflict("이미 처리된 증빙입니다.");
            }
        } catch (DuplicateKeyException e) {
            throw conflict("이미 사용 중인 참조번호입니다.");
        }

        // 4. 감사 로그 (이전 값·사유 보존)
        refNoMapper.insertAdminAuditLog(proof.getGroupId(), adminId, proof.getMemberId(), "REF_CORRECT",
                "참조번호 정정 #" + proofId + " (" + oldRefNo + " → " + newRefNo + ", 사유: " + reason + ")");

        return RefNoResponse.corrected(proofId, newRefNo, proof.getRefCorrectCount() + 1);
    }

    // ---------- 내부 도우미 ----------

    // ACTIVE 관리자만 (비활성 관리자의 토큰이 만료 전이어도 차단, F41-4)
    private void checkAdmin(Long adminId) {
        if (adminId == null || refNoMapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 이용할 수 있습니다.");
        }
    }

    private RefProofDto getProofOrThrow(Long proofId) {
        RefProofDto proof = refNoMapper.findProof(proofId);
        if (proof == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 증빙입니다.");
        }
        return proof;
    }

    // 수동 입력 번호: 형식(MOAVA-00001) + 중복 검사
    private String checkNewRefNo(String refNo) {
        if (!refNo.matches(REF_NO_PATTERN)) {
            throw badRequest("참조번호는 MOAVA-00001 형식이어야 합니다.");
        }
        if (refNoMapper.countRefNo(refNo) > 0) {
            throw conflict("이미 사용 중인 참조번호입니다.");
        }
        return refNo;
    }

    // 다음 번호 발급 — 관리자가 손으로 넣은 번호와 겹치면 다음 번호로 넘어감
    private String issueNextRefNo() {
        for (int i = 0; i < MAX_NEXT_TRIES; i++) {
            String refNo = refNoMapper.nextRefNo();
            if (refNoMapper.countRefNo(refNo) == 0) {
                return refNo;
            }
        }
        throw conflict("사용 가능한 참조번호를 찾지 못했습니다. 번호를 직접 입력해 주세요.");
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}