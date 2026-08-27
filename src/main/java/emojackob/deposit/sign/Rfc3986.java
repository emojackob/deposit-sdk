package emojackob.deposit.sign;

/** RFC3986 percent-encoding（与后端签名规则一致：字母数字 -_.~ 不编码，其余 %XX 大写）。 */
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
