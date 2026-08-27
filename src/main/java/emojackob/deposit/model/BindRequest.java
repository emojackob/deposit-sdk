package emojackob.deposit.model;

/** 绑定/解绑用户请求 data：{ user_binding, label }（user_binding 传空字符串即解绑）。 */
public class BindRequest {
    private String userBinding;
    private String label;

    public BindRequest() {}

    public BindRequest(String userBinding, String label) {
        this.userBinding = userBinding;
        this.label = label;
    }

    public String getUserBinding() {
        return userBinding;
    }

    public void setUserBinding(String userBinding) {
        this.userBinding = userBinding;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
