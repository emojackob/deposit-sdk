package emojackob.deposit.evm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import emojackob.deposit.util.Json;
import java.io.UncheckedIOException;

/**
 * {@link DepositDataSigner#signDeposit} 的出参：合约 {@code depositToken} 的两段 bytes。
 *
 * <p>返回给前端的 JSON 只有两个 0x hex 字段（不要把 {@code byte[]} 直接丢进 Jackson，
 * 默认会编成 Base64，ethers / viem 认不了）：
 *
 * <pre>
 * {"data":"0x…","signature":"0x…"}
 * </pre>
 *
 * 前端原样传入 {@code depositToken(amount, data, signature)}。
 */
@JsonPropertyOrder({"data", "signature"})
public final class SignedDeposit {

    private final byte[] data;
    private final byte[] signature;

    SignedDeposit(byte[] data, byte[] signature) {
        this.data = data.clone();
        this.signature = signature.clone();
    }

    /** 入参编码后的 {@code bytes data}（128 字节 abi.encode）。 */
    @JsonIgnore
    public byte[] getData() {
        return data.clone();
    }

    /** ECDSA 签名 {@code bytes signature}（65 字节 r||s||v）。 */
    @JsonIgnore
    public byte[] getSignature() {
        return signature.clone();
    }

    /** JSON 字段 {@code data}：0x hex，ethers / viem 可直接当 BytesLike。 */
    @JsonProperty("data")
    public String getDataHex() {
        return DepositDataSigner.hex(data);
    }

    /** JSON 字段 {@code signature}：0x hex。 */
    @JsonProperty("signature")
    public String getSignatureHex() {
        return DepositDataSigner.hex(signature);
    }

    /**
     * HTTP 响应体（{@code Content-Type: application/json}）。
     *
     * <pre>
     * {"data":"0x…","signature":"0x…"}
     * </pre>
     */
    public String toJson() {
        try {
            return Json.MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return toJson();
    }
}
