package com.moava.proofs.service;

import com.moava.proofs.dto.PaymentProofCreateRequest;
import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofFileDto;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.dto.PaymentProofResubmitRequest;
import com.moava.proofs.mapper.PaymentProofMapper;
import com.moava.proofs.dto.PaymentProofReviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.moava.common.util.NameMasker;


@Service
@RequiredArgsConstructor
public class PaymentProofService {

    private final PaymentProofMapper paymentProofMapper;

    @Value("${moava.upload.dir}")
    private String uploadDir;

    private static final int MAX_FILES = 5;
    private static final long MAX_FILE_SIZE = 1024 * 1024;   // 1MB (DDL CHECK와 동일)
    private static final Map<String, String> ALLOWED_MIME = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "application/pdf", ".pdf");

    /**
     * F22 납입 증빙 등록
     * @param memberId 로그인 회원 ID (JWT 연동 전까지는 Controller에서 임시로 받음)
     * @return 생성된 PAYMENT_PROOF_ID
     */
    @Transactional
    public Long registerProof(Long memberId, PaymentProofCreateRequest req, List<MultipartFile> files) {

        // 1. 필수값 · 귀속 월 형식
        if (memberId == null || req.getGroupId() == null || req.getPayMonth() == null
                || req.getRoundNo() == null || req.getAmount() == null || req.getPayDate() == null) {
            throw badRequest("필수 입력값이 누락되었습니다.");
        }
        if (!req.getPayMonth().matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw badRequest("납입 귀속 월 형식이 올바르지 않습니다. (YYYYMM)");
        }

        // 2. 모임 존재 + 해산 완료 모임 차단
        ProofGroupDto group = getGroupOrThrow(req.getGroupId());
        if ("DISSOLVED".equals(group.getStatus())) {
            throw badRequest("해산된 모임에는 증빙을 등록할 수 없습니다.");
        }

        // 3. 참여 회원만 (REQ-SEC-04 서버 재검증)
        if (paymentProofMapper.countActiveMember(req.getGroupId(), memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "참여 회원만 증빙을 등록할 수 있습니다.");
        }

        // 4. 회차 범위 1 ~ MONTHS
        if (req.getRoundNo() < 1 || req.getRoundNo() > group.getMonths()) {
            throw badRequest("납입 회차는 1 ~ " + group.getMonths() + " 사이여야 합니다.");
        }

        // 5. 약정액 일치 (F22-02)
        if (!req.getAmount().equals(group.getAmount())) {
            throw badRequest("모임 약정 납입액과 일치해야 합니다.");
        }

        // 6~7. 파일 개수·형식·크기·매직 넘버 + 해시 중복 검사 (재제출과 공통)
        List<String> hashes = validateFiles(files, null);

        // 8. 같은 모임·회원·귀속 월·회차 중복 제출 차단 (F22-03)
        if (paymentProofMapper.countDuplicateProof(
                req.getGroupId(), memberId, req.getPayMonth(), req.getRoundNo()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "해당 모임·귀속 월·회차에 이미 제출한 증빙이 있습니다.");
        }

        // 9. PAYMENT_PROOF 저장 (STATUS=PENDING) → selectKey로 ID가 채워짐
        PaymentProofDto proof = new PaymentProofDto();
        proof.setGroupId(req.getGroupId());
        proof.setMemberId(memberId);
        proof.setPayMonth(req.getPayMonth());
        proof.setRoundNo(req.getRoundNo());
        proof.setAmount(req.getAmount());
        proof.setPayDate(req.getPayDate());

        try {
            paymentProofMapper.insertProof(proof);
        } catch (DuplicateKeyException e) {
            // 8번 검사 직후 같은 증빙이 동시에 들어온 경우 (UK_PAYMENT_PROOF_1 위반)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "해당 모임·귀속 월·회차에 이미 제출한 증빙이 있습니다.");
        }

        // 10. 파일 저장 + 메타 INSERT (재제출과 공통)
        saveFiles(proof.getPaymentProofId(), files, hashes);

        // TODO: 모임장 알림(F35 NotificationService), AUDIT_LOG(PROOF_UPLOAD) — 공통 모듈 머지 후 연결
        return proof.getPaymentProofId();
    }

    // ---------- 내부 도우미 ----------

    // 파일 개수·크기·MIME·매직 넘버 검사 + SHA-256 해시 중복 검사 → 해시 목록 반환 (등록·재제출 공통)
    private List<String> validateFiles(List<MultipartFile> files, Long excludeProofId) {
        if (files == null || files.isEmpty() || files.size() > MAX_FILES) {
            throw badRequest("증빙 파일은 1~5개까지 등록할 수 있습니다.");
        }
        for (MultipartFile f : files) {
            if (f.isEmpty() || f.getSize() > MAX_FILE_SIZE) {
                throw badRequest("파일은 1MB 이하만 등록할 수 있습니다.");
            }
            String type = f.getContentType();
            if (type == null || !ALLOWED_MIME.containsKey(type)) {   // Map.of는 containsKey(null)에서 NPE → null 먼저 검사
                throw badRequest("JPG, PNG, WebP, PDF 파일만 등록할 수 있습니다.");
            }
            if (!hasValidSignature(f, type)) {
                throw badRequest("파일 내용이 선택한 형식과 일치하지 않습니다.");
            }
        }

        List<String> hashes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MultipartFile f : files) {
            String hash = sha256(f);
            if (!seen.add(hash)) {
                throw badRequest("동일한 파일을 중복으로 첨부할 수 없습니다.");
            }
            if (paymentProofMapper.countFileHash(hash, excludeProofId) > 0) {
                throw badRequest("이미 등록된 증빙 파일입니다.");
            }
            hashes.add(hash);
        }
        return hashes;
    }

    // 파일을 UUID 이름으로 저장 + PAYMENT_PROOF_FILE INSERT (등록·재제출 공통)
    // 중간에 실패하면 이미 쓴 파일은 지우고 예외를 다시 던짐 → DB는 @Transactional로 롤백
    private void saveFiles(Long proofId, List<MultipartFile> files, List<String> hashes) {
        Path dir = Paths.get(uploadDir, "proofs").toAbsolutePath().normalize();
        List<Path> savedFiles = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            for (int i = 0; i < files.size(); i++) {
                MultipartFile f = files.get(i);
                String storedName = UUID.randomUUID() + ALLOWED_MIME.get(f.getContentType());
                Path target = dir.resolve(storedName);
                f.transferTo(target);
                savedFiles.add(target);

                PaymentProofFileDto meta = new PaymentProofFileDto();
                meta.setPaymentProofId(proofId);
                meta.setSortNo(i + 1);
                meta.setFilePath("proofs/" + storedName);
                meta.setOriginName(originName(f));
                meta.setMimeType(f.getContentType());
                meta.setFileSize(f.getSize());
                meta.setFileHash(hashes.get(i));
                meta.setEncryptedYn("N");   // TODO: AES-256-GCM 암호화 모듈 붙이면 'Y'
                meta.setKeyVer(1);
                paymentProofMapper.insertProofFile(meta);
            }
            // 여기까지 성공해도 이후 DB 작업·커밋이 실패해 롤백되면 새로 쓴 파일을 지움
            afterRollback(() -> deleteQuietly(savedFiles));
        } catch (IOException e) {                          // ← 기존 189번째 줄
            deleteQuietly(savedFiles);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다.", e);
        } catch (RuntimeException e) {
            deleteQuietly(savedFiles);
            throw e;
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }


    // 모임 조회 — 없으면 404 (등록·목록·심사 공통)
    private ProofGroupDto getGroupOrThrow(Long groupId) {
        ProofGroupDto group = paymentProofMapper.findGroupForProof(groupId);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
        return group;
    }

    // 증빙 조회 (경로의 groupId 소속인 것만) — 없으면 404 (심사·재제출 공통)
    private PaymentProofDto getProofOrThrow(Long groupId, Long proofId) {
        PaymentProofDto proof = paymentProofMapper.findProofForReview(groupId, proofId);
        if (proof == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 증빙입니다.");
        }
        return proof;
    }


    // 트랜잭션이 커밋된 뒤에만 실행. 트랜잭션 밖이면 실행하지 않음 (파일은 지우는 것보다 남기는 쪽이 안전)
    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        }
    }

    // 트랜잭션이 롤백되면 실행 (커밋 실패 포함). 커밋 결과를 알 수 없는 경우(UNKNOWN)는 실행하지 않음
    private void afterRollback(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        task.run();
                    }
                }
            });
        }
    }

    private String sha256(MultipartFile f) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(f.getBytes()));   // 64자 소문자 hex
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 해시 계산 실패", e);
        }
    }

    private String originName(MultipartFile f) {
        String name = f.getOriginalFilename() == null ? "unknown" : f.getOriginalFilename();
        return name.length() > 255 ? name.substring(0, 255) : name;   // ORIGIN_NAME VARCHAR2(255)
    }

    private void deleteQuietly(List<Path> paths) {
        for (Path p : paths) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
        }
    }
    // 파일 앞부분(매직 넘버)으로 실제 형식 확인 — Content-Type 위조 차단 (REQ-SEC-02)
    private boolean hasValidSignature(MultipartFile f, String type) {
        byte[] head;
        try (InputStream in = f.getInputStream()) {
            head = in.readNBytes(12);   // WebP 판별에 12바이트 필요
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 확인 중 오류가 발생했습니다.", e);
        }
        return switch (type) {
            case "image/jpeg"      -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
            case "image/png"       -> startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "application/pdf" -> startsWith(head, 0, 0x25, 0x50, 0x44, 0x46);              // "%PDF"
            case "image/webp"      -> startsWith(head, 0, 0x52, 0x49, 0x46, 0x46)                // "RIFF"
                    && startsWith(head, 8, 0x57, 0x45, 0x42, 0x50);              // "WEBP"
            default -> false;
        };
    }

    // data의 offset 위치부터 sig 바이트들과 일치하는지
    private boolean startsWith(byte[] data, int offset, int... sig) {
        if (data.length < offset + sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if ((data[offset + i] & 0xFF) != sig[i]) return false;
        }
        return true;
    }
    @Transactional(readOnly = true)
    public List<PaymentProofListDto> getProofList(Long groupId, Long memberId) {
        // 1. 모임 존재 확인 (등록 때 만든 findGroupForProof 재사용)
        ProofGroupDto group = getGroupOrThrow(groupId);

        // 2. 권한: 모임장이면 전체, ACTIVE 구성원이면 본인 것만
        boolean isOwner = memberId.equals(group.getOwnerId());
        if (!isOwner && paymentProofMapper.countActiveMember(groupId, memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "모임 구성원만 증빙 목록을 볼 수 있습니다.");
        }

        List<PaymentProofListDto> list =
                paymentProofMapper.findProofList(groupId, isOwner ? null : memberId);

        // 3. 타인 이름 마스킹 (F03-2)
        for (PaymentProofListDto p : list) {
            if (!memberId.equals(p.getMemberId())) {
                p.setMemberName(NameMasker.mask(p.getMemberName()));
            }
        }
        return list;
    }

    /**
     * F23 증빙 승인·보완 요청 (모임장)
     * @param memberId 로그인 회원 ID (JWT 연동 전까지는 Controller에서 임시로 받음)
     * @return 변경된 상태 (CONFIRMED / REVISION)
     */
    @Transactional
    public String reviewProof(Long memberId, Long groupId, Long proofId, PaymentProofReviewRequest req) {

        // 1. 결정 값 확인
        String decision = req.getDecision();
        if (!"CONFIRMED".equals(decision) && !"REVISION".equals(decision)) {
            throw badRequest("심사 결과는 CONFIRMED 또는 REVISION만 가능합니다.");
        }

        // 2. 모임 존재 + 모임장 확인 (REQ-SEC-04 서버 재검증)
        ProofGroupDto group = getGroupOrThrow(groupId);
        if (!group.getOwnerId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임장만 승인할 수 있어요.");
        }

        // 3. 증빙 존재 (이 모임의 증빙인지까지 확인)
        PaymentProofDto proof = getProofOrThrow(groupId, proofId);

        // 4. 확인 대기(PENDING) 상태만 심사 가능
        if (!"PENDING".equals(proof.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확인 대기 중인 증빙만 심사할 수 있습니다.");
        }

        // 5. 보완 요청이면 사유 필수, 승인이면 사유 저장 안 함
        String memo = null;
        if ("REVISION".equals(decision)) {
            if (req.getReviewMemo() == null || req.getReviewMemo().isBlank()) {
                throw badRequest("보완 사유를 입력해 주세요.");
            }
            memo = req.getReviewMemo().trim();
            if (memo.length() > 500) {   // DDL REVIEW_MEMO VARCHAR2(500)
                throw badRequest("보완 사유는 500자 이하로 입력해 주세요.");
            }
        }

        // 6. 상태 변경 (PENDING 조건 UPDATE → 0건이면 그 사이에 다른 요청이 먼저 처리한 것)
        int updated = paymentProofMapper.updateProofReview(proofId, decision, memberId, memo);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 증빙입니다.");
        }

        // TODO F23: 승인 시 같은 모임·회원·귀속 월 OVERDUE_CASE → RESOLVED (F26 미납 감지와 함께 구현)
        // TODO F23: 회원 알림(NOTIFICATION), 감사 로그(AUDIT_LOG PROOF_CONFIRM / PROOF_REVISION)

        return decision;
    }

    /**
     * F22-05 보완 재제출 (증빙 작성자)
     * - REVISION 상태만 가능. 귀속 월·회차·금액은 기존 값 유지, 납입일·파일만 교체
     * - 본인 증빙의 기존 파일은 다시 첨부 가능 (다른 증빙 파일 재사용은 계속 차단)
     * @return 재제출 후 누적 횟수
     */
    @Transactional
    public int resubmitProof(Long memberId, Long groupId, Long proofId,
                             PaymentProofResubmitRequest req, List<MultipartFile> files) {

        // 1. 필수값
        if (memberId == null || req == null || req.getPayDate() == null) {
            throw badRequest("필수 입력값이 누락되었습니다.");
        }

        // 2. 증빙 존재 (이 모임의 증빙인지까지 확인)
        PaymentProofDto proof = getProofOrThrow(groupId, proofId);

        // 3. 작성자 본인만
        if (!memberId.equals(proof.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 제출한 증빙만 재제출할 수 있습니다.");
        }

        // 4. 보완 요청(REVISION) 상태만
        if (!"REVISION".equals(proof.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "보완 요청 상태의 증빙만 재제출할 수 있습니다.");
        }

        // 5. 새 파일 검사 (본인 증빙의 기존 파일은 해시 중복 검사에서 제외)
        List<String> hashes = validateFiles(files, proofId);

        // 6. 상태·납입일·재제출 횟수 갱신 (REVISION 조건 UPDATE → 0건이면 다른 요청이 먼저 처리한 것)
        if (paymentProofMapper.updateProofResubmit(proofId, req.getPayDate()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 증빙입니다.");
        }

        // 7. 기존 파일 메타 삭제 → 새 파일 저장 (SORT_NO는 1부터 다시)
        List<String> oldPaths = paymentProofMapper.findProofFilePaths(proofId);
        paymentProofMapper.deleteProofFiles(proofId);
        saveFiles(proofId, files, hashes);

        // 8. 기존 디스크 파일 삭제 — DB 커밋이 성공한 뒤에만 (업로드 폴더 밖 경로는 건너뜀)
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        List<Path> oldFiles = new ArrayList<>();
        for (String p : oldPaths) {
            Path target = base.resolve(p).normalize();
            if (target.startsWith(base)) {   // "../" 등으로 업로드 폴더를 벗어난 경로 차단
                oldFiles.add(target);
            }
        }
        afterCommit(() -> deleteQuietly(oldFiles));

        // TODO: 모임장 알림(F35), AUDIT_LOG(PROOF_RESUBMIT) — 공통 모듈 머지 후 연결
        return proof.getResubmitCount() + 1;
    }



}