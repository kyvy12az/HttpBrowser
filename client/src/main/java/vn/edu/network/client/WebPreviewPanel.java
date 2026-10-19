package vn.edu.network.client;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.concurrent.atomic.AtomicBoolean;

final class WebPreviewPanel extends JPanel {
    private final JFXPanel fxPanel = new JFXPanel();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile WebEngine engine;
    private volatile String pendingHtml = welcomeHtml();

    WebPreviewPanel() {
        super(new BorderLayout());
        add(fxPanel, BorderLayout.CENTER);

        Platform.setImplicitExit(false);
        Platform.runLater(() -> {
            WebView webView = new WebView();
            engine = webView.getEngine();
            engine.setJavaScriptEnabled(true);
            fxPanel.setScene(new Scene(webView));
            ready.set(true);
            engine.loadContent(pendingHtml, "text/html");
        });
    }

    void showHtml(String html, String baseUrl) {
        String content = prepareHtml(html, baseUrl);
        pendingHtml = content;
        if (ready.get()) {
            Platform.runLater(() -> engine.loadContent(content, "text/html"));
        }
    }

    void showHeadMessage() {
        showHtml("""
                <!doctype html><html><body style='font-family:Segoe UI,sans-serif;padding:48px;color:#334155'>
                <h2>HEAD không có nội dung trang</h2>
                <p>Phương thức HEAD chỉ trả về thông tin tài nguyên. Hãy mở tab <b>Headers</b>.</p>
                </body></html>
                """, "");
    }

    void clear() {
        showHtml(welcomeHtml(), "");
    }

    private String prepareHtml(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            return "<html><body></body></html>";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return html;
        }
        String escaped = baseUrl.replace("&", "&amp;").replace("\"", "&quot;");
        String base = "<base href=\"" + escaped + "\">";
        if (html.matches("(?is).*<head[^>]*>.*")) {
            return html.replaceFirst("(?i)<head([^>]*)>", "<head$1>" + base);
        }
        return "<head>" + base + "</head>" + html;
    }

    private static String welcomeHtml() {
        String globeIcon = WebPreviewPanel.class.getResource("/icons/globe.svg").toExternalForm();
        return """
                <!doctype html><html><body style='margin:0;background:#f8fafc;font-family:Segoe UI,sans-serif;'>
                <div style='height:100vh;display:flex;align-items:center;justify-content:center;color:#64748b;text-align:center'>
                  <div><img src='%s' width='54' height='54' alt='Browser'><h2 style='color:#334155'>Simple HTTP Browser</h2>
                  <p>Nhập URL, chọn GET/POST/HEAD và nhấn “Gửi yêu cầu”.</p></div>
                </div></body></html>
                """.formatted(globeIcon);
    }
}
