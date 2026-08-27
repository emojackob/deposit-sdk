package emojackob.deposit.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Rfc3986Test {

    @Test
    void unreservedCharsPassThrough() {
        assertEquals("AZaz09-_.~", Rfc3986.encode("AZaz09-_.~"));
    }

    @Test
    void reservedCharsArePercentEncodedUppercase() {
        assertEquals("a%20b%26c%3D", Rfc3986.encode("a b&c="));
    }

    @Test
    void utf8MultiByteEncoded() {
        assertEquals("%E4%B8%AD", Rfc3986.encode("中"));
    }

    @Test
    void emptyStringStaysEmpty() {
        assertEquals("", Rfc3986.encode(""));
    }

    @Test
    void allReservedCharsMappedUppercase() {
        assertEquals(
                "%21%23%24%25%26%27%28%29%2A%2B%2C%2F%3A%3B%3D%3F%40%5B%5D",
                Rfc3986.encode("!#$%&'()*+,/:;=?@[]"));
    }

    @Test
    void urlEncoderIsNotRfc3986() {
        // 锁死：URLEncoder 是 form-urlencoded（空格→+），与签名规则不兼容
        assertEquals("a%20b", Rfc3986.encode("a b"));
        assertNotEquals(Rfc3986.encode("a b"), java.net.URLEncoder.encode("a b", StandardCharsets.UTF_8));
    }

    @Test
    void nullInputThrowsNpe() {
        assertThrows(NullPointerException.class, () -> Rfc3986.encode(null));
    }
}
