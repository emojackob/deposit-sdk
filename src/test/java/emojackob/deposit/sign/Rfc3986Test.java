package emojackob.deposit.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
