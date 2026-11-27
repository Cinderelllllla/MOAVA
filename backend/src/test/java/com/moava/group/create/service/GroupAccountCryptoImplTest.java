package com.moava.group.create.service;

import com.moava.admin.groups.dto.AdminGroupAuditDTO;
import com.moava.admin.groups.mapper.AdminGroupAuditMapper;
import com.moava.admin.groups.service.AdminGroupAuditService;
import com.moava.crypto.service.CryptoKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupAccountCryptoImplTest {

    @Mock
    private CryptoKeyService cryptoKeyService;

    @Mock
    private AdminGroupAuditMapper auditMapper;

    private byte[] legacyAesKeyBytes;
    private String legacyAesKeyBase64;
    private String hmacKeyBase64;

    @BeforeEach
    void setUp() {
        legacyAesKeyBytes = new byte[32];
        byte[] hmacBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            legacyAesKeyBytes[i] = (byte) (i + 1);
            hmacBytes[i] = (byte) (i + 65);
        }
        legacyAesKeyBase64 = Base64.getEncoder().encodeToString(legacyAesKeyBytes);
        hmacKeyBase64 = Base64.getEncoder().encodeToString(hmacBytes);
    }

    @Test
    @DisplayName("암호화·복호화·키 버전이 CryptoKeyService로 통일되어 동작한다")
    void encryptAndDecrypt_delegatesToCryptoKeyService() {
        GroupAccountCryptoImpl crypto = new GroupAccountCryptoImpl(cryptoKeyService, legacyAesKeyBase64, hmacKeyBase64);
        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(2);
        when(cryptoKeyService.encrypt("110123456789", 2)).thenReturn("ENC_BY_V2");
        when(cryptoKeyService.decrypt("ENC_BY_V1", 1)).thenReturn("110000001111");

        assertThat(crypto.keyVersion()).isEqualTo(2);
        assertThat(crypto.encrypt("110123456789")).isEqualTo("ENC_BY_V2");
        assertThat(crypto.decrypt("ENC_BY_V1", 1)).isEqualTo("110000001111");

        verify(cryptoKeyService).encrypt("110123456789", 2);
        verify(cryptoKeyService).decrypt("ENC_BY_V1", 1);
    }

    @Test
    @DisplayName("CryptoKeyService 복호화 실패 시 레거시 고정 키로 암호화된 기존 계좌·예금주를 폴백 복호화한다")
    void decrypt_fallsBackToLegacyAesKey() throws Exception {
        GroupAccountCryptoImpl crypto = new GroupAccountCryptoImpl(cryptoKeyService, legacyAesKeyBase64, hmacKeyBase64);
        String legacyCipher = encryptWithLegacyAesKey("홍길동");
        when(cryptoKeyService.decrypt(legacyCipher, 1))
                .thenThrow(new IllegalStateException("GCM tag mismatch"));

        String plain = crypto.decrypt(legacyCipher, 1);

        assertThat(plain).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("CryptoKeyService와 레거시 키 모두 실패하면 내부 정보 노출 없이 예외를 반환한다")
    void decrypt_throwsSanitizedExceptionWhenBothFail() {
        GroupAccountCryptoImpl crypto = new GroupAccountCryptoImpl(cryptoKeyService, "", hmacKeyBase64);
        when(cryptoKeyService.decrypt("BROKEN_CIPHER", 1))
                .thenThrow(new IllegalStateException("tag mismatch secret detail"));

        assertThatThrownBy(() -> crypto.decrypt("BROKEN_CIPHER", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("암호문 복호화에 실패했습니다.");
    }

    @Test
    @DisplayName("관리자 계좌 상세 조회 시 레거시 키로 암호화된 예금주도 GroupAccountCrypto 폴백으로 정상 복호화된다")
    void adminGroupAuditService_decryptsLegacyHolderNameViaFallback() throws Exception {
        GroupAccountCryptoImpl crypto = new GroupAccountCryptoImpl(cryptoKeyService, legacyAesKeyBase64, hmacKeyBase64);
        AdminGroupAuditService auditService = new AdminGroupAuditService(auditMapper, cryptoKeyService, crypto);

        String legacyHolderEnc = encryptWithLegacyAesKey("김철수");
        when(auditMapper.countActiveAdmin(1L)).thenReturn(1);

        AdminGroupAuditDTO.GroupDetailRow detailRow = new AdminGroupAuditDTO.GroupDetailRow();
        detailRow.setGroupId(10L);
        detailRow.setTitle("테스트 모임");
        detailRow.setOwnerId(2L);
        detailRow.setOwnerName("박영희");
        detailRow.setStatus("ACTIVE");
        detailRow.setVisibility("PUBLIC");
        when(auditMapper.selectAdminGroupDetail(10L)).thenReturn(detailRow);

        AdminGroupAuditDTO.GroupAccountRow accountRow = new AdminGroupAuditDTO.GroupAccountRow();
        accountRow.setGyeAccountId(100L);
        accountRow.setBankName("국민은행");
        accountRow.setHolderNameEnc(legacyHolderEnc);
        accountRow.setAccountNoLast4("6789");
        accountRow.setKeyVer(1);
        accountRow.setVerifyStatus("VERIFIED");
        accountRow.setAccountStatus("CURRENT");
        accountRow.setRegisteredByName("박영희");
        when(auditMapper.selectGroupAccountByStatus(10L, "CURRENT")).thenReturn(accountRow);
        when(auditMapper.selectGroupAccountByStatus(10L, "PENDING")).thenReturn(null);

        when(cryptoKeyService.decrypt(legacyHolderEnc, 1))
                .thenThrow(new IllegalStateException("key mismatch"));

        AdminGroupAuditDTO.GroupDetailResponse response = auditService.getGroupDetail(1L, 10L);

        assertThat(response.getCurrentAccount()).isNotNull();
        // 김철수 -> 김○수 (등록자명 박영희 -> 박○희 로 대체되지 않고 실제 예금주 복호화 성공)
        assertThat(response.getCurrentAccount().getMaskedHolderName()).isEqualTo("김○수");
    }

    private String encryptWithLegacyAesKey(String plainText) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(legacyAesKeyBytes, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }
}
