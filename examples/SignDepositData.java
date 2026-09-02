import emojackob.deposit.evm.SignDepositDataExample;

/**
 * 示例 5：DepositWithdrawState.depositToken 链下 ECDSA 签名（固定向量）。
 * stdout 即返回给前端的 JSON：{"data":"0x…","signature":"0x…"}
 * 用法：
 *   mvn -q compile exec:java
 * 或：
 *   mvn -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=cp.txt
 *   javac -cp "target/deposit-sdk-0.1.0-SNAPSHOT.jar:$(cat cp.txt)" examples/SignDepositData.java
 *   java -cp "examples:target/deposit-sdk-0.1.0-SNAPSHOT.jar:$(cat cp.txt)" SignDepositData
 */
public class SignDepositData {
    public static void main(String[] args) {
        SignDepositDataExample.main(args);
    }
}
