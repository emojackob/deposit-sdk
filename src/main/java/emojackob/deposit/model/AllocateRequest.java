package emojackob.deposit.model;

/**
 * 分配充值地址请求 data：{ user_binding, label, count }。
 * 三个字段均必填；count 取值 1..=100。同一 user_binding 幂等，label/count 不同则 409。
 */
public class AllocateRequest {
    private String userBinding;
    private String label;
    private Integer count;

    public AllocateRequest() {}

    public AllocateRequest(String userBinding, String label, int count) {
        this.userBinding = userBinding;
        this.label = label;
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

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
