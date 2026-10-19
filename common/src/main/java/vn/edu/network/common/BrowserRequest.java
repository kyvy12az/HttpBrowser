package vn.edu.network.common;

import java.io.Serial;
import java.io.Serializable;

public record BrowserRequest(HttpMethodType method, String url, String body)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static BrowserRequest ping() {
        return new BrowserRequest(HttpMethodType.PING, "", "");
    }
}
