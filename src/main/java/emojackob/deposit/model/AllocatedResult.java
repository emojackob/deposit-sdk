package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 分配子地址响应 data：{ allocated: [...] }。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllocatedResult {
    private List<AllocatedAddress> allocated;

    public List<AllocatedAddress> getAllocated() {
        return allocated;
    }

    public void setAllocated(List<AllocatedAddress> allocated) {
        this.allocated = allocated;
    }
}
