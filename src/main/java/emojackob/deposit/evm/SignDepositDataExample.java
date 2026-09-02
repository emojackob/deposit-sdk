package emojackob.deposit.evm;

/**
 * 链下签名固定向量：私钥、地址、data 全部硬编码，多次运行签名输出一致。
 *
 * <p>调用入口 {@link DepositDataSigner#signDeposit}，stdout 先打验证者密钥，再打出参 JSON。
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
        // 验证者密钥与下面固定向量签名无关；address 给合约验证者，privateKey 留服务端。
        SignerKey verifier = generateVerifierKey();
        System.out.println("verifier.address=" + verifier.getAddress());
        System.out.println("verifier.privateKey=" + verifier.getPrivateKeyHex());

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

    /**
     * 生成验证者 secp256k1 密钥（一次性）。
     *
     * <ul>
     *   <li>{@code address}：交给合约验证者（{@code signer}），链上 ecrecover 用
     *   <li>{@code privateKey}：只留服务端，给 {@link DepositDataSigner#signDeposit} 用，不要返回前端
     * </ul>
     *
     * 不参与本类固定向量的签名 / 验签。
     */
    static SignerKey generateVerifierKey() {
        return DepositDataSigner.generateSigner();
    }
}
