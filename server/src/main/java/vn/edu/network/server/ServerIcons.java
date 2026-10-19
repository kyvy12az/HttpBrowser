package vn.edu.network.server;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import java.awt.Color;

final class ServerIcons {
    private ServerIcons() {
    }

    static FlatSVGIcon icon(String name, int size) {
        return new FlatSVGIcon("icons/" + name + ".svg", size, size);
    }

    static FlatSVGIcon icon(String name, int size, Color color) {
        FlatSVGIcon icon = icon(name, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(original -> color));
        return icon;
    }
}
