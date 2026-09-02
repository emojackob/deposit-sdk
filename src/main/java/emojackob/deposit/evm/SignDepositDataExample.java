package emojackob.deposit.evm;

/**
 * 链下签名固定向量：私钥、地址、data 全部硬编码，多次运行输出一致。
 *
 * <p>stdout 即返回给前端的 JSON 体（{@code Content-Type: application/json}）：
 *
 * <pre>
 * {"data":"0x…","signature":"0x…"}
 * </pre>
 *
 * 前端（ethers / viem）原样传入合约，不要再解码：
 *
 * <pre>
 * const { data, signature } = await res.json();
 * await contract.depositToken(amount, data, signature);
 * </pre>
 *
 * <pre>
 *   mvn -q compile exec:java
 * </pre>
 */
public final class SignDepositDataExample {

    /** 测试夹具私钥，勿用于主网。 */
    public static final String PRIVATE_KEY =
            "0xfb69d66f0870bd915cd6ca7e7faea08093e8bf3dd1c8e61929f024d2d5301a39";
    public static final String ADDRESS = "0xb27364863157e74Ed39b3e0c77D5EFd3d5d5d034";
    /** 与 Foundry {@code address(0xCAFE)} 相同。 */
    public static final String RECEIVER3 = "0x000000000000000000000000000000000000cafe";
    public static final long P1 = 50;
    public static final long P2 = 45;
    public static final long P3 = 5;

    public static void main(String[] args) {
        String derived = DepositDataSigner.addressFromPrivateKey(PRIVATE_KEY);
        if (!ADDRESS.equalsIgnoreCase(derived)) {
            throw new IllegalStateException("address mismatch: " + derived);
        }
        SignedDeposit out = DepositDataSigner.signDeposit(PRIVATE_KEY, RECEIVER3, P1, P2, P3);
        SignedDeposit again = DepositDataSigner.signDeposit(PRIVATE_KEY, RECEIVER3, P1, P2, P3);
        if (!out.getDataHex().equals(again.getDataHex())
                || !out.getSignatureHex().equals(again.getSignatureHex())) {
            throw new IllegalStateException("signature not deterministic");
        }

        // HTTP 接口把这段 JSON 写进响应体即可，例如：
        //   response.setContentType("application/json; charset=UTF-8");
        //   response.getWriter().write(out.toJson());
        // Spring MVC：return out;  // Jackson 序列化成同样的 {"data","signature"}
        //
        // 前端：
        //   const { data, signature } = await res.json();
        //   await contract.depositToken(amount, data, signature);
        System.out.println(out.toJson());
    }
}
