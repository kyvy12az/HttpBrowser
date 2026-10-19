package vn.edu.network.server;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.showButtons", true);

        SwingUtilities.invokeLater(() -> new ServerFrame().setVisible(true));
    }
}
