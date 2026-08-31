package emojackob.deposit.model;

/**
 * 创建提款请求 data：{ order_no, method, to, amount, token, from_addr }。
 * {@code order_no} 必填幂等键；{@code token} 必填 ERC20 合约地址。
 * 业务接口不接受空 {@code order_no}（不会由服务端自动生成）。
 */
public class CreateWithdrawalRequest {
    private String orderNo;
    private String method;
    private String to;
    private String amount;
    private String token;
    private String fromAddr;

    public CreateWithdrawalRequest() {}

    public CreateWithdrawalRequest(String orderNo, String method, String to, String amount,
                                   String token, String fromAddr) {
        this.orderNo = orderNo;
        this.method = method;
        this.to = to;
        this.amount = amount;
        this.token = token;
        this.fromAddr = fromAddr;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFromAddr() {
        return fromAddr;
    }

    public void setFromAddr(String fromAddr) {
        this.fromAddr = fromAddr;
    }
}
