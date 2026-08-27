package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 分配子地址结果：{ allocated: [...] } 中的一项。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllocatedAddress {
    private String address;
    private int index;
    private String userBinding;
    private String label;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
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
