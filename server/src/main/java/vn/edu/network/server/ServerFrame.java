package vn.edu.network.server;

import vn.edu.network.common.BrowserRequest;
import vn.edu.network.common.HttpMethodType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class ServerFrame extends JFrame implements ServerController.Listener {
    private static final Color BLUE = new Color(30, 104, 238);
    private static final Color GREEN = new Color(17, 139, 76);
    private static final Color RED = new Color(210, 47, 55);
    private static final Color ORANGE = new Color(226, 139, 18);
    private static final Color PURPLE = new Color(109, 40, 217);
    private static final Color BORDER = new Color(214, 221, 230);
    private static final Color LOG_BACKGROUND = new Color(24, 31, 43);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField hostField = new JTextField("0.0.0.0");
    private final JTextField portField = new JTextField("9090");
    private final JButton startButton = new JButton("Khởi động",
            ServerIcons.icon("play", 17, Color.WHITE));
    private final JButton stopButton = new JButton("Dừng Server",
            ServerIcons.icon("square", 16, Color.WHITE));
    private final JLabel serverState = new JLabel("Server Offline",
            ServerIcons.icon("circle", 11, RED), SwingConstants.CENTER);
    private final JLabel totalValue = statValue();
    private final JLabel activeValue = statValue();
    private final JLabel successValue = statValue();
    private final JLabel errorValue = statValue();
    private final JLabel listeningLabel = new JLabel("Server chưa khởi động",
            ServerIcons.icon("circle", 11, RED), SwingConstants.LEFT);
    private final RequestTableModel tableModel = new RequestTableModel();
    private final JTable requestTable = new JTable(tableModel);
    private final JTextPane logPane = new JTextPane();
    private final ServerController controller = new ServerController(this);
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();

    ServerFrame() {
        super("Simple HTTP Browser Server");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 700));
        setSize(1500, 850);
        setLocationRelativeTo(null);
        setIconImage(ServerIcons.icon("server", 24, BLUE).getImage());
        setContentPane(buildContent());
        configureEvents();
        setServerRunning(false);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 18, 10, 18));

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.add(buildControlPanel(), BorderLayout.NORTH);
        top.add(buildStatisticsPanel(), BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        root.add(buildMainArea(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildControlPanel() {
        JPanel panel = cardPanel(new BorderLayout(16, 10));
        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);
        JLabel title = new JLabel("Điều khiển Server",
                ServerIcons.icon("server", 23, BLUE), SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
        title.setIconTextGap(9);
        left.add(title, BorderLayout.NORTH);

        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        fields.setOpaque(false);
        JLabel hostLabel = new JLabel("Địa chỉ IP", ServerIcons.icon("server", 16, BLUE),
                SwingConstants.LEFT);
        hostLabel.setIconTextGap(6);
        fields.add(hostLabel);
        hostField.setPreferredSize(new Dimension(220, 38));
        fields.add(hostField);
        fields.add(Box.createHorizontalStrut(10));
        JLabel portLabel = new JLabel("Cổng", ServerIcons.icon("terminal", 16, BLUE),
                SwingConstants.LEFT);
        portLabel.setIconTextGap(6);
        fields.add(portLabel);
        portField.setPreferredSize(new Dimension(150, 38));
        fields.add(portField);
        left.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 24));
        actions.setOpaque(false);
        stylePrimaryButton(startButton, BLUE);
        stylePrimaryButton(stopButton, new Color(100, 116, 139));
        serverState.setOpaque(true);
        serverState.setFont(serverState.getFont().deriveFont(Font.BOLD, 14f));
        serverState.setBorder(new EmptyBorder(11, 18, 11, 18));
        actions.add(startButton);
        actions.add(stopButton);
        actions.add(Box.createHorizontalStrut(22));
        actions.add(serverState);

        panel.add(left, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildStatisticsPanel() {
        JPanel stats = new JPanel(new GridLayout(1, 4, 14, 0));
        stats.add(statCard("file-text", "Tổng request", totalValue, BLUE, new Color(225, 238, 255)));
        stats.add(statCard("cog", "Đang xử lý", activeValue, ORANGE, new Color(255, 244, 214)));
        stats.add(statCard("circle-check-big", "Thành công", successValue, GREEN,
                new Color(224, 247, 233)));
        stats.add(statCard("circle-alert", "Lỗi", errorValue, RED, new Color(255, 230, 232)));
        return stats;
    }

    private JSplitPane buildMainArea() {
        JPanel history = cardPanel(new BorderLayout(0, 10));
        history.add(sectionTitle("Lịch sử request", "list"), BorderLayout.NORTH);
        configureTable();
        history.add(new JScrollPane(requestTable), BorderLayout.CENTER);

        JPanel logs = cardPanel(new BorderLayout(0, 10));
        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        logHeader.add(sectionTitle("Nhật ký Server", "terminal"), BorderLayout.WEST);
        JButton clearLogButton = new JButton("Xóa nhật ký", ServerIcons.icon("trash", 16));
        clearLogButton.addActionListener(e -> logPane.setText(""));
        logHeader.add(clearLogButton, BorderLayout.EAST);
        logs.add(logHeader, BorderLayout.NORTH);
        logPane.setEditable(false);
        logPane.setBackground(LOG_BACKGROUND);
        logPane.setForeground(new Color(218, 226, 238));
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        logPane.setBorder(new EmptyBorder(7, 8, 7, 8));
        logs.add(new JScrollPane(logPane), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, history, logs);
        split.setResizeWeight(0.68);
        split.setDividerLocation(980);
        split.setContinuousLayout(true);
        split.setBorder(null);
        return split;
    }

    private JPanel buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(1, 4, 0, 4));
        listeningLabel.setForeground(RED);
        status.add(listeningLabel, BorderLayout.WEST);
        JLabel runtimeLabel = new JLabel("TCP Server  •  Java 17",
                ServerIcons.icon("server", 15, new Color(100, 116, 139)), SwingConstants.RIGHT);
        runtimeLabel.setIconTextGap(6);
        status.add(runtimeLabel, BorderLayout.EAST);
        return status;
    }

    private void configureTable() {
        requestTable.setRowHeight(34);
        requestTable.setFillsViewportHeight(true);
        requestTable.setAutoCreateRowSorter(true);
        requestTable.getTableHeader().setReorderingAllowed(false);
        requestTable.getTableHeader().setPreferredSize(new Dimension(0, 36));
        requestTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        requestTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        requestTable.getColumnModel().getColumn(2).setPreferredWidth(75);
        requestTable.getColumnModel().getColumn(3).setPreferredWidth(300);
        requestTable.getColumnModel().getColumn(4).setPreferredWidth(125);
        requestTable.getColumnModel().getColumn(5).setPreferredWidth(110);
        requestTable.getColumnModel().getColumn(2).setCellRenderer(new MethodRenderer());
        requestTable.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
    }

    private void configureEvents() {
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> controller.stop());
        portField.addActionListener(e -> startServer());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.stop();
            }
        });
    }

    private void startServer() {
        String host = hostField.getText().trim();
        int port;
        try {
            if (host.isBlank()) {
                throw new IllegalArgumentException("Vui lòng nhập địa chỉ IP.");
            }
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("Cổng phải nằm trong khoảng 1 - 65535.");
            }
        } catch (NumberFormatException e) {
            showInputError("Cổng phải là một số nguyên hợp lệ.");
            return;
        } catch (IllegalArgumentException e) {
            showInputError(e.getMessage());
            return;
        }

        startButton.setEnabled(false);
        hostField.setEnabled(false);
        portField.setEnabled(false);
        final int selectedPort = port;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                controller.start(host, selectedPort);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    setServerRunning(false);
                    String message = rootMessage(e);
                    appendLog("Không thể khởi động Server: " + message, ServerController.LogType.ERROR);
                    JOptionPane.showMessageDialog(ServerFrame.this,
                            "Không thể khởi động Server.\n" + message,
                            "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    @Override
    public void onServerStarted(String host, int port) {
        runOnEdt(() -> {
            setServerRunning(true);
            listeningLabel.setText("Đang lắng nghe kết nối tại " + host + ":" + port);
            listeningLabel.setForeground(GREEN);
            listeningLabel.setIcon(ServerIcons.icon("circle", 11, GREEN));
            appendLog("Server khởi động tại cổng " + port, ServerController.LogType.SUCCESS);
        });
    }

    @Override
    public void onServerStopped() {
        runOnEdt(() -> {
            setServerRunning(false);
            listeningLabel.setText("Server đã dừng");
            listeningLabel.setForeground(RED);
            listeningLabel.setIcon(ServerIcons.icon("circle", 11, RED));
            appendLog("Server đã dừng", ServerController.LogType.ERROR);
        });
    }

    @Override
    public void onRequestStarted(String clientIp, BrowserRequest request) {
        int newTotal = total.incrementAndGet();
        int newActive = active.incrementAndGet();
        runOnEdt(() -> {
            totalValue.setText(String.valueOf(newTotal));
            activeValue.setText(String.valueOf(newActive));
            appendLog("Client " + clientIp + " đã kết nối", ServerController.LogType.INFO);
            appendLog(request.method() + "  " + request.url(), ServerController.LogType.METHOD);
        });
    }

    @Override
    public void onRequestFinished(RequestRecord record) {
        int newActive = active.updateAndGet(value -> Math.max(0, value - 1));
        int completed = record.successful() ? success.incrementAndGet() : errors.incrementAndGet();
        runOnEdt(() -> {
            activeValue.setText(String.valueOf(newActive));
            if (record.successful()) {
                successValue.setText(String.valueOf(completed));
            } else {
                errorValue.setText(String.valueOf(completed));
            }
            tableModel.add(record);
            String status = record.statusCode() > 0
                    ? record.statusCode() + " " + record.reasonPhrase()
                    : record.reasonPhrase();
            appendLog("Hoàn thành: " + status + " - " + record.elapsedMillis() + " ms",
                    record.successful() ? ServerController.LogType.SUCCESS : ServerController.LogType.ERROR);
        });
    }

    @Override
    public void onLog(String message, ServerController.LogType type) {
        runOnEdt(() -> appendLog(message, type));
    }

    private void setServerRunning(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        hostField.setEnabled(!running);
        portField.setEnabled(!running);
        serverState.setText(running ? "Server Online" : "Server Offline");
        serverState.setForeground(running ? new Color(11, 111, 59) : new Color(153, 27, 27));
        serverState.setBackground(running ? new Color(218, 248, 229) : new Color(254, 226, 226));
        serverState.setIcon(ServerIcons.icon("circle", 11, running ? GREEN : RED));
        serverState.setIconTextGap(7);
    }

    private void appendLog(String message, ServerController.LogType type) {
        StyledDocument document = logPane.getStyledDocument();
        SimpleAttributeSet style = new SimpleAttributeSet();
        Color color = switch (type) {
            case METHOD -> BLUE.brighter();
            case SUCCESS -> new Color(73, 222, 128);
            case ERROR -> new Color(251, 113, 133);
            default -> new Color(218, 226, 238);
        };
        StyleConstants.setForeground(style, color);
        try {
            document.insertString(document.getLength(),
                    "[" + LocalDateTime.now().format(TIME_FORMAT) + "]  " + message + "\n", style);
            logPane.setCaretPosition(document.getLength());
        } catch (BadLocationException ignored) {
            // The insertion offset always points to the document end.
        }
    }

    private JPanel statCard(String iconName, String title, JLabel value, Color accent, Color iconBackground) {
        JPanel panel = cardPanel(new BorderLayout(14, 0));
        JLabel iconLabel = new JLabel(ServerIcons.icon(iconName, 28, accent), SwingConstants.CENTER);
        iconLabel.setOpaque(true);
        iconLabel.setBackground(iconBackground);
        iconLabel.setPreferredSize(new Dimension(64, 64));
        panel.add(iconLabel, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(name.getFont().deriveFont(15f));
        text.add(Box.createVerticalGlue());
        text.add(name);
        text.add(Box.createVerticalStrut(4));
        text.add(value);
        text.add(Box.createVerticalGlue());
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private JPanel cardPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(12, 14, 12, 14)));
        return panel;
    }

    private JLabel sectionTitle(String text, String iconName) {
        JLabel label = new JLabel(text, ServerIcons.icon(iconName, 20, BLUE), SwingConstants.LEFT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 19f));
        label.setIconTextGap(8);
        return label;
    }

    private void stylePrimaryButton(JButton button, Color background) {
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setPreferredSize(new Dimension(190, 42));
    }

    private void showInputError(String message) {
        JOptionPane.showMessageDialog(this, message, "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private static JLabel statValue() {
        JLabel label = new JLabel("0");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 25f));
        return label;
    }

    private static final class RequestTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Thời gian", "Client IP", "Method", "URL", "Status", "Thời gian xử lý"
        };
        private static final DateTimeFormatter TABLE_TIME =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private final List<RequestRecord> rows = new ArrayList<>();

        void add(RequestRecord record) {
            int row = rows.size();
            rows.add(record);
            fireTableRowsInserted(row, row);
        }

        RequestRecord recordAt(int row) {
            return rows.get(row);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RequestRecord record = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> record.timestamp().format(TABLE_TIME);
                case 1 -> record.clientIp();
                case 2 -> record.method();
                case 3 -> record.url();
                case 4 -> record.statusCode() > 0
                        ? record.statusCode() + " " + record.reasonPhrase()
                        : "Lỗi";
                case 5 -> record.elapsedMillis() + " ms";
                default -> "";
            };
        }
    }

    private static final class MethodRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            if (!selected && value instanceof HttpMethodType method) {
                label.setForeground(switch (method) {
                    case GET -> BLUE;
                    case POST -> PURPLE;
                    case HEAD -> new Color(180, 95, 10);
                    default -> UIManager.getColor("Label.foreground");
                });
                label.setBackground(switch (method) {
                    case GET -> new Color(219, 234, 254);
                    case POST -> new Color(237, 233, 254);
                    case HEAD -> new Color(254, 243, 199);
                    default -> table.getBackground();
                });
            }
            return label;
        }
    }

    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            if (!selected) {
                int modelRow = table.convertRowIndexToModel(row);
                boolean ok = tableModel.recordAt(modelRow).successful();
                label.setForeground(ok ? new Color(11, 111, 59) : new Color(185, 28, 28));
                label.setBackground(ok ? new Color(220, 252, 231) : new Color(254, 226, 226));
            }
            return label;
        }
    }
}
