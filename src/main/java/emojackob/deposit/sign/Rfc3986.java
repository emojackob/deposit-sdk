package emojackob.deposit.sign;

/**
 * RFC3986 percent-encoding（与后端签名规则一致：字母数字 -_.~ 不编码，其余 %XX 大写）。
 *
 * <p>不用 {@link java.net.URLEncoder}：它是 {@code application/x-www-form-urlencoded} 规则
 * （空格编码为 {@code +}，不是 {@code %20}），且 {@code ~} 的行为随 JDK 版本不一致；
 * Guava / Spring 的 escaper 同样是 {@code +} 规则。JDK 没有现成的纯 RFC3986 实现，
 * 因此按白名单手写——AWS / 币安 SDK 同样自带一份。</p>
 */
public final class Rfc3986 {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private Rfc3986() {}

    public static String encode(String input) {
        byte[] bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(HEX[c >>> 4]).append(HEX[c & 0x0F]);
            }
        }
        return sb.toString();
    }
}
