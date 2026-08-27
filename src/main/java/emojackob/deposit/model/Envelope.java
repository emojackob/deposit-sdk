package emojackob.deposit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 统一响应/回调信封：{ project, data, err }。
 * 判定规则：err != null 即失败（data 为 null）；成功时 err == null。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Envelope<T> {
    private String project;
    private T data;
    private ErrInfo err;

    public boolean isSuccess() {
        return err == null;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ErrInfo getErr() {
        return err;
    }

    public void setErr(ErrInfo err) {
        this.err = err;
    }
}
