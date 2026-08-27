package emojackob.deposit.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import emojackob.deposit.model.AllocateRequest;
import emojackob.deposit.model.AllocatedAddress;
import emojackob.deposit.model.Balance;
import emojackob.deposit.model.BindRequest;
import emojackob.deposit.model.CreateWithdrawalRequest;
import emojackob.deposit.sign.Keys;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 端到端：本地 HTTP 服务端按后端规则重建负载并验签，验证 SDK 签名与解析。 */
class DepositClientTest {

    static HttpServer server;
    static String baseUrl;
    static KeyPair kp;

    @BeforeAll
    static void start() throws Exception {
        kp = Keys.generateKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/biz/addresses", DepositClientTest::handleAddresses);
        server.createContext("/api/v1/biz/withdrawals", DepositClientTest::handleWithdrawals);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    DepositClient client() {
        return new DepositClient(DepositClientConfig.builder()
                .baseUrl(baseUrl)
                .apiKey("dnk_test")
                .privateKey(kp.getPrivate())
                .project("demo")
                .build());
    }

    @Test
    void allocateAddressParsesAndSigns() throws Exception {
        try (DepositClient c = client()) {
            List<AllocatedAddress> r = c.allocateAddress(new AllocateRequest(1, null, null));
            assertEquals(1, r.size());
            assertEquals("0xabc", r.get(0).getAddress());
            assertEquals(1, r.get(0).getIndex());
        }
    }

    @Test
    void bindAddressOk() throws Exception {
        try (DepositClient c = client()) {
            assertTrue(c.bindAddress("0xabc", new BindRequest("user-1", null)));
        }
    }

    @Test
    void getBalanceParses() throws Exception {
        try (DepositClient c = client()) {
            Balance b = c.getBalance("0xabc", "native");
            assertEquals("native", b.getToken());
            assertEquals("1.5", b.getBalance());
            assertEquals("1500000000000000000", b.getBalanceRaw());
        }
    }

    @Test
    void errorEnvelopeThrowsApiException() {
        try (DepositClient c = client()) {
            ApiException e = assertThrows(ApiException.class, () ->
                    c.createWithdrawal(new CreateWithdrawalRequest("WD-1", "pool", "0x1", "1", null, null)));
            assertEquals("conflict", e.getCode());
            assertEquals(409, e.getHttpStatus());
        }
    }

    // ---- 模拟服务端：按后端规则重建负载并验签 ----

    static void handleAddresses(HttpExchange ex) throws IOException {
        if (!verify(ex)) {
            respond(ex, 401, "{\"project\":\"\",\"data\":null,\"err\":{\"code\":\"unauthorized\",\"message\":\"invalid signature\",\"detail\":null}}");
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

    static void handleWithdrawals(HttpExchange ex) throws IOException {
        if (!verify(ex)) {
            respond(ex, 401, "{\"project\":\"\",\"data\":null,\"err\":{\"code\":\"unauthorized\",\"message\":\"invalid signature\",\"detail\":null}}");
            return;
        }
        respond(ex, 409, "{\"project\":\"demo\",\"data\":null,"
                + "\"err\":{\"code\":\"conflict\",\"message\":\"order_no already exists\",\"detail\":null}}");
    }

    static boolean verify(HttpExchange ex) throws IOException {
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
            s.initVerify(kp.getPublic());
            s.update(payload.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(sig));
        } catch (GeneralSecurityException e) {
            return false;
        }
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
