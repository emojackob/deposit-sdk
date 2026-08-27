package emojackob.deposit.sign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class KeysTest {

    @Test
    void pemRoundTrip() throws Exception {
        KeyPair kp = Keys.generateKeyPair();
        String pubPem = toPublicPem(kp.getPublic());
        String privPem = toPrivatePem(kp.getPrivate());

        PublicKey pub = Keys.publicKeyFromPem(pubPem);
        PrivateKey priv = Keys.privateKeyFromPem(privPem);

        assertEquals(kp.getPublic(), pub);
        assertEquals(kp.getPrivate(), priv);
    }

    static String toPublicPem(PublicKey key) {
        return wrapPem("PUBLIC KEY", key.getEncoded());
    }

    static String toPrivatePem(PrivateKey key) {
        return wrapPem("PRIVATE KEY", key.getEncoded());
    }

    private static String wrapPem(String label, byte[] der) {
        String b64 = Base64.getEncoder().encodeToString(der).replaceAll("(.{64})", "$1\n");
        return "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
    }
}
