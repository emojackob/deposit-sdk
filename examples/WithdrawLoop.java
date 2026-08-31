import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import emojackob.deposit.callback.WebhookCodec;
import emojackob.deposit.client.ApiException;
import emojackob.deposit.client.DepositClient;
import emojackob.deposit.client.DepositClientConfig;
import emojackob.deposit.model.CreateWithdrawalRequest;
import emojackob.deposit.model.DepositNotify;
import emojackob.deposit.model.Envelope;
import emojackob.deposit.model.WithdrawalNotify;
import emojackob.deposit.sign.Keys;
import emojackob.deposit.sign.WebhookVerifier;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 示例 4：回调监听 + 定时提款循环。
 *
 * 行为：
 *  1. 启动本地 HTTP 服务监听回调 URL（验签 + 打印日志，内存幂等去重）；
 *  2. 每 DN_INTERVAL_SECS 秒调用一次创建提款接口；
 *  3. 等待充值 / 提款回调，只打印日志，不接数据库、不持久化。
 *
 * 环境变量：
 *  DN_BASE_URL            平台地址（默认 http://127.0.0.1:8600）
 *  DN_API_KEY             注册的 key_id（需管理员授予 withdrawals:create）
 *  DN_PRIVATE_KEY_FILE    接入方 PKCS#8 私钥 PEM 文件（或 DN_PRIVATE_KEY 直接给内容）
 *  DN_PROJECT             可选，请求体携带 project
 *  DN_PUBLIC_KEY_FILE     平台实例回调公钥 PEM 文件（「回调公钥」页；或 DN_PUBLIC_KEY）
 *  DN_CALLBACK_PORT       回调监听端口（默认 9090）
 *  DN_CALLBACK_PATH       回调路径（默认 /callback）
 *  DN_INTERVAL_SECS       提款间隔秒（默认 30）
 *  DN_WITHDRAW_TO         提款收款地址
 *  DN_TOKEN               ERC20 合约地址（必填）
 *  DN_WITHDRAW_AMOUNT     提款数量（默认 100）
 *  DN_METHOD              提款方式 pool|contract（默认 pool）
 *
 * 用法：
 *  javac -cp "../target/deposit-sdk-0.1.0.jar:$CP" WithdrawLoop.java
 *  java -cp "$CP:../target/deposit-sdk-0.1.0.jar" WithdrawLoop
 */
public class WithdrawLoop {

    public static void main(String[] args) throws Exception {
        String baseUrl = env("DN_BASE_URL", "http://127.0.0.1:8600");
        String apiKey = env("DN_API_KEY", "");
        String project = env("DN_PROJECT", "");
        long intervalSecs = Long.parseLong(env("DN_INTERVAL_SECS", "30"));
        String withdrawTo = env("DN_WITHDRAW_TO", "");
        String token = env("DN_TOKEN", "");
        String amount = env("DN_WITHDRAW_AMOUNT", "100");
        String method = env("DN_METHOD", "pool");
        int port = Integer.parseInt(env("DN_CALLBACK_PORT", "9090"));
        String callbackPath = env("DN_CALLBACK_PATH", "/callback");
        String publicKeyPem = pem("DN_PUBLIC_KEY_FILE", "DN_PUBLIC_KEY");
        String privateKeyPem = pem("DN_PRIVATE_KEY_FILE", "DN_PRIVATE_KEY");

        if (apiKey.isEmpty() || withdrawTo.isEmpty() || token.isEmpty()
                || publicKeyPem == null || privateKeyPem == null) {
            System.err.println("缺少配置：DN_API_KEY / DN_WITHDRAW_TO / DN_TOKEN / DN_PRIVATE_KEY(或文件) / DN_PUBLIC_KEY(或文件)");
            System.exit(2);
        }
        PrivateKey privateKey = Keys.privateKeyFromPem(privateKeyPem);

        // 1) 回调监听
        Set<String> seenKeys = new HashSet<>();
        WebhookVerifier verifier = new WebhookVerifier(publicKeyPem);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(callbackPath, ex -> handleCallback(ex, verifier, seenKeys));
        server.start();
        System.out.printf("回调监听已启动: http://127.0.0.1:%d%s%n", port, callbackPath);
        System.out.println("请先在平台「回调端点」注册该 URL，并订阅 deposit / withdrawal_status 事件。");

        // 2) 定时提款
        DepositClient client = new DepositClient(DepositClientConfig.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .privateKey(privateKey)
                .project(project.isEmpty() ? null : project)
                .build());
        AtomicLong seq = new AtomicLong();
        System.out.printf("每 %d 秒创建一笔提款，收款地址 %s…%n", intervalSecs, withdrawTo);
        while (true) {
            try {
                // 每轮新单号；同一业务订单重试必须复用原来的 order_no（指纹相同 → 200 当前快照）
                String orderNo = "wd-" + System.currentTimeMillis() + "-" + seq.incrementAndGet();
                WithdrawalNotify wd = client.createWithdrawal(
                        new CreateWithdrawalRequest(orderNo, method, withdrawTo, amount, token, ""));
                System.out.printf("[提款] order=%s status=%s tx=%s%n",
                        wd.getOrderNo(), wd.getStatus(), wd.getTxHash());
            } catch (ApiException e) {
                System.out.printf("[提款失败] code=%s http=%d msg=%s%n",
                        e.getCode(), e.getHttpStatus(), e.getMessage());
            }
            Thread.sleep(intervalSecs * 1000);
        }
    }

    private static void handleCallback(HttpExchange ex, WebhookVerifier verifier, Set<String> seenKeys)
            throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        Map<String, String> headers = headerMap(ex);
        String event = headers.get("x-notify-event");
        if (!verifier.verify(headers, body)) {
            System.out.println("[回调] 验签失败或时间窗超限 → 401");
            respond(ex, 401);
            return;
        }
        String dedupKey = null;
        try {
            if ("withdrawal_status".equals(event)) {
                Envelope<WithdrawalNotify> env = WebhookCodec.parseWithdrawal(body);
                WithdrawalNotify d = env.getData();
                dedupKey = d.getEventKey();
                System.out.printf("[回调:提款] key=%s order=%s status=%s tx=%s%n",
                        d.getEventKey(), d.getOrderNo(), d.getStatus(), d.getTxHash());
            } else {
                Envelope<DepositNotify> env = WebhookCodec.parseDeposit(body);
                DepositNotify d = env.getData();
                dedupKey = d.getEventKey();
                System.out.printf("[回调:充值] key=%s account=%s amount=%s tx=%s%n",
                        d.getEventKey(), d.getAccountAddr(), d.getAmount(), d.getTxHash());
            }
        } catch (IOException e) {
            System.out.println("[回调] body 解析失败: " + e.getMessage());
        }
        boolean duplicate = dedupKey != null && !seenKeys.add(dedupKey);
        System.out.printf("[回调] event=%s dedupKey=%s%s%n", event, dedupKey,
                duplicate ? "（重复，已忽略）" : "");
        respond(ex, 200);
    }

    private static Map<String, String> headerMap(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, List<String>> e : ex.getRequestHeaders().entrySet()) {
            map.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue().get(0));
        }
        return map;
    }

    private static void respond(HttpExchange ex, int status) throws IOException {
        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    private static String pem(String fileKey, String contentKey) throws IOException {
        String file = System.getenv(fileKey);
        if (file != null && !file.isBlank()) {
            return Files.readString(Path.of(file));
        }
        String content = System.getenv(contentKey);
        return content == null || content.isBlank() ? null : content;
    }
}
