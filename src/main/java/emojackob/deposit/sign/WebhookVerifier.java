package emojackob.deposit.sign;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * 回调（webhook）验签：
 * 负载 = timestamp={X-Notify-Timestamp}&idempotency_key={X-Notify-Idempotency-Key}&<原始 body>
 * 用实例回调公钥（SPKI PEM）验 X-Notify-Signature；时间窗默认 ±5 分钟。
 * 幂等去重以 body 内 data.event_key 为准（不依赖回调头）。
 */
public final class WebhookVerifier {

    public static final long DEFAULT_MAX_AGE_MS = 300_000;

    private final PublicKey publicKey;
    private final long maxAgeMs;

    public WebhookVerifier(String publicKeyPem) throws GeneralSecurityException {
        this(publicKeyPem, DEFAULT_MAX_AGE_MS);
    }

    public WebhookVerifier(String publicKeyPem, long maxAgeMs) throws GeneralSecurityException {
        this.publicKey = Keys.publicKeyFromPem(publicKeyPem);
        this.maxAgeMs = maxAgeMs;
    }

    /** 验签 + 时间窗；任一项不满足返回 false。 */
    public boolean verify(Map<String, String> headers, byte[] body) {
        String ts = header(headers, "X-Notify-Timestamp");
        String idem = header(headers, "X-Notify-Idempotency-Key");
        String sig = header(headers, "X-Notify-Signature");
        if (ts == null || idem == null || sig == null) {
            return false;
        }
        long tsMs;
        try {
            tsMs = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - tsMs) > maxAgeMs) {
            return false;
        }
        String payload = "timestamp=" + ts + "&idempotency_key=" + idem + "&"
                + new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
        try {
            byte[] sigBytes = Base64.getDecoder().decode(sig);
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(publicKey);
            s.update(payload.getBytes(StandardCharsets.UTF_8));
            return s.verify(sigBytes);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }
}
