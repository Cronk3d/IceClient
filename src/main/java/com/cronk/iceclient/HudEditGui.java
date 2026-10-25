package com.cronk.iceclient;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class HudEditGui extends GuiScreen {
    private Hud.Element drag;
    private int offX, offY;

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawRect(0, 0, width, height, 0x66000000);
        int acc = Theme.current().accent();
        for (Hud.Element el : Hud.ELEMENTS) {
            if (!el.enabled) continue;
            int x = el.px(width), y = el.py(height);
            Hud.drawScaled(el, x, y, true);
            if (el.movable) Ui.outline(x - 2, y - 2, el.sw() + 4, el.sh() + 4, Ui.alpha(acc, 0xB0));
        }
        String hint = "Drag to move  \u2022  Scroll over an element to resize  \u2022  Esc to go back";
        Ui.text(hint, (width - Ui.width(hint)) / 2, height - 24, 0xFFFFFFFF);
        super.drawScreen(mx, my, pt);
    }

    private Hud.Element at(int mx, int my) {
        for (int i = Hud.ELEMENTS.size() - 1; i >= 0; i--) {
            Hud.Element el = Hud.ELEMENTS.get(i);
            if (!el.enabled || !el.movable) continue;
            int x = el.px(width), y = el.py(height);
            if (Ui.in(mx, my, x - 2, y - 2, el.sw() + 4, el.sh() + 4)) return el;
        }
        return null;
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        if (btn != 0) return;
        Hud.Element el = at(mx, my);
        if (el != null) {
            drag = el;
            offX = mx - el.px(width);
            offY = my - el.py(height);
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        if (drag != null) drag.setPos(mx - offX, my - offY, width, height);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        super.mouseReleased(mx, my, state);
        if (drag != null) {
            drag.save();
            drag = null;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        Hud.Element el = at(mx, my);
        if (el != null) {
            el.scale = Math.max(30, Math.min(400, el.scale + (d > 0 ? 10 : -10)));
            el.save();
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == 1) mc.displayGuiScreen(new SettingsGui());
        else super.keyTyped(c, key);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
