package vn.edu.network.common;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public record BrowserResponse(
        boolean success,
        int statusCode,
        String reasonPhrase,
        String finalUrl,
        Map<String, String> headers,
        String body,
        long elapsedMillis,
        int contentLength,
        int pCount,
        int divCount,
        int spanCount,
        int imgCount,
        String errorMessage
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public BrowserResponse {
        headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        body = body == null ? "" : body;
        reasonPhrase = reasonPhrase == null ? "" : reasonPhrase;
        finalUrl = finalUrl == null ? "" : finalUrl;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static BrowserResponse pong() {
        return new BrowserResponse(true, 200, "PONG", "", Map.of(), "",
                0, 0, 0, 0, 0, 0, "");
    }

    public static BrowserResponse error(String message) {
        return new BrowserResponse(false, 0, "", "", Map.of(), "",
                0, 0, 0, 0, 0, 0, message);
    }
}
