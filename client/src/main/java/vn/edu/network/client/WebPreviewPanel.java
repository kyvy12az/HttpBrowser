package vn.edu.network.client;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

final class WebPreviewPanel extends JPanel {
    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 2.0;
    private static final double ZOOM_STEP = 0.1;

    private final JFXPanel fxPanel = new JFXPanel();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final JButton zoomValueButton = new JButton("100%");
    private volatile WebEngine engine;
    private volatile WebView webView;
    private volatile String pendingHtml = welcomeHtml();
    private volatile double zoom = 1.0;

    WebPreviewPanel() {
        super(new BorderLayout());
        add(buildZoomToolbar(), BorderLayout.NORTH);
        add(fxPanel, BorderLayout.CENTER);
        configureZoomShortcuts();

        Platform.setImplicitExit(false);
        Platform.runLater(() -> {
            WebView view = new WebView();
            webView = view;
            engine = view.getEngine();
            engine.setJavaScriptEnabled(true);
            view.setZoom(zoom);
            fxPanel.setScene(new Scene(view));
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
        resetZoom();
    }

    private JPanel buildZoomToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 4));

        JButton zoomOutButton = new JButton("−");
        zoomOutButton.setToolTipText("Thu nhỏ (Ctrl+-)");
        zoomOutButton.setMargin(new Insets(3, 10, 3, 10));
        zoomOutButton.setFocusable(false);
        zoomOutButton.addActionListener(event -> changeZoom(-ZOOM_STEP));

        zoomValueButton.setToolTipText("Đặt lại mức thu phóng 100% (Ctrl+0)");
        zoomValueButton.setMargin(new Insets(3, 10, 3, 10));
        zoomValueButton.setFocusable(false);
        zoomValueButton.addActionListener(event -> resetZoom());

        JButton zoomInButton = new JButton("+");
        zoomInButton.setToolTipText("Phóng to (Ctrl++)");
        zoomInButton.setMargin(new Insets(3, 10, 3, 10));
        zoomInButton.setFocusable(false);
        zoomInButton.addActionListener(event -> changeZoom(ZOOM_STEP));

        toolbar.add(zoomOutButton);
        toolbar.add(zoomValueButton);
        toolbar.add(zoomInButton);
        return toolbar;
    }

    private void configureZoomShortcuts() {
        registerKeyboardAction(event -> changeZoom(ZOOM_STEP),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(event -> changeZoom(ZOOM_STEP),
                KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(event -> changeZoom(-ZOOM_STEP),
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(event -> changeZoom(-ZOOM_STEP),
                KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(event -> resetZoom(),
                KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void changeZoom(double amount) {
        setZoom(zoom + amount);
    }

    private void resetZoom() {
        setZoom(1.0);
    }

    private void setZoom(double requestedZoom) {
        double bounded = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
        zoom = Math.round(bounded * 10.0) / 10.0;
        zoomValueButton.setText(Math.round(zoom * 100) + "%");

        WebView currentView = webView;
        if (ready.get() && currentView != null) {
            Platform.runLater(() -> currentView.setZoom(zoom));
        }
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
