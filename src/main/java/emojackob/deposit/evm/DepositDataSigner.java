package emojackob.deposit.evm;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * 与 {@code DepositWithdrawState.depositHash} 对齐的链下 ECDSA 签名。
 *
 * <p>对外入口是 {@link #signDeposit}：入参为私钥和分账比例，出参是合约
 * {@code depositToken(amount, data, signature)} 的 {@code data} / {@code signature} 两段 bytes。
 */
public final class DepositDataSigner {

    private DepositDataSigner() {}

    /**
     * 构造 {@code depositToken} 所需的 data 与 signature。
     *
     * @param privateKeyHex 合约 {@code signer} 对应的 secp256k1 私钥（可带 0x）
     * @param receiver3     第三收款地址；全 0 则该份额并入合约 {@code receiver1}
     * @param p1            {@code receiver1} 百分比
     * @param p2            {@code receiver2} 百分比
     * @param p3            {@code receiver3} 百分比；{@code p1 + p2 + p3} 必须等于 100
     * @return {@code data}、{@code signature} 两段 bytes，前端原样传给合约
     */
    public static SignedDeposit signDeposit(
            String privateKeyHex, String receiver3, long p1, long p2, long p3) {
        byte[] data = encode(receiver3, p1, p2, p3);
        return new SignedDeposit(data, sign(data, privateKeyHex));
    }

    /** abi.encode(address,uint256,uint256,uint256) */
    public static byte[] encode(String receiver3, long p1, long p2, long p3) {
        if (p1 + p2 + p3 != 100) {
            throw new IllegalArgumentException("p1+p2+p3 must be 100");
        }
        byte[] out = new byte[128];
        copy32(Numeric.toBytesPadded(Numeric.toBigInt(receiver3), 32), out, 0);
        copy32(Numeric.toBytesPadded(BigInteger.valueOf(p1), 32), out, 32);
        copy32(Numeric.toBytesPadded(BigInteger.valueOf(p2), 32), out, 64);
        copy32(Numeric.toBytesPadded(BigInteger.valueOf(p3), 32), out, 96);
        return out;
    }

    /** EIP-191 digest，与合约 {@code depositHash(data)} 相同。 */
    public static byte[] digest(byte[] data) {
        byte[] inner = Hash.sha3(data);
        byte[] prefix = "\u0019Ethereum Signed Message:\n32".getBytes(StandardCharsets.US_ASCII);
        byte[] prefixed = new byte[prefix.length + inner.length];
        System.arraycopy(prefix, 0, prefixed, 0, prefix.length);
        System.arraycopy(inner, 0, prefixed, prefix.length, inner.length);
        return Hash.sha3(prefixed);
    }

    /** 65 字节 r || s || v（v 为 27 或 28）。 */
    public static byte[] sign(byte[] data, String privateKeyHex) {
        ECKeyPair keyPair = ECKeyPair.create(Numeric.toBigInt(privateKeyHex));
        Sign.SignatureData sd = Sign.signMessage(digest(data), keyPair, false);
        byte[] sig = new byte[65];
        System.arraycopy(sd.getR(), 0, sig, 0, 32);
        System.arraycopy(sd.getS(), 0, sig, 32, 32);
        sig[64] = sd.getV()[0];
        return sig;
    }

    public static String addressFromPrivateKey(String privateKeyHex) {
        ECKeyPair keyPair = ECKeyPair.create(Numeric.toBigInt(privateKeyHex));
        return Keys.toChecksumAddress(Keys.getAddress(keyPair));
    }

    /** 0x 前缀小写 hex，可直接作为 Solidity / ethers / viem 的 {@code bytes}。 */
    public static String hex(byte[] bytes) {
        return Numeric.toHexString(bytes);
    }

    private static void copy32(byte[] src, byte[] dest, int offset) {
        System.arraycopy(src, 0, dest, offset, 32);
    }
}
