package com.cronk.iceclient;

import java.awt.Color;

public enum Theme {
    AMETHYST("Amethyst", 0xFFA070E0, 0xFF150F1F, 0xFF1E1630),
    ORCHID("Orchid", 0xFFDA70D6, 0xFF1A0F1A, 0xFF2A1630),
    ULTRAVIOLET("Ultraviolet", 0xFF7A3CFF, 0xFF0C0A1F, 0xFF161233),
    CHROMA("Chroma", 0xFFFFFFFF, 0xFF0F0F14, 0xFF1A1A24);

    public final String label;
    private final int accent;
    public final int bg;
    public final int panel;

    Theme(String label, int accent, int bg, int panel) {
        this.label = label;
        this.accent = accent;
        this.bg = bg;
        this.panel = panel;
    }

    /** Chroma cycles through hues; the others are fixed. */
    public int accent() {
        if (this == CHROMA) {
            float hue = (System.currentTimeMillis() % 6000L) / 6000f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF);
        }
        return accent;
    }

    public static Theme current() {
        Theme[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, ClickerConfig.theme))];
    }
}
