package vn.edu.network.client;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

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

        JButton downloadImageButton = new JButton("Tải ảnh", ClientIcons.icon("image", 15));
        downloadImageButton.setToolTipText("Tải một hình ảnh đang hiển thị trên trang");
        downloadImageButton.setMargin(new Insets(3, 10, 3, 10));
        downloadImageButton.setFocusable(false);
        downloadImageButton.addActionListener(event -> chooseImageToDownload());

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

        toolbar.add(downloadImageButton);
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

    private void chooseImageToDownload() {
        if (!ready.get() || engine == null) {
            JOptionPane.showMessageDialog(this, "Trang web chưa tải xong.",
                    "Chưa thể tải ảnh", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Platform.runLater(() -> {
            try {
                String script = """
                        Array.from(new Set(
                            Array.from(document.images)
                                .map(image => image.currentSrc || image.src)
                                .filter(url => url.startsWith('http://') || url.startsWith('https://'))
                        )).join('|||HTTP_BROWSER_IMAGE|||')
                        """;
                Object result = engine.executeScript(script);
                String value = result == null ? "" : result.toString();
                String[] imageUrls = value.isBlank()
                        ? new String[0]
                        : value.split(Pattern.quote("|||HTTP_BROWSER_IMAGE|||"));

                SwingUtilities.invokeLater(() -> showImageChooser(imageUrls));
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> showDownloadError(exception));
            }
        });
    }

    private void showImageChooser(String[] imageUrls) {
        if (imageUrls.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy hình ảnh HTTP/HTTPS trong trang hiện tại.",
                    "Không có hình ảnh", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JComboBox<String> imageBox = new JComboBox<>(imageUrls);
        imageBox.setPreferredSize(new Dimension(620, 32));
        JPanel selectionPanel = new JPanel(new BorderLayout(0, 8));
        selectionPanel.add(new javax.swing.JLabel(
                "Chọn hình ảnh muốn tải (" + imageUrls.length + " ảnh):"), BorderLayout.NORTH);
        selectionPanel.add(imageBox, BorderLayout.CENTER);

        int option = JOptionPane.showConfirmDialog(this, selectionPanel,
                "Tải hình ảnh", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION || imageBox.getSelectedItem() == null) {
            return;
        }

        String imageUrl = imageBox.getSelectedItem().toString();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn vị trí lưu hình ảnh");
        chooser.setSelectedFile(new File(suggestFileName(imageUrl)));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File destination = chooser.getSelectedFile();
            if (destination.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(this,
                        "Tệp đã tồn tại. Bạn có muốn ghi đè không?",
                        "Xác nhận ghi đè", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (overwrite != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            downloadImage(imageUrl, destination, imageBox);
        }
    }

    private void downloadImage(String imageUrl, File destination, JComponent owner) {
        owner.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "SimpleHttpBrowser/1.0 (Java 17)")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = client.send(
                        request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Website trả về HTTP " + response.statusCode());
                }
                Files.write(destination.toPath(), response.body());
                return null;
            }

            @Override
            protected void done() {
                owner.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(WebPreviewPanel.this,
                            "Đã lưu hình ảnh tại:\n" + destination.getAbsolutePath(),
                            "Tải ảnh thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    showDownloadError(exception);
                }
            }
        }.execute();
    }

    private String suggestFileName(String imageUrl) {
        try {
            String path = URI.create(imageUrl).getPath();
            String fileName = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            return fileName.isBlank() ? "image.jpg" : fileName;
        } catch (Exception exception) {
            return "image.jpg";
        }
    }

    private void showDownloadError(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
        JOptionPane.showMessageDialog(this,
                "Không thể tải hình ảnh.\n" + message,
                "Tải ảnh thất bại", JOptionPane.ERROR_MESSAGE);
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
