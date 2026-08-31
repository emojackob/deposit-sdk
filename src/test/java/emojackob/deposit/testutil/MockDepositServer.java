package emojackob.deposit.testutil;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import emojackob.deposit.sign.Rfc3986;
import emojackob.deposit.util.Json;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地模拟 deposit-notify 服务端：按后端规则（canonical 排序 + body_hash + Ed25519）重建负载并验签，
 * 供客户端测试与示例使用。
 */
public final class MockDepositServer {

    private static final String WITHDRAWALS = "/api/v1/biz/withdrawals";

    private final HttpServer server;
    private final KeyPair keyPair;
    /** POST 创建的提款：order_no → 指纹 + 当前快照。 */
    private final Map<String, StoredWithdrawal> created = new ConcurrentHashMap<>();

    public MockDepositServer(KeyPair keyPair) throws IOException {
        this.keyPair = keyPair;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/api/v1/biz/addresses", this::handleAddresses);
        this.server.createContext("/api/v1/biz/deposits", this::handleDeposits);
        this.server.createContext(WITHDRAWALS, this::handleWithdrawals);
        this.server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    private void handleAddresses(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        if (!verify(ex, body)) {
            respond(ex, 401, errJson("unauthorized", "invalid signature"));
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (ex.getRequestMethod().equals("POST")) {
            if (!"/api/v1/biz/addresses".equals(path)) {
                respond(ex, 404, errJson("not_found", "route not found"));
                return;
            }
            respond(ex, 200, "{\"project\":\"demo\",\"data\":" + allocationJson() + ",\"err\":null}");
            return;
        }
        if (path.endsWith("/balance")) {
            respond(ex, 200, "{\"project\":\"demo\",\"data\":{"
                    + "\"address\":\"0xabc\",\"token\":\"USDT\","
                    + "\"balance\":\"1.5\",\"balance_raw\":\"1500000\""
                    + "},\"err\":null}");
            return;
        }
        respond(ex, 200, "{\"project\":\"demo\",\"data\":" + allocationJson() + ",\"err\":null}");
    }

    private static String allocationJson() {
        return "{\"user_binding\":\"user-1001\",\"label\":\"primary\",\"count\":1,"
                + "\"allocated\":[{"
                + "\"address\":\"0xabc\",\"index\":1,"
                + "\"user_binding\":\"user-1001\",\"label\":\"primary\",\"purpose\":\"user\""
                + "}]}";
    }

    private void handleDeposits(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        if (!verify(ex, body)) {
            respond(ex, 401, errJson("unauthorized", "invalid signature"));
            return;
        }
        respond(ex, 200, "{\"project\":\"demo\",\"data\":{\"items\":["
                + "{\"id\":12,\"chain_id\":137,\"method\":\"transfer\",\"from_addr\":\"0xa\","
                + "\"to_addr\":\"0xabc\",\"amount\":\"1.5\",\"amount_raw\":\"1500000000000000000\","
                + "\"token_symbol\":\"USDT\",\"decimals\":6,\"tx_hash\":\"0xtx\","
                + "\"block_number\":61234567,\"status\":\"notified\",\"confirmations\":12}"
                + "],\"total\":1,\"page\":1,\"page_size\":50},\"err\":null}");
    }

    private void handleWithdrawals(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        if (!verify(ex, body)) {
            respond(ex, 401, errJson("unauthorized", "invalid signature"));
            return;
        }
        if (ex.getRequestMethod().equals("POST")
                && WITHDRAWALS.equals(ex.getRequestURI().getPath())) {
            handleCreateWithdrawal(ex, body);
            return;
        }
        if (ex.getRequestMethod().equals("POST")
                && ex.getRequestURI().getPath().endsWith("/cancel")) {
            handleCancelWithdrawal(ex);
            return;
        }
        // 与真实后端一致：无 order_nos 为分页列表；带 order_nos 为按单号批量查。
        if (WITHDRAWALS.equals(ex.getRequestURI().getPath())) {
            if (hasQueryKey(ex, "order_nos")) {
                respond(ex, 200, "{\"project\":\"demo\",\"data\":{\"items\":["
                        + withdrawalNotifyJson("WD-1", "sent")
                        + "]},\"err\":null}");
            } else {
                respond(ex, 200, "{\"project\":\"demo\",\"data\":{\"items\":["
                        + withdrawalNotifyJson("WD-1", "sent")
                        + "],\"total\":1,\"page\":1,\"page_size\":50},\"err\":null}");
            }
        } else {
            respond(ex, 200, "{\"project\":\"demo\",\"data\":"
                    + withdrawalNotifyJson("WD-1", "sent")
                    + ",\"err\":null}");
        }
    }

    private void handleCreateWithdrawal(HttpExchange ex, byte[] body) throws IOException {
        JsonNode data;
        try {
            data = Json.MAPPER.readTree(body).path("data");
        } catch (IOException e) {
            respond(ex, 400, errJson("bad_request", "invalid request body"));
            return;
        }
        String orderNo = text(data, "order_no");
        if (orderNo == null || orderNo.isBlank()) {
            respond(ex, 400, errJson("bad_request", "order_no is required"));
            return;
        }
        Fingerprint fp = Fingerprint.from(data);
        StoredWithdrawal existing = created.get(orderNo);
        if (existing != null) {
            if (existing.matches(fp)) {
                respond(ex, 200, "{\"project\":\"demo\",\"data\":" + existing.snapshot + ",\"err\":null}");
            } else {
                respond(ex, 409, errJson("conflict", "order_no already exists with different parameters"));
            }
            return;
        }
        String snapshot = withdrawalNotifyJson(orderNo, "created");
        created.put(orderNo, new StoredWithdrawal(fp, snapshot));
        respond(ex, 200, "{\"project\":\"demo\",\"data\":" + snapshot + ",\"err\":null}");
    }

    private void handleCancelWithdrawal(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String prefix = WITHDRAWALS + "/";
        String suffix = "/cancel";
        String orderNo = path.startsWith(prefix) && path.endsWith(suffix)
                ? path.substring(prefix.length(), path.length() - suffix.length())
                : "WD-1";
        StoredWithdrawal existing = created.get(orderNo);
        if (existing != null && ("sent".equals(statusOf(existing.snapshot))
                || "confirmed".equals(statusOf(existing.snapshot)))) {
            respond(ex, 409, errJson("conflict", "withdrawal already broadcast"));
            return;
        }
        String snapshot = withdrawalNotifyJson(orderNo, "cancelled");
        if (existing != null) {
            created.put(orderNo, new StoredWithdrawal(existing.fingerprint, snapshot));
        }
        respond(ex, 200, "{\"project\":\"demo\",\"data\":" + snapshot + ",\"err\":null}");
    }

    private static String statusOf(String snapshotJson) {
        int i = snapshotJson.indexOf("\"status\":\"");
        if (i < 0) {
            return "";
        }
        int start = i + "\"status\":\"".length();
        int end = snapshotJson.indexOf('"', start);
        return end < 0 ? "" : snapshotJson.substring(start, end);
    }

    private static boolean hasQueryKey(HttpExchange ex, String key) {
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        for (String pair : raw.split("&")) {
            String k = pair.split("=", 2)[0];
            if (key.equals(k)) {
                return true;
            }
        }
        return false;
    }

    private static String withdrawalNotifyJson(String orderNo, String status) {
        return "{\"event_key\":\"withdrawal:" + orderNo + ":" + status + "\","
                + "\"event_type\":\"withdrawal_status\",\"chain_id\":137,"
                + "\"project\":\"demo\",\"order_no\":\"" + orderNo + "\",\"method\":\"pool\","
                + "\"from\":\"0xabc\",\"to\":\"0x1\",\"amount\":\"1\","
                + "\"amount_raw\":\"1000000000000000000\","
                + "\"token_symbol\":\"USDT\",\"token_address\":\"0xusdt\",\"token_decimals\":6,"
                + "\"tx_hash\":null,\"block_number\":null,\"status\":\"" + status + "\","
                + "\"error\":null,\"created_at\":\"2026-08-25T03:30:00.000Z\"}";
    }

    private boolean verify(HttpExchange ex, byte[] body) {
        Map<String, String> h = new HashMap<>();
        for (Map.Entry<String, List<String>> e : ex.getRequestHeaders().entrySet()) {
            h.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().get(0));
        }
        String ts = h.get("x-timestamp");
        String rw = h.get("x-recv-window");
        String sig = h.get("x-signature");
        if (h.get("x-api-key") == null || ts == null || rw == null || sig == null) {
            return false;
        }

        List<String> pairs = new ArrayList<>();
        String rawQuery = ex.getRequestURI().getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String pair : rawQuery.split("&")) {
                String[] kv = pair.split("=", 2);
                pairs.add(Rfc3986.encode(kv[0]) + "=" + Rfc3986.encode(kv.length > 1 ? kv[1] : ""));
            }
        }
        pairs.add("recvWindow=" + Rfc3986.encode(rw));
        pairs.add("timestamp=" + Rfc3986.encode(ts));
        pairs.sort(String::compareTo);

        String payload = ex.getRequestMethod() + "&" + ex.getRequestURI().getPath()
                + "&" + String.join("&", pairs)
                + "&body_hash=" + sha256Hex(body);
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(keyPair.getPublic());
            s.update(payload.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(sig));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static String errJson(String code, String message) {
        return "{\"project\":\"\",\"data\":null,\"err\":{\"code\":\"" + code
                + "\",\"message\":\"" + message + "\",\"detail\":null}}";
    }

    private static String sha256Hex(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static String text(JsonNode data, String field) {
        JsonNode n = data.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String s = n.asText();
        return s.isEmpty() ? null : s;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    /** 与后端 plan C 对齐：method 精确；to/token 忽略大小写；amount 按请求字符串；from 仅在重试带了才比。 */
    private static final class Fingerprint {
        final String method;
        final String to;
        final String amount;
        final String token;
        final String fromAddr;

        Fingerprint(String method, String to, String amount, String token, String fromAddr) {
            this.method = method;
            this.to = to;
            this.amount = amount;
            this.token = token;
            this.fromAddr = fromAddr;
        }

        static Fingerprint from(JsonNode data) {
            return new Fingerprint(
                    text(data, "method"),
                    text(data, "to"),
                    text(data, "amount"),
                    text(data, "token"),
                    text(data, "from_addr"));
        }

        boolean matches(Fingerprint retry) {
            if (retry.method == null || !retry.method.equals(method)) {
                return false;
            }
            if (!eqIgnoreCase(to, retry.to) || !eqIgnoreCase(token, retry.token)) {
                return false;
            }
            if (amount == null || !amount.equals(retry.amount)) {
                return false;
            }
            if (retry.fromAddr != null) {
                return eqIgnoreCase(fromAddr, retry.fromAddr);
            }
            return true;
        }

        private static boolean eqIgnoreCase(String a, String b) {
            if (a == null || b == null) {
                return a == b;
            }
            return lower(a).equals(lower(b));
        }
    }

    private static final class StoredWithdrawal {
        final Fingerprint fingerprint;
        final String snapshot;

        StoredWithdrawal(Fingerprint fingerprint, String snapshot) {
            this.fingerprint = fingerprint;
            this.snapshot = snapshot;
        }

        boolean matches(Fingerprint retry) {
            return fingerprint.matches(retry);
        }
    }
}
