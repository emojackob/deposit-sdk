package emojackob.deposit.evm;

/**
 * 验证者密钥：{@link #getAddress} 交给合约验证者（{@code signer}），
 * {@link #getPrivateKeyHex} 只留在服务端给 {@link DepositDataSigner#signDeposit} 用。
 *
 * <p>不要把本对象返回给前端。
 */
public final class SignerKey {

    private final String privateKeyHex;
    private final String address;

    SignerKey(String privateKeyHex, String address) {
        this.privateKeyHex = privateKeyHex;
        this.address = address;
    }

    /** secp256k1 私钥，{@code 0x} + 64 hex。 */
    public String getPrivateKeyHex() {
        return privateKeyHex;
    }

    /** EIP-55 checksum 地址，交给合约验证者。 */
    public String getAddress() {
        return address;
    }
}
