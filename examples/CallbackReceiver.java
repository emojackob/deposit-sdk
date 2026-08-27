import emojackob.deposit.callback.WebhookCodec;
import emojackob.deposit.model.DepositNotify;
import emojackob.deposit.model.Envelope;
import emojackob.deposit.sign.Keys;
import emojackob.deposit.sign.WebhookVerifier;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 示例 3：回调接收端（验签 → 时间窗 → 解析 → data.event_key 幂等去重）。
 * 本示例自包含：本地生成实例密钥并构造一条充值回调来演示完整流程；
 * 真实环境中 publicKeyPem 来自「回调公钥」页，headers/body 来自你的 HTTP 框架。
 * 用法：
 *   javac -cp ../target/deposit-sdk-0.1.0.jar CallbackReceiver.java
 *   java -cp .:../target/deposit-sdk-0.1.0.jar CallbackReceiver
 */
public class CallbackReceiver {
    public static void main(String[] args) throws Exception {
        // —— 模拟平台推送（真实环境省略，由 deposit-notify 调用你的端点）——
        KeyPair instanceKeys = Keys.generateKeyPair();
        long ts = System.currentTimeMillis();
        String body = "{\"project\":\"demo\",\"data\":{"
                + "\"event_key\":\"deposit:137:0xtx:3\",\"event_type\":\"deposit\",\"chain_id\":137,"
                + "\"method\":\"transfer\",\"from\":\"0xfrom\",\"to\":\"0xto\","
                + "\"amount\":\"1.5\",\"amount_raw\":\"1500000000000000000\","
                + "\"token_symbol\":\"USDT\",\"token_address\":\"0xtoken\",\"token_decimals\":6,"
                + "\"tx_hash\":\"0xtx\",\"log_index\":3,\"block_number\":61234567,"
                + "\"status\":\"confirmed\",\"confirmations\":12}}";
        String idem = "deposit:137:0xtx:3";
        String signed = "timestamp=" + ts + "&idempotency_key=" + idem + "&" + body;
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(instanceKeys.getPrivate());
        sig.update(signed.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(sig.sign());

        // —— 接收方处理 ——
        String publicKeyPem = toPem("PUBLIC KEY", instanceKeys.getPublic().getEncoded());
        WebhookVerifier verifier = new WebhookVerifier(publicKeyPem);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Notify-Event", "deposit");
        headers.put("X-Notify-Timestamp", String.valueOf(ts));
        headers.put("X-Notify-Idempotency-Key", idem);
        headers.put("X-Notify-Signature", signature);

        if (!verifier.verify(headers, body.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("验签失败或时间窗超限，返回 401");
        }
        Envelope<DepositNotify> envelope = WebhookCodec.parseDeposit(body.getBytes(StandardCharsets.UTF_8));
        DepositNotify event = envelope.getData();

        // 幂等去重：以 data.event_key 为准（同一事件重复投递只处理一次）
        String dedupKey = event.getEventKey();
        System.out.println("eventType = " + event.getEventType());
        System.out.println("dedupKey  = " + dedupKey);
        System.out.println("amount    = " + event.getAmount());
        System.out.println("txHash    = " + event.getTxHash());
    }

    private static String toPem(String label, byte[] der) {
        String b64 = Base64.getEncoder().encodeToString(der).replaceAll("(.{64})", "$1\n");
        return "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
    }
}
