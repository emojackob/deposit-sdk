import emojackob.deposit.sign.Keys;
import java.security.KeyPair;
import java.util.Base64;

/**
 * 示例 1：生成 Ed25519 密钥对并导出 PEM。
 * 用法：
 *   mvn -q package -DskipTests
 *   javac -cp ../target/deposit-sdk-0.1.0.jar GenerateKeys.java
 *   java -cp .:../target/deposit-sdk-0.1.0.jar GenerateKeys
 */
public class GenerateKeys {
    public static void main(String[] args) throws Exception {
        KeyPair kp = Keys.generateKeyPair();
        System.out.println("—— 公钥（粘贴到「我的 API Key」注册）——");
        System.out.println(toPem("PUBLIC KEY", kp.getPublic().getEncoded()));
        System.out.println("—— 私钥（妥善保存，不要外发）——");
        System.out.println(toPem("PRIVATE KEY", kp.getPrivate().getEncoded()));
    }

    private static String toPem(String label, byte[] der) {
        String b64 = Base64.getEncoder().encodeToString(der).replaceAll("(.{64})", "$1\n");
        return "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
    }
}
