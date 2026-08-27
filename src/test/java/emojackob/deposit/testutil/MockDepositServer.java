package emojackob.deposit.testutil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import emojackob.deposit.sign.Rfc3986;
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

/**
 * 本地模拟 deposit-notify 服务端：按后端规则（canonical 排序 + body_hash + Ed25519）重建负载并验签，
 * 供客户端测试与示例使用。
 */
public final class MockDepositServer {

    private final HttpServer server;
    private final KeyPair keyPair;

    public MockDepositServer(KeyPair keyPair) throws IOException {
        this.keyPair = keyPair;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/api/v1/biz/addresses", this::handleAddresses);
        this.server.createContext("/api/v1/biz/deposits", this::handleDeposits);
        this.server.createContext("/api/v1/biz/withdrawals", this::handleWithdrawals);
        this.server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    private void handleAddresses(HttpExchange ex) throws IOException {
        if (!verify(ex)) {
            respond(ex, 401, errJson("unauthorized", "invalid signature"));
            return;
        }
        if (ex.getRequestMethod().equals("POST")) {
            if (ex.getRequestURI().getPath().endsWith("/binding")) {
                respond(ex, 200, "{\"project\":\"demo\",\"data\":{\"ok\":true},\"err\":null}");
            } else {
                respond(ex, 200, "{\"project\":\"demo\",\"data\":{\"allocated\":["
                        + "{\"address\":\"0xabc\",\"index\":1,\"user_binding\":null,\"label\":null}"
                        + "]},\"err\":null}");
            }
        } else {
            respond(ex, 200, "{\"project\":\"demo\",\"data\":{"
                    + "\"address\":\"0xabc\",\"token\":\"native\","
                    + "\"balance\":\"1.5\",\"balance_raw\":\"1500000000000000000\""
                    + "},\"err\":null}");
        }
    }

    private void handleDeposits(HttpExchange ex) throws IOException {
        if (!verify(ex)) {
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
        if (!verify(ex)) {
            respond(ex, 401, errJson("unauthorized", "invalid signature"));
            return;
        }
        if (ex.getRequestMethod().equals("POST")) {
            respond(ex, 409, errJson("conflict", "order_no already exists"));
            return;
        }
        // 与真实后端一致：同步响应 data 与提款回调共用同一结构（WithdrawalNotify）。
        if (ex.getRequestURI().getPath().equals("/api/v1/biz/withdrawals")) {
            respond(ex, 200, "{\"project\":\"demo\",\"data\":{\"items\":["
                    + withdrawalNotifyJson("WD-1", "sent")
                    + "]},\"err\":null}");
        } else {
            respond(ex, 200, "{\"project\":\"demo\",\"data\":"
                    + withdrawalNotifyJson("WD-1", "sent")
                    + ",\"err\":null}");
        }
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

    private boolean verify(HttpExchange ex) throws IOException {
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

        byte[] body = ex.getRequestBody().readAllBytes();
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
}
