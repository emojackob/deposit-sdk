package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 无返回体操作的响应 data：{ ok: true }。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OkResult {
    private boolean ok;

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }
}
