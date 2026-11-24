package com.moava.openbanking;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.openbanking.codef.CodefAccountMapper;
import com.moava.openbanking.codef.CodefApiClient;
import com.moava.openbanking.codef.CodefBankCode;
import com.moava.openbanking.codef.CodefBankService;
import com.moava.openbanking.codef.CodefCryptoUtil;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.codef.CodefProperties;
import com.moava.openbanking.service.KftcSecurityGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodefBankServiceTest {

    @Mock
    private CodefApiClient codefApiClient;
    @Mock
    private CodefAccountMapper mapper;
    @Mock
    private CryptoKeyService cryptoKeyService;

    private ObjectMapper objectMapper;
    private KeyPair testRsaKeyPair;
    private CodefProperties properties;
    private CodefBankService service;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testRsaKeyPair = kpg.generateKeyPair();
        String pubKeyBase64 = Base64.getEncoder().encodeToString(testRsaKeyPair.getPublic().getEncoded());

        properties = new CodefProperties(
                "https://oauth.codef.io",
                "https://development.codef.io",
                "demo-client-id",
                "demo-client-secret",
                pubKeyBase64
        );
        service = new CodefBankService(codefApiClient, properties, mapper, cryptoKeyService, new KftcSecurityGuard());
    }

    @Test
    void rsa_공개키_암호화후_개인키로_원문_복호화_일치() throws Exception {
        String encrypted = CodefCryptoUtil.encryptRsa("mySecretPw!123", properties.getPublicKey());
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, testRsaKeyPair.getPrivate());
        String decrypted = new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
        assertEquals("mySecretPw!123", decrypted);
    }

    @Test
    void 은행코드_기관코드_KFTC코드_한글명_별칭_모두_매핑된다() {
        assertEquals(CodefBankCode.KB, CodefBankCode.resolve("0004"));
        assertEquals(CodefBankCode.KB, CodefBankCode.resolve("004"));
        assertEquals(CodefBankCode.KB, CodefBankCode.resolve("국민은행"));
        assertEquals(CodefBankCode.KB, CodefBankCode.resolve("KB국민"));
        assertEquals(CodefBankCode.SHINHAN, CodefBankCode.resolve("신한"));
        assertThrows(CodefException.class, () -> CodefBankCode.resolve("없는은행"));
    }

    @Test
    void 공동인증서_연결_성공_및_간편인증_명확한_안내로_차단() throws Exception {
        // 1) 간편인증(loginType=5) 요청 시 은행 API 미지원 안내 반환
        CodefDto.ConnectRequest simpleAuthReq = new CodefDto.ConnectRequest();
        simpleAuthReq.setOrganization("국민은행");
        simpleAuthReq.setLoginType("5");

        CodefException simpleEx = assertThrows(
                CodefException.class,
                () -> service.connectBankAccount(10L, simpleAuthReq, "127.0.0.1")
        );
        assertEquals("CODEF_SIMPLE_AUTH_UNSUPPORTED", simpleEx.getCode());

        // 2) 공동인증서(loginType=0, DER+KEY) 정상 연결 및 connectedId 암호화 저장
        JsonNode dataNode = objectMapper.readTree("""
                {"connectedId":"conn_test_998877","successList":[{"organization":"0004"}],"errorList":[]}
                """);
        when(codefApiClient.postApi(eq("/v1/account/create"), anyMap())).thenReturn(dataNode);
        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(1);
        when(cryptoKeyService.encrypt("conn_test_998877", 1)).thenReturn("ENC_CONN_ID");

        CodefDto.ConnectRequest certReq = new CodefDto.ConnectRequest();
        certReq.setOrganization("국민은행");
        certReq.setLoginType("0");
        certReq.setDerFile("BASE64_DER_DATA");
        certReq.setKeyFile("BASE64_KEY_DATA");
        certReq.setPassword("cert-password-123");

        CodefDto.ConnectResponse res = service.connectBankAccount(10L, certReq, "127.0.0.1");
        assertTrue(res.isConnected());
        assertEquals("0004", res.getOrganization());
        assertEquals("국민은행", res.getBankName());

        // 전송된 비밀번호가 평문이 아닌 RSA 암호문인지 검증
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(codefApiClient).postApi(eq("/v1/account/create"), payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accountList = (List<Map<String, Object>>) payloadCaptor.getValue().get("accountList");
        assertNotEquals("cert-password-123", accountList.get(0).get("password"));
        assertEquals("0", accountList.get(0).get("loginType"));

        ArgumentCaptor<CodefDto.ConnectionRecord> recCaptor = ArgumentCaptor.forClass(CodefDto.ConnectionRecord.class);
        verify(mapper).upsertConnection(recCaptor.capture());
        assertEquals("ENC_CONN_ID", recCaptor.getValue().getConnectedIdEnc());
    }

    @Test
    void 실계좌인증_예금주_일치시_인증저장_불일치시_403_차단() throws Exception {
        CodefDto.ConnectionRecord conn = new CodefDto.ConnectionRecord();
        conn.setMemberId(10L);
        conn.setOrganization("0088");
        conn.setConnectedIdEnc("ENC_CONN");
        conn.setKeyVer(1);

        when(mapper.findActiveMemberName(10L)).thenReturn("홍길동");
        when(mapper.findConnection(10L, "0088")).thenReturn(conn);
        when(cryptoKeyService.decrypt("ENC_CONN", 1)).thenReturn("conn_real_123");

        // 1) 예금주 일치 -> 인증 완료
        JsonNode matchedData = objectMapper.readTree("""
                {
                  "resAccountHolder": "홍길동",
                  "resAccountBalance": "1250000",
                  "resTrHistoryList": [
                    {
                      "resAccountTrDate": "20261124",
                      "resAccountTrTime": "101500",
                      "resAccountIn": "50000",
                      "resAccountOut": "0",
                      "resAfterTranBalance": "1250000",
                      "resAccountDesc1": "모임회비"
                    }
                  ]
                }
                """);
        when(codefApiClient.postApi(eq("/v1/kr/bank/p/account/transaction-list"), anyMap())).thenReturn(matchedData);
        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(1);
        when(cryptoKeyService.encrypt("110123456789", 1)).thenReturn("ENC_ACC");

        CodefDto.VerifyAccountRequest req = new CodefDto.VerifyAccountRequest();
        req.setOrganization("신한은행");
        req.setAccount("110-123-456789");

        CodefDto.VerifyAccountResponse res = service.verifyRealAccount(10L, req, "127.0.0.1");
        assertTrue(res.isVerified());
        assertEquals("홍길동", res.getHolderName());
        assertEquals("6789", res.getAccountLast4());
        assertEquals(1_250_000L, res.getBalanceAmt());
        assertEquals(1, res.getRecentTransactionCount());
        verify(mapper).markAccountVerified(eq(10L), eq("0088"), eq("ENC_ACC"), eq("6789"), eq("홍길동"), eq(1), any());

        // 2) 예금주 불일치 -> 403 차단
        JsonNode mismatchData = objectMapper.readTree("""
                {"resAccountHolder": "타인명의", "resAccountBalance": "10000", "resTrHistoryList": []}
                """);
        when(codefApiClient.postApi(eq("/v1/kr/bank/p/account/transaction-list"), anyMap())).thenReturn(mismatchData);

        CodefException mismatchEx = assertThrows(
                CodefException.class,
                () -> service.verifyRealAccount(10L, req, "127.0.0.1")
        );
        assertEquals("CODEF_HOLDER_MISMATCH", mismatchEx.getCode());
        assertEquals(403, mismatchEx.getHttpStatus().value());
    }

    @Test
    void 거래내역조회_저장된_인증계좌_자동사용_및_XSS_이스케이프() throws Exception {
        CodefDto.ConnectionRecord conn = new CodefDto.ConnectionRecord();
        conn.setMemberId(10L);
        conn.setOrganization("0088");
        conn.setConnectedIdEnc("ENC_CONN");
        conn.setVerifiedAccountEnc("ENC_ACC");
        conn.setKeyVer(1);

        when(mapper.findConnection(10L, "0088")).thenReturn(conn);
        when(cryptoKeyService.decrypt("ENC_CONN", 1)).thenReturn("conn_real_123");
        when(cryptoKeyService.decrypt("ENC_ACC", 1)).thenReturn("110123456789");

        JsonNode txData = objectMapper.readTree("""
                {
                  "resAccountHolder": "홍길동",
                  "resAccountBalance": "900000",
                  "commStartDate": "20261024",
                  "commEndDate": "20261124",
                  "resTrHistoryList": [
                    {
                      "resAccountTrDate": "20261123",
                      "resAccountTrTime": "093000",
                      "resAccountIn": "0",
                      "resAccountOut": "35000",
                      "resAfterTranBalance": "900000",
                      "resAccountDesc1": "<script>alert(1)</script>"
                    }
                  ]
                }
                """);
        when(codefApiClient.postApi(eq("/v1/kr/bank/p/account/transaction-list"), anyMap())).thenReturn(txData);

        CodefDto.TransactionRequest req = new CodefDto.TransactionRequest();
        req.setOrganization("0088");
        CodefDto.TransactionListResponse res = service.fetchTransactions(10L, req, "127.0.0.1");

        assertEquals(1, res.getCount());
        assertEquals("출금", res.getTransactions().get(0).getInoutType());
        assertEquals(35_000L, res.getTransactions().get(0).getTranAmt());
        assertFalse(res.getTransactions().get(0).getDesc1().contains("<script>"));
    }

    @Test
    void codef_URL인코딩_응답_디코딩_파싱_및_XML_매퍼_계약검증() throws Exception {
        // URL-Encoded JSON 파싱 검증
        String rawJson = "{\"result\":{\"code\":\"CF-00000\",\"message\":\"성공\"},\"data\":{\"resAccountHolder\":\"홍길동\"}}";
        String urlEncoded = URLEncoder.encode(rawJson, StandardCharsets.UTF_8);

        CodefApiClient client = new CodefApiClient(properties, objectMapper);
        Method parseMethod = CodefApiClient.class.getDeclaredMethod("parseJsonLenient", String.class);
        parseMethod.setAccessible(true);
        JsonNode parsed = (JsonNode) parseMethod.invoke(client, urlEncoded);
        assertEquals("CF-00000", parsed.path("result").path("code").asString());
        assertEquals("홍길동", parsed.path("data").path("resAccountHolder").asString());

        // MyBatis XML ↔ CodefAccountMapper 메서드 일치 검증
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("mapper/openbanking/CodefAccountMapper.xml")) {
            assertNotNull(is, "CodefAccountMapper.xml을 찾을 수 없습니다.");
            String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Pattern idPattern = Pattern.compile("<(?:select|insert|update|delete)\\s+id=\"([^\"]+)\"");
            Matcher m = idPattern.matcher(xml);
            Set<String> xmlIds = new HashSet<>();
            while (m.find()) {
                xmlIds.add(m.group(1));
            }
            for (Method method : CodefAccountMapper.class.getDeclaredMethods()) {
                assertTrue(xmlIds.contains(method.getName()), "XML 누락 메서드: " + method.getName());
            }
        }
    }
}
