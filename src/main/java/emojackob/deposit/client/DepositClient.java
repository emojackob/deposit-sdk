package emojackob.deposit.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import emojackob.deposit.model.AllocateRequest;
import emojackob.deposit.model.AllocatedAddress;
import emojackob.deposit.model.AllocatedResult;
import emojackob.deposit.model.Balance;
import emojackob.deposit.model.BindRequest;
import emojackob.deposit.model.CreateWithdrawalRequest;
import emojackob.deposit.model.Deposit;
import emojackob.deposit.model.Envelope;
import emojackob.deposit.model.ItemsResult;
import emojackob.deposit.model.OkResult;
import emojackob.deposit.model.Page;
import emojackob.deposit.model.WithdrawalNotify;
import emojackob.deposit.sign.RequestSigner;
import emojackob.deposit.sign.Rfc3986;
import emojackob.deposit.sign.SignedRequest;
import emojackob.deposit.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * deposit-notify 业务接口客户端（/api/v1/biz/*）。
 * 统一信封 { project, data, err }；err != null 时抛 {@link ApiException}。
 */
public final class DepositClient implements AutoCloseable {

    private final String baseUrl;
    private final String project;
    private final int recvWindow;
    private final RequestSigner signer;
    private final HttpClient http;
    private final int requestTimeoutSeconds;

    public DepositClient(DepositClientConfig config) {
        this.baseUrl = stripTrailingSlash(config.baseUrl());
        this.project = config.project();
        this.recvWindow = config.recvWindow() > 0 ? config.recvWindow() : RequestSigner.DEFAULT_RECV_WINDOW_MS;
        this.signer = new RequestSigner(config.privateKey(), config.apiKey());
        this.requestTimeoutSeconds = config.requestTimeoutSeconds() > 0 ? config.requestTimeoutSeconds() : 30;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds() > 0 ? config.connectTimeoutSeconds() : 10))
                .build();
    }

    // ---- 地址 ----

    /** 分配子地址。 */
    public List<AllocatedAddress> allocateAddress(AllocateRequest request) {
        AllocatedResult r = post("/api/v1/biz/addresses", null, request, AllocatedResult.class);
        return r == null || r.getAllocated() == null ? List.of() : r.getAllocated();
    }

    /** 绑定 / 解绑用户（user_binding 传空字符串即解绑）。 */
    public boolean bindAddress(String address, BindRequest request) {
        OkResult r = post("/api/v1/biz/addresses/" + address + "/binding", null, request, OkResult.class);
        return r != null && r.isOk();
    }

    /** 查询地址余额；token 缺省为原生币。 */
    public Balance getBalance(String address, String token) {
        Map<String, String> query = token == null || token.isBlank()
                ? Map.of()
                : Map.of("token", token);
        return get("/api/v1/biz/addresses/" + address + "/balance", query, Balance.class);
    }

    // ---- 充值 ----

    /** 充值列表；query 可传 address / status / page / page_size。 */
    public Page<Deposit> listDeposits(Map<String, String> query) {
        return get("/api/v1/biz/deposits", query, new TypeReference<Page<Deposit>>() {});
    }

    /** 按交易哈希查单笔充值。 */
    public Deposit getDeposit(String txHash) {
        return get("/api/v1/biz/deposits/" + txHash, null, Deposit.class);
    }

    // ---- 提款 ----

    /** 创建提款订单；返回与提款回调相同的 data 结构（同步响应无签名，回调带签名）。 */
    public WithdrawalNotify createWithdrawal(CreateWithdrawalRequest request) {
        return post("/api/v1/biz/withdrawals", null, request, WithdrawalNotify.class);
    }

    /** 查询单个提款进度。 */
    public WithdrawalNotify getWithdrawal(String orderNo) {
        return get("/api/v1/biz/withdrawals/" + orderNo, null, WithdrawalNotify.class);
    }

    /** 批量查询提款进度。 */
    public List<WithdrawalNotify> listWithdrawals(List<String> orderNos) {
        ItemsResult<WithdrawalNotify> r = get(
                "/api/v1/biz/withdrawals",
                Map.of("order_nos", String.join(",", orderNos)),
                new TypeReference<ItemsResult<WithdrawalNotify>>() {});
        return r == null || r.getItems() == null ? List.of() : r.getItems();
    }

    // ---- 基础设施 ----

    private <T> T get(String path, Map<String, String> query, Class<T> type) {
        return request("GET", path, query, null, type);
    }

    private <T> T get(String path, Map<String, String> query, TypeReference<T> type) {
        return request("GET", path, query, null, type);
    }

    private <T> T post(String path, Map<String, String> query, Object data, Class<T> type) {
        return request("POST", path, query, envelope(data), type);
    }

    private <T> T request(String method, String path, Map<String, String> query, byte[] body, Class<T> type) {
        JsonNode data = requestRaw(method, path, query, body);
        return Json.MAPPER.convertValue(data, type);
    }

    private <T> T request(String method, String path, Map<String, String> query, byte[] body, TypeReference<T> type) {
        JsonNode data = requestRaw(method, path, query, body);
        return Json.MAPPER.convertValue(data, type);
    }

    private JsonNode requestRaw(String method, String path, Map<String, String> query, byte[] body) {
        Map<String, String> q = query == null ? Map.of() : query;
        byte[] b = body == null ? new byte[0] : body;
        SignedRequest sr = signer.sign(method, path, q, b, System.currentTimeMillis(), recvWindow);

        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (!q.isEmpty()) {
            url.append('?');
            for (Map.Entry<String, String> e : q.entrySet()) {
                url.append(Rfc3986.encode(e.getKey())).append('=')
                        .append(Rfc3986.encode(e.getValue() == null ? "" : e.getValue())).append('&');
            }
            url.setLength(url.length() - 1);
        }

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds));
        for (Map.Entry<String, String> h : sr.headers().entrySet()) {
            rb.header(h.getKey(), h.getValue());
        }
        if (b.length > 0) {
            rb.header("Content-Type", "application/json");
            rb.method(method, HttpRequest.BodyPublishers.ofByteArray(b));
        } else {
            rb.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<byte[]> resp;
        try {
            resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ApiException("network_error", 0, e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("network_error", 0, "interrupted: " + e.getMessage(), null);
        }

        Envelope<JsonNode> env;
        try {
            env = Json.MAPPER.readValue(resp.body(), new TypeReference<Envelope<JsonNode>>() {});
        } catch (IOException e) {
            throw new ApiException("bad_response", resp.statusCode(), "invalid response body", null);
        }
        if (env.getErr() != null) {
            throw new ApiException(env.getErr(), resp.statusCode());
        }
        if (resp.statusCode() >= 400) {
            throw new ApiException(
                    "http_" + resp.statusCode(),
                    resp.statusCode(),
                    new String(resp.body(), StandardCharsets.UTF_8),
                    null);
        }
        return env.getData();
    }

    /** 请求信封：{ project?, data }。 */
    private byte[] envelope(Object data) {
        try {
            ObjectNode root = Json.MAPPER.createObjectNode();
            if (project != null && !project.isBlank()) {
                root.put("project", project);
            }
            root.set("data", Json.MAPPER.valueToTree(data));
            return Json.MAPPER.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot serialize request body", e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void close() {
        // java.net.http.HttpClient 无需显式关闭
    }
}
