package emojackob.deposit.callback;

import com.fasterxml.jackson.core.type.TypeReference;
import emojackob.deposit.model.DepositNotify;
import emojackob.deposit.model.Envelope;
import emojackob.deposit.model.WithdrawalNotify;
import emojackob.deposit.util.Json;

import java.io.IOException;

/** 回调 body 解析（统一信封 { project, data, err }）。 */
public final class WebhookCodec {

    private WebhookCodec() {}

    public static Envelope<DepositNotify> parseDeposit(byte[] body) throws IOException {
        return Json.MAPPER.readValue(body, new TypeReference<Envelope<DepositNotify>>() {});
    }

    public static Envelope<WithdrawalNotify> parseWithdrawal(byte[] body) throws IOException {
        return Json.MAPPER.readValue(body, new TypeReference<Envelope<WithdrawalNotify>>() {});
    }
}
