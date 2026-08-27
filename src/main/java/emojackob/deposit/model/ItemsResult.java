package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 批量结果响应 data：{ items: [...] }。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemsResult<T> {
    private List<T> items;

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }
}
