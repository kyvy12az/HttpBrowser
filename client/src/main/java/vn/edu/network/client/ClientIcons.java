package vn.edu.network.client;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import java.awt.Color;

final class ClientIcons {
    private ClientIcons() {
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
