package emojackob.deposit.sign;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Ed25519 密钥工具：生成、PEM（PKCS8 / SPKI）解析。JDK 15+ 内置 Ed25519，无需第三方加密库。 */
public final class Keys {

    private Keys() {}

    /** 生成新的 Ed25519 密钥对（私钥留给调用方本地保管，公钥注册到平台）。 */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        return kpg.generateKeyPair();
    }

    /** 解析 PKCS#8 PEM 私钥（-----BEGIN PRIVATE KEY-----）。 */
    public static PrivateKey privateKeyFromPem(String pem) throws GeneralSecurityException {
        byte[] der = pemToDer(pem);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** 解析 SPKI PEM 公钥（-----BEGIN PUBLIC KEY-----），用于回调验签。 */
    public static PublicKey publicKeyFromPem(String pem) throws GeneralSecurityException {
        byte[] der = pemToDer(pem);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
    }

    static byte[] pemToDer(String pem) {
        String body = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
