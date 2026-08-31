package emojackob.deposit.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import emojackob.deposit.model.AddressAllocation;
import emojackob.deposit.model.AllocateRequest;
import emojackob.deposit.model.Balance;
import emojackob.deposit.model.CreateWithdrawalRequest;
import emojackob.deposit.model.Deposit;
import emojackob.deposit.model.Envelope;
import emojackob.deposit.model.ItemsResult;
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

    /**
     * 创建或幂等读取不可变充值地址分配（addresses:allocate）。
     * {@code user_binding} / {@code label} 必填非空；{@code count} 为 1..=100。
     * 同一属主下相同 {@code user_binding} 且 label/count 一致则返回原 allocation；
     * label 或 count 不同则服务端 409。创建后不可通过业务接口修改或解绑。
     */
    public AddressAllocation allocateAddress(AllocateRequest request) {
        requireAllocation(request);
        return post("/api/v1/biz/addresses", null, request, AddressAllocation.class);
    }

    /** 按业务用户绑定读取当前 API 属主的地址分配（addresses:read）。 */
    public AddressAllocation getAddressAllocation(String userBinding) {
        return get("/api/v1/biz/addresses",
                Map.of("user_binding", requireUserBinding(userBinding)), AddressAllocation.class);
    }

    /** 按地址反查所属分配（addresses:read）。 */
    public AddressAllocation getAddressAllocationByAddress(String address) {
        return get("/api/v1/biz/addresses/" + requireAddress(address), null, AddressAllocation.class);
    }

    /** 查询地址 ERC20 余额；token 为合约地址，必填。不接受空值或 {@code native}。 */
    public Balance getBalance(String address, String token) {
        return get("/api/v1/biz/addresses/" + requireAddress(address) + "/balance",
                Map.of("token", requireErc20Token(token)), Balance.class);
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

    /**
     * 创建提款订单。{@code order_no} 必填，作为幂等键；{@code token} 必填 ERC20 合约地址。
     * 同一 {@code order_no} 且指纹相同（method / to / amount / token，以及请求里带了的 from_addr）再次提交，
     * 返回 200 与该订单当前快照（状态可能已推进），不是首次响应的字节拷贝。
     * 同一 {@code order_no} 但参数不同，或该单已 cancelled/failed，服务端返回 409 {@code conflict}。
     * 返回与提款回调相同的 data 结构（同步响应无签名，回调带签名）。
     */
    public WithdrawalNotify createWithdrawal(CreateWithdrawalRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        requireOrderNo(request.getOrderNo());
        requireErc20Token(request.getToken());
        return post("/api/v1/biz/withdrawals", null, request, WithdrawalNotify.class);
    }

    /** 查询单个提款进度。 */
    public WithdrawalNotify getWithdrawal(String orderNo) {
        return get("/api/v1/biz/withdrawals/" + requireOrderNo(orderNo), null, WithdrawalNotify.class);
    }

    /**
     * 取消未上链提款（withdrawals:create）。仅 {@code created}/{@code pending} 且未广播可取消。
     * 请求体仍走统一信封；业务 data 为空对象。
     */
    public WithdrawalNotify cancelWithdrawal(String orderNo) {
        return post("/api/v1/biz/withdrawals/" + requireOrderNo(orderNo) + "/cancel",
                null, Map.of(), WithdrawalNotify.class);
    }

    /**
     * 提款记录分页列表；query 可传 status / page / page_size。
     * 不要传 {@code order_nos}（按单号批量查请用 {@link #listWithdrawals}）。
     */
    public Page<WithdrawalNotify> listWithdrawalRecords(Map<String, String> query) {
        return get("/api/v1/biz/withdrawals", query, new TypeReference<Page<WithdrawalNotify>>() {});
    }

    /** 按已知单号批量查询提款进度（请求带 {@code order_nos}，响应仅为 items）。 */
    public List<WithdrawalNotify> listWithdrawals(List<String> orderNos) {
        ItemsResult<WithdrawalNotify> r = get(
                "/api/v1/biz/withdrawals",
                Map.of("order_nos", String.join(",", orderNos == null ? List.of() : orderNos)),
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

    /** 业务创建提款必须自带 order_no（幂等键）；空值由服务端拒绝，客户端提前拦下。 */
    static String requireOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("order_no is required");
        }
        return orderNo.trim();
    }

    /** 余额与提款的 token 必须是 ERC20 合约地址。 */
    static String requireErc20Token(String token) {
        if (token == null || token.isBlank() || "native".equalsIgnoreCase(token.trim())) {
            throw new IllegalArgumentException("token (ERC20 address) is required");
        }
        return token.trim();
    }

    static AllocateRequest requireAllocation(AllocateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        requireUserBinding(request.getUserBinding());
        if (request.getLabel() == null || request.getLabel().isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        Integer count = request.getCount();
        if (count == null || count < 1 || count > 100) {
            throw new IllegalArgumentException("count must be between 1 and 100");
        }
        return request;
    }

    static String requireUserBinding(String userBinding) {
        if (userBinding == null || userBinding.isBlank()) {
            throw new IllegalArgumentException("user_binding is required");
        }
        return userBinding.trim();
    }

    static String requireAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address is required");
        }
        return address.trim();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void close() {
        // java.net.http.HttpClient 无需显式关闭
    }
}
