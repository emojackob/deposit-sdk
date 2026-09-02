package emojackob.deposit.evm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DepositDataSignerTest {

    @Test
    void signDepositEntryReturnsStableDataAndSignatureBytes() {
        SignedDeposit out = DepositDataSigner.signDeposit(
                SignDepositDataExample.PRIVATE_KEY,
                SignDepositDataExample.RECEIVER3,
                SignDepositDataExample.P1,
                SignDepositDataExample.P2,
                SignDepositDataExample.P3);

        assertEquals(
                SignDepositDataExample.ADDRESS.toLowerCase(),
                DepositDataSigner.addressFromPrivateKey(SignDepositDataExample.PRIVATE_KEY).toLowerCase());
        assertEquals(128, out.getData().length);
        assertEquals(65, out.getSignature().length);
        assertEquals(
                "0x000000000000000000000000000000000000000000000000000000000000cafe"
                        + "0000000000000000000000000000000000000000000000000000000000000032"
                        + "000000000000000000000000000000000000000000000000000000000000002d"
                        + "0000000000000000000000000000000000000000000000000000000000000005",
                out.getDataHex());
        assertEquals(
                "0xd48bf5c71ee362a8aa9a85df7ab5f4ac54feee545b6e961e669e696d59e6405f"
                        + "40d24b4629252bc117616a67ee034f7072755b65c262f4c643d6f929a54b7f861c",
                out.getSignatureHex());

        SignedDeposit again = DepositDataSigner.signDeposit(
                SignDepositDataExample.PRIVATE_KEY,
                SignDepositDataExample.RECEIVER3,
                SignDepositDataExample.P1,
                SignDepositDataExample.P2,
                SignDepositDataExample.P3);
        assertArrayEquals(out.getData(), again.getData());
        assertArrayEquals(out.getSignature(), again.getSignature());
        assertEquals(
                "{\"data\":\"" + out.getDataHex() + "\",\"signature\":\"" + out.getSignatureHex() + "\"}",
                out.toJson());
    }

    @Test
    void generateSignerDerivesMatchingAddressAndCanSign() {
        SignerKey signer = DepositDataSigner.generateSigner();
        assertEquals(66, signer.getPrivateKeyHex().length());
        assertEquals("0x", signer.getPrivateKeyHex().substring(0, 2));
        assertEquals(
                signer.getAddress().toLowerCase(),
                DepositDataSigner.addressFromPrivateKey(signer.getPrivateKeyHex()).toLowerCase());

        SignedDeposit out = DepositDataSigner.signDeposit(
                signer.getPrivateKeyHex(),
                SignDepositDataExample.RECEIVER3,
                SignDepositDataExample.P1,
                SignDepositDataExample.P2,
                SignDepositDataExample.P3);
        assertEquals(128, out.getData().length);
        assertEquals(65, out.getSignature().length);
    }
}
