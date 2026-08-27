package emojackob.deposit.sign;

import java.util.Map;

/** 一次签名请求的结果：请求头 + 用于签名的 payload 字符串（可调试）。 */
public final class SignedRequest {
    private final Map<String, String> headers;
    private final String payload;

    public SignedRequest(Map<String, String> headers, String payload) {
        this.headers = headers;
        this.payload = payload;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String payload() {
        return payload;
    }
}
