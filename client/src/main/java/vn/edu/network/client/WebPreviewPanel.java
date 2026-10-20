package vn.edu.network.client;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WebPreviewPanel extends JPanel {
    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 2.0;
    private static final double ZOOM_STEP = 0.1;
    private static final int MAX_HISTORY_ENTRIES = 200;
    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    private final JFXPanel fxPanel = new JFXPanel();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final JButton backButton = new JButton("←");
    private final JButton forwardButton = new JButton("→");
    private final JButton zoomValueButton = new JButton("100%");
    private volatile WebEngine engine;
    private volatile WebView webView;
    private volatile String pendingHtml = welcomeHtml();
    private volatile double zoom = 1.0;
    private final List<AccessHistoryEntry> accessHistory = new ArrayList<>();
    private volatile Consumer<String> locationListener = url -> { };

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
            configureHistory();
            engine.locationProperty().addListener(
                    (observable, oldUrl, newUrl) -> notifyLocationChanged(newUrl));
            engine.getLoadWorker().stateProperty().addListener(
                    (observable, oldState, newState) -> {
                        if (newState == Worker.State.SUCCEEDED) {
                            recordHistory(engine.getLocation(), engine.getTitle());
                        }
                    });
            view.setZoom(zoom);
            fxPanel.setScene(new Scene(view));
            ready.set(true);
            engine.loadContent(pendingHtml, "text/html");
        });
    }

    void setLocationListener(Consumer<String> listener) {
        locationListener = listener == null ? url -> { } : listener;
    }

    private void notifyLocationChanged(String url) {
        if (url == null || url.isBlank()
                || url.equals("about:blank")
                || url.startsWith("data:")) {
            return;
        }
        SwingUtilities.invokeLater(() -> locationListener.accept(url));
    }

    void showHtml(String html, String baseUrl) {
        String content = prepareHtml(html, baseUrl);
        pendingHtml = content;
        recordHistory(baseUrl, extractTitle(html));
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

        backButton.setToolTipText("Quay lại trang trước (Alt+←)");
        backButton.setMargin(new Insets(3, 10, 3, 10));
        backButton.setFocusable(false);
        backButton.setEnabled(false);
        backButton.addActionListener(event -> navigateHistory(-1));

        forwardButton.setToolTipText("Đi tới trang sau (Alt+→)");
        forwardButton.setMargin(new Insets(3, 10, 3, 10));
        forwardButton.setFocusable(false);
        forwardButton.setEnabled(false);
        forwardButton.addActionListener(event -> navigateHistory(1));

        JButton historyButton = new JButton("Lịch sử", ClientIcons.icon("clock-3", 15));
        historyButton.setToolTipText("Xem lịch sử truy cập");
        historyButton.setMargin(new Insets(3, 10, 3, 10));
        historyButton.setFocusable(false);
        historyButton.addActionListener(event -> showAccessHistory());

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

        toolbar.add(backButton);
        toolbar.add(forwardButton);
        toolbar.add(historyButton);
        toolbar.add(downloadImageButton);
        toolbar.add(zoomOutButton);
        toolbar.add(zoomValueButton);
        toolbar.add(zoomInButton);
        return toolbar;
    }

    private void configureZoomShortcuts() {
        registerKeyboardAction(event -> navigateHistory(-1),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(event -> navigateHistory(1),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
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

    private void configureHistory() {
        WebHistory history = engine.getHistory();
        history.currentIndexProperty().addListener(
                (observable, oldValue, newValue) -> updateNavigationButtons());
        history.getEntries().addListener(
                (ListChangeListener<WebHistory.Entry>) change -> updateNavigationButtons());
        updateNavigationButtons();
    }

    private void navigateHistory(int offset) {
        if (!ready.get() || engine == null) {
            return;
        }
        Platform.runLater(() -> {
            WebHistory history = engine.getHistory();
            int targetIndex = history.getCurrentIndex() + offset;
            if (targetIndex >= 0 && targetIndex < history.getEntries().size()) {
                history.go(offset);
            }
            updateNavigationButtons();
        });
    }

    private void updateNavigationButtons() {
        if (engine == null) {
            return;
        }
        WebHistory history = engine.getHistory();
        int currentIndex = history.getCurrentIndex();
        int entryCount = history.getEntries().size();
        SwingUtilities.invokeLater(() -> {
            backButton.setEnabled(currentIndex > 0);
            forwardButton.setEnabled(currentIndex >= 0 && currentIndex < entryCount - 1);
        });
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

    private void recordHistory(String url, String title) {
        if (url == null || url.isBlank()
                || url.equals("about:blank")
                || url.startsWith("data:")) {
            return;
        }

        String normalizedTitle = title == null || title.isBlank()
                ? url
                : title.replaceAll("\\s+", " ").trim();

        synchronized (accessHistory) {
            if (!accessHistory.isEmpty()) {
                AccessHistoryEntry latest = accessHistory.get(accessHistory.size() - 1);
                if (latest.url().equals(url)) {
                    return;
                }
            }
            accessHistory.add(new AccessHistoryEntry(
                    LocalDateTime.now(), normalizedTitle, url));
            if (accessHistory.size() > MAX_HISTORY_ENTRIES) {
                accessHistory.remove(0);
            }
        }
    }

    private String extractTitle(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1)
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim();
    }

    private void showAccessHistory() {
        List<AccessHistoryEntry> snapshot;
        synchronized (accessHistory) {
            snapshot = new ArrayList<>(accessHistory);
        }

        if (snapshot.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Chưa có địa chỉ nào trong lịch sử truy cập.",
                    "Lịch sử truy cập", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DefaultListModel<String> model = new DefaultListModel<>();
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            AccessHistoryEntry entry = snapshot.get(index);
            model.addElement(entry.visitedAt().format(HISTORY_TIME)
                    + "  •  " + entry.title() + "  •  " + entry.url());
        }

        JList<String> historyList = new JList<>(model);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setSelectedIndex(0);
        JScrollPane scrollPane = new JScrollPane(historyList);
        scrollPane.setPreferredSize(new Dimension(760, 340));

        Object[] options = {"Mở lại", "Xóa lịch sử", "Đóng"};
        int option = JOptionPane.showOptionDialog(this, scrollPane,
                "Lịch sử truy cập (" + snapshot.size() + " địa chỉ)",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (option == 0 && historyList.getSelectedIndex() >= 0) {
            int snapshotIndex = snapshot.size() - 1 - historyList.getSelectedIndex();
            String selectedUrl = snapshot.get(snapshotIndex).url();
            Platform.runLater(() -> engine.load(selectedUrl));
        } else if (option == 1) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc muốn xóa toàn bộ lịch sử truy cập?",
                    "Xác nhận xóa", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                synchronized (accessHistory) {
                    accessHistory.clear();
                }
            }
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

    private record AccessHistoryEntry(
            LocalDateTime visitedAt,
            String title,
            String url
    ) {
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
