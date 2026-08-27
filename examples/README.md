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

# 示例 2：完整业务流（分配/绑定/余额/充值/提款 + 错误处理）
# 先替换 BasicUsage.java 里的 baseUrl / apiKey / 私钥占位符
javac -cp "../target/deposit-sdk-0.1.0.jar:$CP" BasicUsage.java
java -cp "$CP:../target/deposit-sdk-0.1.0.jar" BasicUsage

# 示例 3：回调接收端（验签 + 解析 + event_key 幂等）
javac -cp "../target/deposit-sdk-0.1.0.jar:$CP" CallbackReceiver.java
java -cp "$CP:../target/deposit-sdk-0.1.0.jar" CallbackReceiver
```

Windows 下把 `.:` 换成 `.;`。

> 提示：Maven 用户无需手动拼 classpath——引入 SDK 坐标后 jackson 会作为传递依赖自动带上。
