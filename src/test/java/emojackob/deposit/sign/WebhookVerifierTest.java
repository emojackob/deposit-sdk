package emojackob.deposit.sign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookVerifierTest {

    @Test
    void verifyOk() throws Exception {
        KeyPair kp = Keys.generateKeyPair();
        WebhookVerifier verifier = new WebhookVerifier(KeysTest.toPublicPem(kp.getPublic()));
        String ts = String.valueOf(System.currentTimeMillis());
        String body = "{\"project\":\"demo\",\"data\":{\"event_key\":\"deposit:137:0xabc:3\"}}";
        String payload = "timestamp=" + ts + "&idempotency_key=deposit-12&" + body;
        String sig = sign(payload, kp.getPrivate());

        assertTrue(verifier.verify(
                Map.of(
                        "X-Notify-Timestamp", ts,
                        "X-Notify-Idempotency-Key", "deposit-12",
                        "X-Notify-Signature", sig),
                body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void tamperedBodyFails() throws Exception {
        KeyPair kp = Keys.generateKeyPair();
        WebhookVerifier verifier = new WebhookVerifier(KeysTest.toPublicPem(kp.getPublic()));
        String ts = String.valueOf(System.currentTimeMillis());
        String body = "{\"project\":\"demo\",\"data\":{\"event_key\":\"deposit:137:0xabc:3\"}}";
        String payload = "timestamp=" + ts + "&idempotency_key=deposit-12&" + body;
        String sig = sign(payload, kp.getPrivate());

        assertFalse(verifier.verify(
                Map.of(
                        "X-Notify-Timestamp", ts,
                        "X-Notify-Idempotency-Key", "deposit-12",
                        "X-Notify-Signature", sig),
                "{\"tampered\":true}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void expiredTimestampFails() throws Exception {
        KeyPair kp = Keys.generateKeyPair();
        WebhookVerifier verifier = new WebhookVerifier(KeysTest.toPublicPem(kp.getPublic()));
        String ts = String.valueOf(System.currentTimeMillis() - 10 * 60 * 1000);
        String body = "{}";
        String payload = "timestamp=" + ts + "&idempotency_key=k&" + body;
        String sig = sign(payload, kp.getPrivate());

        assertFalse(verifier.verify(
                Map.of(
                        "X-Notify-Timestamp", ts,
                        "X-Notify-Idempotency-Key", "k",
                        "X-Notify-Signature", sig),
                body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sign(String payload, java.security.PrivateKey privateKey) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(privateKey);
        s.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(s.sign());
    }
}
