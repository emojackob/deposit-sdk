package emojackob.deposit.client;

import emojackob.deposit.model.ErrInfo;

/** 业务接口异常：err.code + message + HTTP 状态码。 */
public class ApiException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final Object detail;

    public ApiException(ErrInfo err, int httpStatus) {
        this(err.getCode(), httpStatus, err.getMessage(), err.getDetail());
    }

    public ApiException(String code, int httpStatus, String message, Object detail) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.detail = detail;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Object getDetail() {
        return detail;
    }
}
