package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 不可变充值地址分配（POST / GET 同一结构）：
 * { user_binding, label, count, allocated: [...] }。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressAllocation {
    private String userBinding;
    private String label;
    private int count;
    private List<AllocatedAddress> allocated;

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

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<AllocatedAddress> getAllocated() {
        return allocated;
    }

    public void setAllocated(List<AllocatedAddress> allocated) {
        this.allocated = allocated;
    }
}
