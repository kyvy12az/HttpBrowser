package vn.edu.network.server;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import vn.edu.network.common.BrowserRequest;
import vn.edu.network.common.BrowserResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class HttpProxyService {
    private static final int MAX_BODY_CHARS = 5_000_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    BrowserResponse execute(BrowserRequest browserRequest) {
        long startedAt = System.nanoTime();

        try {
            URI uri = normalizeUri(browserRequest.url());
            validateUri(uri);

            HttpRequest request = createRequest(browserRequest, uri);
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            long elapsed = (System.nanoTime() - startedAt) / 1_000_000;
            String body = browserRequest.method() == vn.edu.network.common.HttpMethodType.HEAD
                    ? ""
                    : limitBody(response.body());

            Map<String, String> headers = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) ->
                    headers.put(name, String.join(", ", values)));

            int p = 0, div = 0, span = 0, img = 0;
            if (!body.isBlank()) {
                Document document = Jsoup.parse(body, response.uri().toString());
                p = document.select("p").size();
                div = document.select("div").size();
                span = document.select("span").size();
                img = document.select("img").size();
            }

            return new BrowserResponse(
                    true,
                    response.statusCode(),
                    reasonPhrase(response.statusCode()),
                    response.uri().toString(),
                    headers,
                    body,
                    elapsed,
                    body.length(),
                    p,
                    div,
                    span,
                    img,
                    ""
            );
        } catch (Exception e) {
            return BrowserResponse.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private HttpRequest createRequest(BrowserRequest request, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "SimpleHttpBrowser/1.0 (Java 17)")
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.7");

        return switch (request.method()) {
            case GET -> builder.GET().build();
            case HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            case POST -> builder
                    .header("Content-Type", detectContentType(request.body()))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            request.body() == null ? "" : request.body(),
                            StandardCharsets.UTF_8))
                    .build();
            default -> throw new IllegalArgumentException("Phương thức không được hỗ trợ.");
        };
    }

    private URI normalizeUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("URL không được để trống.");
        }
        String url = rawUrl.trim();
        if (!url.matches("(?i)^https?://.*")) {
            url = "https://" + url;
        }
        return URI.create(url);
    }

    private void validateUri(URI uri) {
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Chỉ hỗ trợ URL HTTP hoặc HTTPS.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Tên miền trong URL không hợp lệ.");
        }
    }

    private String detectContentType(String body) {
        String value = body == null ? "" : body.stripLeading();
        return value.startsWith("{") || value.startsWith("[")
                ? "application/json; charset=UTF-8"
                : "application/x-www-form-urlencoded; charset=UTF-8";
    }

    private String limitBody(String body) {
        if (body == null || body.length() <= MAX_BODY_CHARS) {
            return body == null ? "" : body;
        }
        return body.substring(0, MAX_BODY_CHARS)
                + "\n<!-- Nội dung đã được cắt bớt tại 5.000.000 ký tự -->";
    }

    private String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "HTTP " + status;
        };
    }
}
