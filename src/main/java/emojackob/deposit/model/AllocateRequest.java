package emojackob.deposit.model;

/** 分配子地址请求 data：{ count, user_binding, label }。 */
public class AllocateRequest {
    private Integer count;
    private String userBinding;
    private String label;

    public AllocateRequest() {}

    public AllocateRequest(int count, String userBinding, String label) {
        this.count = count;
        this.userBinding = userBinding;
        this.label = label;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
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
