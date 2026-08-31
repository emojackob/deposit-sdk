import emojackob.deposit.client.ApiException;
import emojackob.deposit.client.DepositClient;
import emojackob.deposit.client.DepositClientConfig;
import emojackob.deposit.model.AllocateRequest;
import emojackob.deposit.model.AllocatedAddress;
import emojackob.deposit.model.Balance;
import emojackob.deposit.model.BindRequest;
import emojackob.deposit.model.CreateWithdrawalRequest;
import emojackob.deposit.model.Deposit;
import emojackob.deposit.model.Page;
import emojackob.deposit.model.WithdrawalNotify;
import emojackob.deposit.sign.Keys;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;

/**
 * 示例 2：完整业务流 + 错误处理。
 * 用法（先按示例 1 生成密钥并注册公钥，把下面占位符替换为真实值）：
 *   javac -cp ../target/deposit-sdk-0.1.0.jar BasicUsage.java
 *   java -cp .:../target/deposit-sdk-0.1.0.jar BasicUsage
 */
public class BasicUsage {
    public static void main(String[] args) throws Exception {
        PrivateKey privateKey = Keys.privateKeyFromPem("""
                -----BEGIN PRIVATE KEY-----
                ...替换为你的 PKCS#8 私钥...
                -----END PRIVATE KEY-----
                """);

        try (DepositClient client = new DepositClient(DepositClientConfig.builder()
                .baseUrl("https://your-platform.example.com")   // 平台地址
                .apiKey("dnk_xxx")                              // 注册的 key_id
                .privateKey(privateKey)
                .project("demo")                                // 可选，POST 请求体携带
                .build())) {

            // 1) 分配 10 个子地址
            List<AllocatedAddress> allocated = client.allocateAddress(new AllocateRequest(10, null, null));
            System.out.println("allocated = " + allocated.size());

            // 2) 绑定用户
            String subAddress = allocated.get(0).getAddress();
            System.out.println("bound = " + client.bindAddress(subAddress, new BindRequest("user-1001", null)));

            // 3) 查询 ERC20 余额（token 必填合约地址）
            String usdt = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
            Balance balance = client.getBalance(subAddress, usdt);
            System.out.println("balance = " + balance.getBalance() + " " + balance.getToken());

            // 4) 充值列表
            Page<Deposit> deposits = client.listDeposits(Map.of("page", "1", "page_size", "50"));
            System.out.println("deposits total = " + deposits.getTotal());

            // 5) 提款记录分页列表（不要带 order_nos；按单号批量查用 listWithdrawals）
            Page<WithdrawalNotify> withdrawals = client.listWithdrawalRecords(
                    Map.of("page", "1", "page_size", "50"));
            System.out.println("withdrawals total = " + withdrawals.getTotal());

            // 6) 创建提款（order_no 必填幂等键；同一单号同参数重试返回当前快照）
            WithdrawalNotify wd = client.createWithdrawal(new CreateWithdrawalRequest(
                    "WD-20260825-001", "pool", "0xreceiving", "100", usdt, ""));
            System.out.println("withdrawal = " + wd.getOrderNo() + " / " + wd.getStatus());

        } catch (ApiException e) {
            // err.code 分支：conflict = 同单号参数不同或该单已终态；不是「订单号重复」
            switch (e.getCode()) {
                case "conflict" -> System.out.println("同单号参数冲突或已终态：" + e.getMessage());
                case "unauthorized" -> System.out.println("签名或 Key 无效，请检查配置");
                default -> System.out.println("http=" + e.getHttpStatus() + " code=" + e.getCode()
                        + " msg=" + e.getMessage());
            }
        }
    }
}
