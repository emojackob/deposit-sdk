package emojackob.deposit.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestSignerTest {

    @Test
    void canonicalParamsSortedAscendingCaseSensitive() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("Address", "x");
        query.put("address", "y");
        String cp = RequestSigner.canonicalParams(query, 1725000000000L, 30000);
        // ASCII 字节序：大写 < 小写；Address < address < recvWindow < timestamp
        assertEquals(
                "Address=x&address=y&recvWindow=30000&timestamp=1725000000000",
                cp);
    }

    @Test
    void canonicalParamsWithoutQueryOnlyHeaderParams() {
        assertEquals(
                "recvWindow=30000&timestamp=1",
                RequestSigner.canonicalParams(Map.of(), 1L, 30000));
    }

    @Test
    void getPayloadMatchesContract() throws Exception {
        KeyPair kp = Keys.generateKeyPair();
        RequestSigner signer = new RequestSigner(kp.getPrivate(), "dnk_test");
        SignedRequest sr = signer.sign(
                "GET",
                "/api/v1/biz/deposits",
                Map.of("address", "0xabc"),
                new byte[0],
                1725000000000L,
                30000);

        assertEquals(
                "GET&/api/v1/biz/deposits&address=0xabc&recvWindow=30000&timestamp=1725000000000"
                        + "&body_hash=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                sr.payload());
        assertEquals("dnk_test", sr.headers().get("X-API-Key"));
        assertEquals("1725000000000", sr.headers().get("X-Timestamp"));
        assertEquals("30000", sr.headers().get("X-Recv-Window"));
        assertTrue(verify(kp, sr));
    }

    @Test
    void postBodyHashBindsRawBody() throws Exception {
        KeyPair kp = Keys.generateKeyPair();
        RequestSigner signer = new RequestSigner(kp.getPrivate(), "dnk_test");
        String body = "{\"project\":\"demo\",\"data\":{\"count\":1}}";
        SignedRequest sr = signer.signPost("/api/v1/biz/addresses", null, body.getBytes());

        assertEquals(
                "POST&/api/v1/biz/addresses&recvWindow=30000&timestamp=" + sr.headers().get("X-Timestamp")
                        + "&body_hash=" + RequestSigner.sha256Hex(body.getBytes()),
                sr.payload());
        assertTrue(verify(kp, sr));
    }

    private static boolean verify(KeyPair kp, SignedRequest sr) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(kp.getPublic());
        s.update(sr.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return s.verify(Base64.getDecoder().decode(sr.headers().get("X-Signature")));
    }
}
