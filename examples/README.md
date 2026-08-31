# 示例

独立于 SDK 源码的示例（默认不参与 `mvn test` 构建）。

## 构建 SDK

```bash
mvn -q install
```

## 运行示例

```bash
# 先生成依赖 classpath（把 jackson 等传递依赖写进 cp.txt）
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt

cd examples
CP=".:$(cat ../cp.txt)"

# 示例 1：生成 Ed25519 密钥对并导出 PEM
javac -cp "../target/deposit-sdk-0.1.0.jar:$CP" GenerateKeys.java
java -cp "$CP:../target/deposit-sdk-0.1.0.jar" GenerateKeys

# 示例 2：完整业务流（分配/绑定/余额/充值/提款列表/提款 + 错误处理）
# 先替换 BasicUsage.java 里的 baseUrl / apiKey / 私钥占位符
javac -cp "../target/deposit-sdk-0.1.0.jar:$CP" BasicUsage.java
java -cp "$CP:../target/deposit-sdk-0.1.0.jar" BasicUsage

# 示例 3：回调接收端（验签 + 解析 + event_key 幂等）
javac -cp "../target/deposit-sdk-0.1.0.jar:$CP" CallbackReceiver.java
java -cp "$CP:../target/deposit-sdk-0.1.0.jar" CallbackReceiver

# 示例 4：端到端调试（回调监听 + 定时提款循环）
javac -cp "../target/deposit-sdk-0.1.0.jar:$CP" WithdrawLoop.java
java -cp "$CP:../target/deposit-sdk-0.1.0.jar" WithdrawLoop
```

Windows 下把 `.:` 换成 `.;`。

> 提示：Maven 用户无需手动拼 classpath——引入 SDK 坐标后 jackson 会作为传递依赖自动带上。

## 端到端调试（示例 4）

`WithdrawLoop` 用于把「SDK 请求签名 → 后端处理 → 回调验签解析」整条链路跑通，只打印日志，不接数据库。

### 前置准备（平台侧，一次性）

1. 管理后台完成实例初始化：所属链、主地址确认启用；「扫描监控 → ERC20 直转」填写代币地址
2. 部署合约（至少提款合约，或先只跑 `pool` 方式并分配提款地址池）；提款合约地址配在「地址管理 → 提款合约」
3. 生成接入方密钥对并注册 API Key（`GenerateKeys` 示例），**管理员授予 `withdrawals:create` 权限**
4. 注册回调端点：指向本机可被后端访问的地址
   - 同机调试：`http://127.0.0.1:9090/callback`
   - 跨机/云上：用局域网 IP 或 ngrok/内网穿透，确保后端能访问到
   - 订阅 `deposit` + `withdrawal_status` 两个事件
5. 从平台「回调公钥」页保存实例公钥 PEM 到本地文件

### 运行

```bash
DN_BASE_URL=http://127.0.0.1:8600 \
DN_API_KEY=dnk_xxx \
DN_PRIVATE_KEY_FILE=/path/private_key.pem \
DN_PUBLIC_KEY_FILE=/path/instance_public_key.pem \
DN_WITHDRAW_TO=0x收款地址 \
DN_TOKEN=0xdAC17F958D2ee523a2206206994597C13D831ec7 \
DN_INTERVAL_SECS=30 \
java -cp "$CP:../target/deposit-sdk-0.1.0.jar" WithdrawLoop
```

### 观察什么

- `[提款] order=… status=… tx=…`：创建提款成功（有签名、走业务接口）
- `[回调:充值] key=… account=… amount=… tx=…`：充值回调验签通过并解析
- `[回调:提款] key=… order=… status=… tx=…`：提款状态变更回调
- `（重复，已忽略）`：同一事件重复投递被幂等键拦下
- `[提款失败] code=…`：按 err.code 定位（403=权限未授予 / 400=缺 order_no 或参数不对 / 401=签名或 Key 问题 / 409=同单号参数冲突或已终态）

> 如果回调一直收不到：先确认回调端点地址后端可访问、事件已订阅；再确认实例公钥与验签时间窗（±5 分钟）。
