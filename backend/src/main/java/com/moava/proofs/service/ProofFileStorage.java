package com.moava.proofs.service;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.proofs.dto.PaymentProofFileDto;
import com.moava.proofs.dto.ProofFileContent;
import com.moava.proofs.mapper.PaymentProofMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 증빙 파일 처리 전담 (검증 · 해시 · 암호화 저장 · 복호화 읽기 · 삭제)
 * - 트랜잭션은 호출하는 서비스 메서드의 @Transactional을 따름 (여기에는 @Transactional을 붙이지 않음)
 */
@Component
@RequiredArgsConstructor
public class ProofFileStorage {

    private final PaymentProofMapper paymentProofMapper;
    private final CryptoKeyService cryptoKeyService;   // 증빙 파일 암호화 (공통 암호화 모듈, 문자열 API 사용)

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
     * 파일 개수·크기·MIME·매직 넘버 검사 + SHA-256 해시 중복 검사 → 해시 목록 반환 (등록·재제출 공통)
     *
     * @param excludeProofId 재제출 시 본인 증빙의 기존 파일은 중복 검사에서 제외 (등록 시 null)
     */
    public List<String> validate(List<MultipartFile> files, Long excludeProofId) {
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

    /**
     * 파일을 암호화해 UUID 이름으로 저장 + PAYMENT_PROOF_FILE INSERT (등록·재제출 공통)
     * 암호화: 파일 바이트 → Base64 문자열 → 공통 암호화(AES-256-GCM) → 저장 (명세 F22-10)
     * 해시·크기는 암호화 전 원본 기준 (DDL: FILE_HASH·FILE_SIZE는 원본 기준)
     * 중간에 실패하면 이미 쓴 파일은 지우고 예외를 다시 던짐 → DB는 호출한 쪽 @Transactional로 롤백
     */
    public void save(Long proofId, List<MultipartFile> files, List<String> hashes) {
        Path dir = Paths.get(uploadDir, "proofs").toAbsolutePath().normalize();
        List<Path> savedFiles = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            int keyVer = cryptoKeyService.getCurrentKeyVersion();   // 현재 ACTIVE 키 버전 (KEY_VER FK)
            for (int i = 0; i < files.size(); i++) {
                MultipartFile f = files.get(i);
                String storedName = UUID.randomUUID() + ".enc";   // 암호문이라 원래 확장자 대신 .enc
                Path target = dir.resolve(storedName);

                String encrypted = cryptoKeyService.encrypt(
                        Base64.getEncoder().encodeToString(f.getBytes()), keyVer);
                Files.writeString(target, encrypted, StandardCharsets.US_ASCII);
                savedFiles.add(target);

                PaymentProofFileDto meta = new PaymentProofFileDto();
                meta.setPaymentProofId(proofId);
                meta.setSortNo(i + 1);
                meta.setFilePath("proofs/" + storedName);
                meta.setOriginName(originName(f));
                meta.setMimeType(f.getContentType());
                meta.setFileSize(f.getSize());
                meta.setFileHash(hashes.get(i));
                meta.setEncryptedYn("Y");
                meta.setKeyVer(keyVer);
                paymentProofMapper.insertProofFile(meta);
            }
            // 여기까지 성공해도 이후 DB 작업·커밋이 실패해 롤백되면 새로 쓴 파일을 지움
            afterRollback(() -> deleteQuietly(savedFiles));
        } catch (IOException e) {
            deleteQuietly(savedFiles);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 중 오류가 발생했습니다.", e);
        } catch (RuntimeException e) {
            deleteQuietly(savedFiles);
            throw e;
        }
    }

    /** 디스크에서 읽어 복호화 (저장 때의 역순: 암호문 → Base64 → 원본 바이트). 업로드 폴더 밖 경로 차단 */
    public ProofFileContent read(PaymentProofFileDto file) {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(file.getFilePath()).normalize();
        if (!target.startsWith(base) || !Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽을 수 없습니다.");
        }

        try {
            byte[] bytes;
            if ("Y".equals(file.getEncryptedYn())) {
                String encrypted = Files.readString(target, StandardCharsets.US_ASCII);
                bytes = Base64.getDecoder().decode(cryptoKeyService.decrypt(encrypted, file.getKeyVer()));
            } else {
                bytes = Files.readAllBytes(target);   // 암호화 적용 전에 올라온 파일
            }
            String fileName = "proof-" + file.getFileId() + ALLOWED_MIME.getOrDefault(file.getMimeType(), "");
            return new ProofFileContent(bytes, file.getMimeType(), fileName);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽을 수 없습니다.", e);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 복호화에 실패했습니다.", e);
        }
    }

    /** 기존 디스크 파일 삭제 — DB 커밋이 성공한 뒤에만 (업로드 폴더 밖 경로는 건너뜀, 재제출용) */
    public void deleteAfterCommit(List<String> oldPaths) {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        List<Path> oldFiles = new ArrayList<>();
        for (String p : oldPaths) {
            Path target = base.resolve(p).normalize();
            if (target.startsWith(base)) {   // "../" 등으로 업로드 폴더를 벗어난 경로 차단
                oldFiles.add(target);
            }
        }
        afterCommit(() -> deleteQuietly(oldFiles));
    }

    // ---------- 내부 도우미 ----------

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
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
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
            case "image/jpeg" -> startsWith(head, 0, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "application/pdf" -> startsWith(head, 0, 0x25, 0x50, 0x44, 0x46);              // "%PDF"
            case "image/webp" -> startsWith(head, 0, 0x52, 0x49, 0x46, 0x46)                // "RIFF"
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

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}