package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 分页结果：{ items, total, page, page_size }。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Page<T> {
    private List<T> items;
    private long total;
    private int page;
    private int pageSize;

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
