package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 地址余额：{ address, token, balance, balance_raw }。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Balance {
    private String address;
    private String token;
    private String balance;
    private String balanceRaw;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getBalance() {
        return balance;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public String getBalanceRaw() {
        return balanceRaw;
    }

    public void setBalanceRaw(String balanceRaw) {
        this.balanceRaw = balanceRaw;
    }
}
