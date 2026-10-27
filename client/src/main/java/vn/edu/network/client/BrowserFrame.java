package vn.edu.network.client;

import vn.edu.network.common.BrowserRequest;
import vn.edu.network.common.BrowserResponse;
import vn.edu.network.common.HttpMethodType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.Map;

final class BrowserFrame extends JFrame {
    private static final Color BLUE = new Color(37, 99, 235);
    private static final Color GREEN = new Color(22, 163, 74);
    private static final Color RED = new Color(220, 38, 38);

    private final ServerConnection connection = new ServerConnection("10.60.4.27", 9090);
    private final JTextField urlField = new JTextField("https://example.com");
    private final JComboBox<HttpMethodType> methodBox = new JComboBox<>(
            new HttpMethodType[]{HttpMethodType.GET, HttpMethodType.POST, HttpMethodType.HEAD});
    private final JButton sendButton = new JButton("Gửi yêu cầu",
            ClientIcons.icon("send", 17, Color.WHITE));
    private final JButton clearButton = new JButton("Xóa", ClientIcons.icon("trash", 16));
    private final JButton reconnectButton = new JButton("Kết nối lại",
            ClientIcons.icon("refresh-cw", 15));
    private final JTextArea postArea = new JTextArea("name=KyVy&message=Hello", 3, 30);
    private final JTextArea sourceArea = createTextArea();
    private final JTextArea headersArea = createTextArea();
    private final WebPreviewPanel previewPanel = new WebPreviewPanel();

    private final JLabel onlineLabel = new JLabel("Đang kiểm tra...");
    private final JLabel statusValue = new JLabel("—");
    private final JLabel typeValue = new JLabel("—");
    private final JLabel timeValue = new JLabel("—");
    private final JLabel lengthValue = new JLabel("—");
    private final JLabel pValue = statValue("0");
    private final JLabel divValue = statValue("0");
    private final JLabel spanValue = statValue("0");
    private final JLabel imgValue = statValue("0");
    private final JProgressBar progressBar = new JProgressBar();

    BrowserFrame() {
        super("Simple HTTP Browser - Java Swing Client");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1450, 850);
        setLocationRelativeTo(null);
        setIconImage(ClientIcons.icon("globe", 24, BLUE).getImage());

        setContentPane(buildContent());
        configureEvents();
        updatePostState();
        checkServer();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 10, 16));
        root.add(buildTop(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildTop() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));

        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        JLabel urlLabel = new JLabel("URL:", ClientIcons.icon("search", 18, BLUE), SwingConstants.LEFT);
        urlLabel.setIconTextGap(7);
        urlLabel.setFont(urlLabel.getFont().deriveFont(Font.BOLD));
        toolbar.add(urlLabel, BorderLayout.WEST);
        toolbar.add(urlField, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        methodBox.setPreferredSize(new Dimension(110, 36));
        sendButton.setBackground(BLUE);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(sendButton.getFont().deriveFont(Font.BOLD));
        sendButton.setPreferredSize(new Dimension(145, 36));
        clearButton.setPreferredSize(new Dimension(90, 36));
        actions.add(methodBox);
        actions.add(sendButton);
        actions.add(clearButton);
        toolbar.add(actions, BorderLayout.EAST);

        JPanel postPanel = new JPanel(new BorderLayout(6, 6));
        postPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                new EmptyBorder(7, 9, 9, 9)));
        JLabel postTitle = new JLabel("Dữ liệu POST (JSON hoặc form data)",
                ClientIcons.icon("code-xml", 16, BLUE), SwingConstants.LEFT);
        postTitle.setFont(postTitle.getFont().deriveFont(Font.BOLD));
        postTitle.setIconTextGap(7);
        postPanel.add(postTitle, BorderLayout.NORTH);
        postArea.setLineWrap(true);
        postArea.setWrapStyleWord(true);
        postPanel.add(new JScrollPane(postArea), BorderLayout.CENTER);

        wrapper.add(toolbar, BorderLayout.NORTH);
        wrapper.add(postPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private JSplitPane buildCenter() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Xem trước", ClientIcons.icon("globe", 16, BLUE), previewPanel);
        tabs.addTab("Mã nguồn HTML", ClientIcons.icon("code-xml", 16, BLUE),
                new JScrollPane(sourceArea));
        tabs.addTab("Headers", ClientIcons.icon("file-text", 16, BLUE),
                new JScrollPane(headersArea));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, buildInfoPanel());
        split.setResizeWeight(0.78);
        split.setDividerLocation(1000);
        split.setContinuousLayout(true);
        return split;
    }

    private JPanel buildInfoPanel() {
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(0, 8, 0, 0));
        info.setPreferredSize(new Dimension(330, 600));

        JPanel responseCard = card("Thông tin phản hồi", "info");
        responseCard.add(infoRow("Trạng thái:", statusValue, "circle-check-big"));
        responseCard.add(infoRow("Content-Type:", typeValue, "file-text"));
        responseCard.add(infoRow("Thời gian:", timeValue, "clock-3"));
        responseCard.add(infoRow("Chiều dài HTML:", lengthValue, "file-code-corner"));

        JPanel statsCard = card("Thống kê thẻ HTML", "chart-no-axes-column-increasing");
        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.add(statTile("<p>", "file-text", pValue, BLUE, new Color(219, 234, 254)));
        grid.add(statTile("<div>", "code-xml", divValue, GREEN, new Color(220, 252, 231)));
        grid.add(statTile("<span>", "file-code-corner", spanValue, new Color(180, 95, 10),
                new Color(254, 249, 195)));
        grid.add(statTile("<img>", "image", imgValue, RED, new Color(254, 226, 226)));
        statsCard.add(grid);

        info.add(responseCard);
        info.add(Box.createVerticalStrut(10));
        info.add(statsCard);
        info.add(Box.createVerticalGlue());
        return info;
    }

    private JPanel buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout(8, 0));
        status.setBorder(new EmptyBorder(5, 3, 0, 3));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(onlineLabel);
        reconnectButton.setMargin(new Insets(3, 9, 3, 9));
        left.add(reconnectButton);
        status.add(left, BorderLayout.WEST);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(180, 8));
        status.add(progressBar, BorderLayout.EAST);
        return status;
    }

    private void configureEvents() {
        previewPanel.setLocationListener(urlField::setText);
        methodBox.addActionListener(e -> updatePostState());
        sendButton.addActionListener(e -> sendRequest());
        clearButton.addActionListener(e -> clearResult());
        reconnectButton.addActionListener(e -> checkServer());
        urlField.addActionListener(e -> sendRequest());
        getRootPane().registerKeyboardAction(e -> sendRequest(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void checkServer() {
        setBusy(true);
        onlineLabel.setText("Đang kiểm tra Server...");
        onlineLabel.setIcon(ClientIcons.icon("circle", 11, new Color(100, 116, 139)));
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return connection.ping();
            }

            @Override
            protected void done() {
                try {
                    setOnline(get());
                } catch (Exception e) {
                    setOnline(false);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void sendRequest() {
        String url = urlField.getText().trim();
        if (url.isBlank()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập URL.",
                    "Thiếu URL", JOptionPane.WARNING_MESSAGE);
            return;
        }

        HttpMethodType method = (HttpMethodType) methodBox.getSelectedItem();
        BrowserRequest request = new BrowserRequest(method, url, postArea.getText());
        setBusy(true);

        new SwingWorker<BrowserResponse, Void>() {
            @Override
            protected BrowserResponse doInBackground() throws Exception {
                return connection.send(request);
            }

            @Override
            protected void done() {
                try {
                    BrowserResponse response = get();
                    setOnline(true);
                    displayResponse(request.method(), response);
                } catch (Exception e) {
                    setOnline(false);
                    showConnectionError(e);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void displayResponse(HttpMethodType method, BrowserResponse response) {
        if (!response.success()) {
            JOptionPane.showMessageDialog(this,
                    "Server không thể xử lý request:\n" + response.errorMessage(),
                    "Request thất bại", JOptionPane.ERROR_MESSAGE);
            statusValue.setText("Lỗi");
            statusValue.setForeground(RED);
            return;
        }

        if (response.finalUrl() != null && !response.finalUrl().isBlank()) {
            urlField.setText(response.finalUrl());
        }
        statusValue.setText(response.statusCode() + " " + response.reasonPhrase());
        statusValue.setForeground(response.statusCode() < 400 ? GREEN : RED);
        typeValue.setText(header(response.headers(), "content-type", "Không xác định"));
        timeValue.setText(response.elapsedMillis() + " ms");
        lengthValue.setText(NumberFormat.getIntegerInstance().format(response.contentLength()) + " ký tự");
        pValue.setText(String.valueOf(response.pCount()));
        divValue.setText(String.valueOf(response.divCount()));
        spanValue.setText(String.valueOf(response.spanCount()));
        imgValue.setText(String.valueOf(response.imgCount()));
        sourceArea.setText(response.body());
        sourceArea.setCaretPosition(0);
        headersArea.setText(formatHeaders(response));
        headersArea.setCaretPosition(0);

        if (method == HttpMethodType.HEAD) {
            previewPanel.showHeadMessage();
        } else {
            previewPanel.showHtml(response.body(), response.finalUrl());
        }
    }

    private String formatHeaders(BrowserResponse response) {
        StringBuilder text = new StringBuilder()
                .append("HTTP ").append(response.statusCode()).append(' ')
                .append(response.reasonPhrase()).append("\n")
                .append("Final-URL: ").append(response.finalUrl()).append("\n\n");
        response.headers().forEach((name, value) ->
                text.append(name).append(": ").append(value).append('\n'));
        return text.toString();
    }

    private String header(Map<String, String> headers, String name, String fallback) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(fallback);
    }

    private void clearResult() {
        sourceArea.setText("");
        headersArea.setText("");
        statusValue.setText("—");
        statusValue.setForeground(UIManager.getColor("Label.foreground"));
        typeValue.setText("—");
        timeValue.setText("—");
        lengthValue.setText("—");
        pValue.setText("0");
        divValue.setText("0");
        spanValue.setText("0");
        imgValue.setText("0");
        previewPanel.clear();
    }

    private void updatePostState() {
        boolean enabled = methodBox.getSelectedItem() == HttpMethodType.POST;
        postArea.setEnabled(enabled);
        postArea.setBackground(enabled ? UIManager.getColor("TextArea.background")
                : UIManager.getColor("TextField.inactiveBackground"));
    }

    private void setOnline(boolean online) {
        onlineLabel.setText(online ? "Server Online"
                : "Server Offline");
        onlineLabel.setForeground(online ? GREEN : RED);
        onlineLabel.setIcon(ClientIcons.icon("circle", 11, online ? GREEN : RED));
        onlineLabel.setIconTextGap(7);
        sendButton.setEnabled(online);
    }

    private void setBusy(boolean busy) {
        progressBar.setVisible(busy);
        sendButton.setEnabled(!busy && onlineLabel.getForeground().equals(GREEN));
        reconnectButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
    }

    private void showConnectionError(Exception e) {
        JOptionPane.showMessageDialog(this,
                "Không thể kết nối đến Java Server tại localhost:9090.\n"
                        + "Hãy chạy Server trước rồi nhấn Kết nối lại.\n\nChi tiết: " + rootMessage(e),
                "Server Offline", JOptionPane.ERROR_MESSAGE);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static JTextArea createTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setTabSize(2);
        return area;
    }

    private JPanel card(String title, String iconName) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                new EmptyBorder(12, 12, 12, 12)));
        JLabel heading = new JLabel(title, ClientIcons.icon(iconName, 18, BLUE), SwingConstants.LEFT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        heading.setIconTextGap(8);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(12));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        return panel;
    }

    private JPanel infoRow(String name, JLabel value, String iconName) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(new EmptyBorder(7, 0, 7, 0));
        JLabel nameLabel = new JLabel(name, ClientIcons.icon(iconName, 14, new Color(100, 116, 139)),
                SwingConstants.LEFT);
        nameLabel.setIconTextGap(6);
        row.add(nameLabel, BorderLayout.WEST);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        value.setToolTipText(value.getText());
        row.add(value, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return row;
    }

    private JPanel statTile(String tag, String iconName, JLabel value, Color accent, Color background) {
        JPanel tile = new JPanel();
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setBackground(background);
        tile.setBorder(new EmptyBorder(12, 8, 12, 8));
        JLabel label = new JLabel(tag, ClientIcons.icon(iconName, 15, accent), SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setIconTextGap(5);
        value.setAlignmentX(Component.CENTER_ALIGNMENT);
        tile.add(label);
        tile.add(Box.createVerticalStrut(5));
        tile.add(value);
        return tile;
    }

    private static JLabel statValue(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        return label;
    }
}
