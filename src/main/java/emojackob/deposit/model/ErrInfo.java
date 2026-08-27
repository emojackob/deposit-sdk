package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 响应信封 err 字段：{ code, message, detail }。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrInfo {
    private String code;
    private String message;
    private Object detail;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getDetail() {
        return detail;
    }

    public void setDetail(Object detail) {
        this.detail = detail;
    }

    @Override
    public String toString() {
        return "ErrInfo{code='" + code + "', message='" + message + "', detail=" + detail + '}';
    }
}
