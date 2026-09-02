# deposit-sdk

deposit-notify 对接 SDK（Java 17+）。业务标的仅为 ERC20：余额查询与创建提款的 `token` 必填合约地址，不接受空值或 `native`。创建提款的 `order_no` 必填，作为幂等键。负责：

- 调用业务接口 `/api/v1/biz/*`：统一信封 + Ed25519 请求签名（SigV4 风格 body_hash）
- 接收充值 / 提款回调：验签 + 时间窗 + 幂等键（`data.event_key`）

## 安装

```bash
mvn install
```

坐标：

```xml
<dependency>
  <groupId>io.github.emojackob.deposit</groupId>
  <artifactId>deposit-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 快速开始

```java
import emojackob.deposit.client.DepositClient;
import emojackob.deposit.client.DepositClientConfig;
import emojackob.deposit.sign.Keys;

// 私钥 = 离线密钥生成 / openssl 生成的 PKCS#8 PEM，公钥注册到「我的 API Key」
var privateKey = Keys.privateKeyFromPem("-----BEGIN PRIVATE KEY-----…");

var client = new DepositClient(DepositClientConfig.builder()
        .baseUrl("https://api.example.com")
        .apiKey("dnk_xxx")
        .privateKey(privateKey)
        .project("demo")          // 可选，POST 请求体携带 project
        .build());

// 分配充值地址（user_binding / label / count 必填；同一 binding 幂等）
var allocation = client.allocateAddress(new AllocateRequest("user-1001", "primary", 2));

// 按 binding 查询 / 按地址反查（与 POST 返回同一 AddressAllocation）
var same = client.getAddressAllocation("user-1001");
var byAddr = client.getAddressAllocationByAddress(allocation.getAllocated().get(0).getAddress());

// 查询 ERC20 余额（token 必填合约地址）
var balance = client.getBalance(allocation.getAllocated().get(0).getAddress(),
        "0xdAC17F958D2ee523a2206206994597C13D831ec7");

// 创建提款（order_no 必填幂等键；token 必填 ERC20 合约地址）
// 同一 order_no + 相同指纹重试 → 200 当前快照；参数不同 → 409 conflict
var wd = client.createWithdrawal(new CreateWithdrawalRequest(
        "WD-20260825-001", "pool", "0x…", "100",
        "0xdAC17F958D2ee523a2206206994597C13D831ec7", ""));

提款接口的同步响应与提款回调共用同一结构（`WithdrawalNotify`）：创建/查询返回的 `data`
即该订单当前状态的事件快照（`event_key = withdrawal:{order_no}:{status}`），可直接复用回调解析逻辑；
区别仅在于同步响应不签名，回调带 Ed25519 签名（见下）。

// 充值列表
var page = client.listDeposits(Map.of("page", "1", "page_size", "50"));

// 提款记录分页列表（不要带 order_nos）
var wdPage = client.listWithdrawalRecords(Map.of("page", "1", "page_size", "50"));

// 按已知单号批量查
var items = client.listWithdrawals(List.of("WD-20260825-001"));

// 取消未上链提款
client.cancelWithdrawal("WD-20260825-001");
```

错误处理：业务失败（`err != null`）抛 `ApiException`，含 `code` / `message` / `httpStatus`。
`409 conflict` 表示同一 `order_no` 已存在但指纹不同，或该单已 `cancelled`/`failed`，不是简单的「订单号重复」。
同一 `order_no` 且参数相同的重试会成功，返回该订单当前状态快照。

## 回调验签

```java
import emojackob.deposit.callback.WebhookCodec;
import emojackob.deposit.sign.WebhookVerifier;

var verifier = new WebhookVerifier(instancePublicKeyPem); // 「回调公钥」页
if (!verifier.verify(headers, bodyBytes)) {
    return 401; // 验签失败 / 时间窗超限
}
var event = WebhookCodec.parseDeposit(bodyBytes).getData();
// 幂等去重以 event.getEventKey() 为准
```

回调体统一信封 `{ project, data, err }`，`data.event_key` 为业务幂等键：

- 充值：`deposit:{chain_id}:{tx_hash}[:{log_index}]`
- 提款：`withdrawal:{order_no}:{status}`

## 签名协议（与后端一致）

```
payload = METHOD & /path & canonical_params & body_hash=sha256_hex(raw_body)
X-Signature = base64( Ed25519_sign( 私钥, payload 的 UTF-8 字节 ) )
```

`canonical_params`：业务 query + recvWindow + timestamp，先 RFC3986 编码、再按 key 升序（ASCII、区分大小写）排序。
签名数据一律在请求头（`X-API-Key` / `X-Timestamp` / `X-Recv-Window` / `X-Signature`）。

## 测试

```bash
mvn test
```

测试覆盖：RFC3986 编码、canonical 排序、签名负载格式、Ed25519 验签往返、回调验签（篡改/过期）、以及本地 HTTP 服务端按后端规则重建负载验签的端到端用例。

## 合约链下签名（`depositToken` 的 data / signature）

入口：`DepositDataSigner.signDeposit`。

```java
import emojackob.deposit.evm.DepositDataSigner;
import emojackob.deposit.evm.SignedDeposit;

// 入参：signer 私钥、receiver3、三份百分比（之和 = 100）
SignedDeposit out = DepositDataSigner.signDeposit(
        "0xfb69d66f0870bd915cd6ca7e7faea08093e8bf3dd1c8e61929f024d2d5301a39",
        "0x000000000000000000000000000000000000cafe",
        50, 45, 5);

// 返回给前端的 JSON（Content-Type: application/json）
// {"data":"0x…","signature":"0x…"}
String json = out.toJson();
```

前端（ethers / viem）原样传入合约，不要解码、不要当 Base64：

```js
const { data, signature } = await res.json();
await contract.depositToken(amount, data, signature);
```

固定向量示例（私钥/消息硬编码，多次运行签名结果相同；另打印一对验证者密钥，address 给合约验证者）：

```bash
mvn -q compile exec:java
```

## 示例

独立可运行的完整示例见 [`examples/`](examples/)：密钥生成、完整业务流、回调接收端、端到端调试（回调监听 + 定时提款）、合约链下签名。
