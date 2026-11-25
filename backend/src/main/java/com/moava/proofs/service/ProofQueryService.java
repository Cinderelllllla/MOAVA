package com.moava.proofs.service;

import com.moava.proofs.dto.PaymentStatusResponse;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.moava.common.util.NameMasker;
import com.moava.proofs.dto.PaymentProofDetailDto;
import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofFileDto;
import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.dto.ProofFileContent;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.mapper.PaymentProofMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * F22 증빙 조회 (목록 · 상세 · 파일 미리보기/다운로드)
 */
@Service
@RequiredArgsConstructor

public class ProofQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PaymentProofMapper paymentProofMapper;
    private final ProofFileStorage proofFileStorage;
    private final ProofAccessChecker proofAccessChecker;

    /** F22-01 증빙 목록 (모임장: 모임 전체 / ACTIVE 구성원: 본인 것만) */
    @Transactional(readOnly = true)
    public List<PaymentProofListDto> getProofList(Long groupId, Long memberId) {
        // 1. 모임 존재 + 권한
        ProofGroupDto group = proofAccessChecker.getGroupOrThrow(groupId);
        boolean isOwner = proofAccessChecker.checkOwnerOrMember(group, memberId, "모임 구성원만 증빙 목록을 볼 수 있습니다.");

        List<PaymentProofListDto> list =
                paymentProofMapper.findProofList(groupId, isOwner ? null : memberId);

        // 2. 타인 이름 마스킹 (F03-2)
        for (PaymentProofListDto p : list) {
            if (!memberId.equals(p.getMemberId())) {
                p.setMemberName(NameMasker.mask(p.getMemberName()));
            }
        }
        return list;
    }

    /** F22 증빙 상세 조회 (모임장: 모임 전체 / ACTIVE 구성원: 본인 것만 — 목록과 같은 기준) */
    @Transactional(readOnly = true)
    public PaymentProofDetailDto getProofDetail(Long groupId, Long proofId, Long memberId) {
        // 1. 모임 존재 + 권한 (모임장 또는 ACTIVE 구성원)
        ProofGroupDto group = proofAccessChecker.getGroupOrThrow(groupId);
        boolean isOwner = proofAccessChecker.checkOwnerOrMember(group, memberId, "모임 구성원만 증빙을 볼 수 있습니다.");

        // 2. 증빙 존재 (이 모임의 증빙인지까지 확인)
        PaymentProofDetailDto detail = paymentProofMapper.findProofDetail(groupId, proofId);
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 증빙입니다.");
        }

        // 3. 모임장이 아니면 본인 증빙만
        if (!isOwner && !memberId.equals(detail.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 제출한 증빙만 볼 수 있습니다.");
        }

        // 4. 타인 이름 마스킹 (F03-2) + 첨부 파일 요약
        if (!memberId.equals(detail.getMemberId())) {
            detail.setMemberName(NameMasker.mask(detail.getMemberName()));
        }
        detail.setFiles(paymentProofMapper.findProofFileSummaries(proofId));
        detail.setFileCount(detail.getFiles().size());   // 상속받은 fileCount가 null로 나가지 않게
        return detail;
    }

    /**
     * F22 증빙 파일 미리보기·다운로드
     * 권한: 증빙 작성자·모임장 (API 명세 v0.5 §9 보수적 안 A안, 팀 결정 대기 — B안이면 2번 조건만 변경)
     */
    @Transactional(readOnly = true)
    public ProofFileContent getProofFile(Long groupId, Long proofId, Long fileId, Long memberId) {
        // 1. 모임·증빙 존재 (이 모임의 증빙인지까지 확인)
        ProofGroupDto group = proofAccessChecker.getGroupOrThrow(groupId);
        PaymentProofDto proof = proofAccessChecker.getProofOrThrow(groupId, proofId);

        // 2. 권한: 모임장 또는 증빙 작성자
        if (!memberId.equals(group.getOwnerId()) && !memberId.equals(proof.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "증빙 작성자와 모임장만 파일을 볼 수 있습니다.");
        }

        // 3. 파일 메타 (이 증빙에 속한 파일인지까지 확인)
        PaymentProofFileDto file = paymentProofMapper.findProofFile(proofId, fileId);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 파일입니다.");
        }

        // 4. 디스크에서 읽어 복호화
        return proofFileStorage.read(file);
    }

    /**
     * F25 계원별 납입 현황 (본인 납입 후 열람)
     * - 귀속 월마다 따로 판단: 그 달 내 증빙이 CONFIRMED이거나 모임장이면 전체 회원 상태, 아니면 본인 행만
     * - 경고 횟수는 모임장만, 회원 이름은 실명 표시, 증빙 파일은 넣지 않음
     * - 내 증빙 내역은 기존 F22 목록(getProofList)이 회원에게 본인 것만 주므로 여기서 다시 주지 않음
     *
     * @param payMonth 조회할 귀속 월 YYYYMM (null이면 이번 달)
     */
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Long groupId, Long memberId, String payMonth) {
        // 1. 모임 존재 + 권한 (모임장 또는 ACTIVE 구성원)
        ProofGroupDto group = proofAccessChecker.getGroupOrThrow(groupId);
        boolean isOwner = proofAccessChecker.checkOwnerOrMember(group, memberId, "모임 구성원만 납입 현황을 볼 수 있습니다.");

        // 2. 귀속 월 (없으면 이번 달)
        String month = (payMonth == null || payMonth.isBlank())
                ? YearMonth.now(SEOUL).format(DateTimeFormatter.ofPattern("yyyyMM"))
                : payMonth.trim();
        if (!month.matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "귀속 월 형식이 올바르지 않습니다. (YYYYMM)");
        }

        // 3. 그 달 회원별 납입 상태 → 잠금 판단 (모임장이거나 그 달 내 증빙이 승인 완료)
        List<PaymentStatusResponse.MemberStatus> members = paymentProofMapper.findMemberPaymentStatus(groupId, month);
        boolean iPaid = members.stream()
                .anyMatch(m -> memberId.equals(m.getMemberId()) && "CONFIRMED".equals(m.getStatus()));
        boolean locked = !isOwner && !iPaid;
        if (locked) {
            members = members.stream().filter(m -> memberId.equals(m.getMemberId())).toList();   // 본인 행만
        }

        // 4. 본인 표시 · 경고 횟수는 모임장만
        for (PaymentStatusResponse.MemberStatus m : members) {
            m.setMe(memberId.equals(m.getMemberId()));

            if (!isOwner) {
                m.setWarningCount(null);
            }
        }

        return new PaymentStatusResponse(month, locked, members);
    }
}