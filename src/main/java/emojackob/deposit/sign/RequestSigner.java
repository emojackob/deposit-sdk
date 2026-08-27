package emojackob.deposit.sign;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入站业务接口请求签名（SigV4 风格）：
 * payload = METHOD & /path & canonical_params & body_hash=sha256_hex(raw_body)
 * canonical_params = 业务 query + recvWindow + timestamp，先 RFC3986 编码、再按 key 升序排序。
 */
public final class RequestSigner {

    public static final int DEFAULT_RECV_WINDOW_MS = 30_000;
    private static final String EMPTY_BODY_HASH =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private final PrivateKey privateKey;
    private final String apiKey;

    public RequestSigner(PrivateKey privateKey, String apiKey) {
        this.privateKey = privateKey;
        this.apiKey = apiKey;
    }

    /** GET 请求签名（无 body）。 */
    public SignedRequest signGet(String path, Map<String, String> query) {
        return sign("GET", path, query, new byte[0], System.currentTimeMillis(), DEFAULT_RECV_WINDOW_MS);
    }

    /** POST 请求签名（body 为原始 UTF-8 字节）。 */
    public SignedRequest signPost(String path, Map<String, String> query, byte[] body) {
        byte[] b = body == null ? new byte[0] : body;
        return sign("POST", path, query, b, System.currentTimeMillis(), DEFAULT_RECV_WINDOW_MS);
    }

    /** 完整签名入口（可指定时间戳/窗口，便于测试）。 */
    public SignedRequest sign(String method, String path, Map<String, String> query,
                              byte[] body, long timestampMs, int recvWindow) {
        String cp = canonicalParams(query == null ? Map.of() : query, timestampMs, recvWindow);
        byte[] b = body == null ? new byte[0] : body;
        String bodyHash = sha256Hex(b);
        String payload = method + "&" + path + "&" + cp + "&body_hash=" + bodyHash;
        byte[] sig = ed25519Sign(payload.getBytes(StandardCharsets.UTF_8));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-API-Key", apiKey);
        headers.put("X-Timestamp", String.valueOf(timestampMs));
        headers.put("X-Recv-Window", String.valueOf(recvWindow));
        headers.put("X-Signature", Base64.getEncoder().encodeToString(sig));
        return new SignedRequest(headers, payload);
    }

    /** GET 空 body 的固定 body_hash（sha256("")）。 */
    public static String emptyBodyHash() {
        return EMPTY_BODY_HASH;
    }

    /**
     * canonical_params：所有 (key,value) 先 RFC3986 编码，再按 key 升序（ASCII 字节序、区分大小写）排序。
     * 纯静态、暴露给测试和排查用。
     */
    public static String canonicalParams(Map<String, String> query, long timestampMs, int recvWindow) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> e : query.entrySet()) {
            pairs.add(Rfc3986.encode(e.getKey()) + "=" + Rfc3986.encode(e.getValue() == null ? "" : e.getValue()));
        }
        pairs.add("recvWindow=" + Rfc3986.encode(String.valueOf(recvWindow)));
        pairs.add("timestamp=" + Rfc3986.encode(String.valueOf(timestampMs)));
        pairs.sort(String::compareTo);
        return String.join("&", pairs);
    }

    private byte[] ed25519Sign(byte[] payload) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(payload);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 signing failed", e);
        }
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }
}
