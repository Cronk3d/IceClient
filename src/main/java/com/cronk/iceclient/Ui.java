package com.cronk.iceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class Ui {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void rect(int x, int y, int w, int h, int c) {
        Gui.drawRect(x, y, x + w, y + h, c);
    }

    /** Rectangle with the corner pixels cut off. */
    public static void round(int x, int y, int w, int h, int c) {
        Gui.drawRect(x + 1, y, x + w - 1, y + h, c);
        Gui.drawRect(x, y + 1, x + w, y + h - 1, c);
    }

    public static void roundBorder(int x, int y, int w, int h, int fill, int border) {
        round(x, y, w, h, border);
        round(x + 1, y + 1, w - 2, h - 2, fill);
    }

    public static void outline(int x, int y, int w, int h, int c) {
        rect(x, y, w, 1, c);
        rect(x, y + h - 1, w, 1, c);
        rect(x, y, 1, h, c);
        rect(x + w - 1, y, 1, h, c);
    }

    public static int alpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static void text(String s, int x, int y, int c) {
        mc.fontRendererObj.drawStringWithShadow(s, x, y, c);
    }

    public static int width(String s) {
        return mc.fontRendererObj.getStringWidth(s);
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void toggle(int x, int y, boolean on) {
        int acc = Theme.current().accent();
        roundBorder(x, y, 22, 11, on ? acc : 0xFF2A2638, on ? acc : 0xFF4A4560);
        rect(on ? x + 12 : x + 2, y + 2, 8, 7, 0xFFFFFFFF);
    }

    public static void button(int x, int y, int w, int h, String label, boolean hover) {
        int acc = Theme.current().accent();
        roundBorder(x, y, w, h, alpha(acc, hover ? 0x50 : 0x28), alpha(acc, 0xA0));
        text(label, x + (w - width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }
}
