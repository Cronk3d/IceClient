package com.cronk.iceclient;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SettingsGui extends GuiScreen {
    private static final int PW = 480, PH = 300, SB = 100, RH = 28;
    private static final int TILE_H = 34, GAP = 8;
    private static final String[] PAGES = {"Home", "Mods", "Appearance"};

    private static int page = 0;
    private static ModDef open = null;

    private int px, py, x0, y0, cw;
    private int scroll = 0;
    private float ui = 1f;       // window size multiplier

    private ModDef.Slider dragging;
    private int dragX, dragW;
    private ModDef.Text editing;
    private String buf = "";
    private boolean listening;                 // waiting for a key for the mod's own keybind
    private ModDef.KeySetting listeningKs;     // waiting for a key for an extra keybind

    private static class Tile {
        final String name;
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;

        Tile(String n, Supplier<Boolean> g, Consumer<Boolean> s) { name = n; get = g; set = s; }
    }

    // ------------------------------------------------------------ layout / scaling

    private float uiScale() {
        float fit = Math.min(width / (PW + 16f), height / (PH + 16f));
        return Math.max(0.4f, Math.min(ClickerConfig.guiScale / 100f, fit));
    }

    @Override
    public void initGui() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        x0 = px + SB + 14;
        y0 = py + 44;
        cw = PW - SB - 28;
        ui = uiScale();
    }

    private int tx(int mx) { return Math.round((mx - width / 2f) / ui + width / 2f); }
    private int ty(int my) { return Math.round((my - height / 2f) / ui + height / 2f); }
    private float sx(float x) { return (x - width / 2f) * ui + width / 2f; }
    private float sy(float y) { return (y - height / 2f) * ui + height / 2f; }

    private ModDef cur() { return page == 2 ? Mods.APPEARANCE : open; }
    private int listTop() { return page == 2 ? y0 + 46 : y0 + 60; }
    private int listHeight() { return py + PH - 10 - listTop(); }

    // ------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int rawX, int rawY, float pt) {
        Theme t = Theme.current();
        int acc = t.accent();
        drawRect(0, 0, width, height, 0x88000000);
        int mx = tx(rawX), my = ty(rawY);

        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2f, height / 2f, 0f);
        GlStateManager.scale(ui, ui, 1f);
        GlStateManager.translate(-width / 2f, -height / 2f, 0f);

        Ui.roundBorder(px, py, PW, PH, t.bg, Ui.alpha(acc, 0x80));
        Ui.round(px + 1, py + 1, SB - 1, PH - 2, t.panel);
        Ui.text("ICE CLIENT", px + 16, py + 16, acc);

        for (int i = 0; i < PAGES.length; i++) {
            int ry = py + 44 + i * 22;
            if (i == page) Ui.roundBorder(px + 8, ry, SB - 16, 18, Ui.alpha(acc, 0x30), Ui.alpha(acc, 0xA0));
            Ui.text(PAGES[i], px + 18, ry + 5, i == page ? 0xFFFFFFFF : 0xFFA9A2BD);
        }

        String title = page == 0 ? "Welcome, " + mc.getSession().getUsername() : PAGES[page];
        Ui.text(title, x0, py + 18, 0xFFFFFFFF);
        Ui.rect(x0, py + 32, cw, 1, Ui.alpha(acc, 0x40));

        if (page == 0) drawHome(mx, my, acc);
        else if (page == 1) {
            if (open == null) drawModList(mx, my, acc);
            else drawDetail(mx, my, acc);
        } else drawAppearance(mx, my, acc);

        GlStateManager.popMatrix();
        super.drawScreen(rawX, rawY, pt);
    }

    // ---- home

    private List<Tile> homeTiles() {
        List<Tile> l = new ArrayList<Tile>();
        l.add(new Tile("Ice Client", () -> ClickerConfig.enabled,
                v -> { ClickerConfig.enabled = v; ClickerConfig.save(); }));
        for (final ModDef m : Mods.ALL) l.add(new Tile(m.name, m.get, m.set));
        return l;
    }

    private int[] tileRect(int i) {
        int w = (cw - 2 * GAP) / 3;
        return new int[]{x0 + (i % 3) * (w + GAP), y0 + (i / 3) * (TILE_H + GAP), w, TILE_H};
    }

    private int[] editBtn() {
        return new int[]{x0, py + PH - 34, 90, 20};
    }

    private static String fmt(long ms) {
        long m = ms / 60000L;
        return (m / 60) + "h " + (m % 60) + "m";
    }

    private void drawHome(int mx, int my, int acc) {
        List<Tile> tl = homeTiles();
        for (int i = 0; i < tl.size(); i++) {
            int[] r = tileRect(i);
            boolean on = tl.get(i).get.get();
            Ui.roundBorder(r[0], r[1], r[2], r[3], on ? Ui.alpha(acc, 0x28) : 0xFF1A1526,
                    on ? Ui.alpha(acc, 0x90) : 0xFF332E45);
            Ui.text(tl.get(i).name, r[0] + 8, r[1] + 7, 0xFFFFFFFF);
            Ui.toggle(r[0] + 8, r[1] + 19, on);
        }
        int[] e = editBtn();
        Ui.button(e[0], e[1], e[2], e[3], "Edit HUD", Ui.in(mx, my, e[0], e[1], e[2], e[3]));
        Ui.text("This session: " + fmt(System.currentTimeMillis() - IceClient.START),
                x0 + 100, e[1] + 6, 0xFFA9A2BD);
        if (Updater.state == Updater.State.AVAILABLE || Updater.state == Updater.State.READY) {
            Ui.text("Update: " + mc.fontRendererObj.trimStringToWidth(Updater.status, cw - 50),
                    x0, e[1] - 14, acc);
        }
    }

    // ---- mods list

    private List<Object> entries() {
        List<Object> l = new ArrayList<Object>();
        String[] cats = {"Utility", "Chat", "HUD"};
        for (String c : cats) {
            l.add(c);
            for (ModDef m : Mods.ALL) if (m.category.equals(c)) l.add(m);
        }
        return l;
    }

    private int entryY(List<Object> es, int idx) {
        int y = y0;
        for (int i = 0; i < idx; i++) y += es.get(i) instanceof String ? 14 : 34;
        return y;
    }

    private void drawModList(int mx, int my, int acc) {
        List<Object> es = entries();
        for (int i = 0; i < es.size(); i++) {
            int y = entryY(es, i);
            Object o = es.get(i);
            if (o instanceof String) {
                Ui.text(((String) o).toUpperCase(), x0, y + 2, 0xFF8C86A0);
                continue;
            }
            ModDef m = (ModDef) o;
            boolean on = m.get.get();
            Ui.roundBorder(x0, y, cw, 30, Ui.in(mx, my, x0, y, cw, 30) ? Ui.alpha(acc, 0x20) : 0xFF1A1526,
                    0xFF332E45);
            Ui.roundBorder(x0 + 6, y + 4, 22, 22, Ui.alpha(acc, 0x30), Ui.alpha(acc, 0x90));
            Ui.text(m.name.substring(0, 1), x0 + 14, y + 11, acc);
            Ui.text(m.name, x0 + 36, y + 5, 0xFFFFFFFF);
            Ui.text(mc.fontRendererObj.trimStringToWidth(m.desc, cw - 100), x0 + 36, y + 17, 0xFF8C86A0);
            Ui.toggle(x0 + cw - 32, y + 10, on);
        }
    }

    // ---- mod detail

    private List<ModDef.Setting> rows(ModDef m) {
        List<ModDef.Setting> l = new ArrayList<ModDef.Setting>(m.settings);
        if (m != Mods.APPEARANCE) {
            l.add(new ModDef.KeyRow());
            l.add(new ModDef.Action("Reset", "Put this mod's settings back to their defaults", "Reset settings", m.reset));
        }
        return l;
    }

    private int[] ctrl(ModDef.Setting s, int ry) {
        int rx = x0 + cw - 10;
        if (s instanceof ModDef.Toggle) return new int[]{rx - 24, ry + 8, 22, 11};
        if (s instanceof ModDef.Slider) return new int[]{rx - 112, ry + 6, 112, 16};
        if (s instanceof ModDef.Text) return new int[]{rx - 120, ry + 5, 120, 18};
        return new int[]{rx - 100, ry + 5, 100, 18};
    }

    private int maxScroll() {
        return Math.max(0, rows(cur()).size() * RH - listHeight());
    }

    private String keyName(int k) {
        return k == 0 ? "None" : Keyboard.getKeyName(k);
    }

    private void drawDetail(int mx, int my, int acc) {
        Ui.button(x0, y0 - 6, 72, 16, "\u2039 All mods", Ui.in(mx, my, x0, y0 - 6, 72, 16));

        int hy = y0 + 16;
        Ui.roundBorder(x0, hy, 30, 30, Ui.alpha(acc, 0x30), Ui.alpha(acc, 0x90));
        Ui.text(open.name.substring(0, 1), x0 + 12, hy + 11, acc);
        Ui.text(open.name, x0 + 40, hy + 3, 0xFFFFFFFF);
        Ui.text(mc.fontRendererObj.trimStringToWidth(open.category + "  \u00B7  " + open.desc, cw - 110),
                x0 + 40, hy + 16, 0xFF8C86A0);
        boolean on = open.get.get();
        Ui.text(on ? "On" : "Off", x0 + cw - 58, hy + 10, 0xFFFFFFFF);
        Ui.toggle(x0 + cw - 32, hy + 10, on);

        drawRows(mx, my, acc);
    }

    private void drawRows(int mx, int my, int acc) {
        int ay = listTop(), ah = listHeight();
        Ui.roundBorder(x0, ay - 2, cw, ah + 4, 0x40000000, 0xFF332E45);
        scroll = Math.max(0, Math.min(maxScroll(), scroll));

        int f = new ScaledResolution(mc).getScaleFactor();
        float sxp = sx(x0), syp = sy(ay), sw = cw * ui, sh = ah * ui;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.round(sxp * f), mc.displayHeight - Math.round((syp + sh) * f),
                Math.round(sw * f), Math.round(sh * f));

        List<ModDef.Setting> rs = rows(cur());
        for (int i = 0; i < rs.size(); i++) {
            int ry = ay + i * RH - scroll;
            if (ry + RH < ay || ry > ay + ah) continue;
            ModDef.Setting s = rs.get(i);
            Ui.text(s.label, x0 + 10, ry + 5, 0xFFFFFFFF);
            Ui.text(mc.fontRendererObj.trimStringToWidth(s.desc, cw - 190), x0 + 10, ry + 16, 0xFF8C86A0);
            Ui.rect(x0 + 4, ry + RH - 1, cw - 8, 1, 0x22FFFFFF);
            drawControl(s, ry, mx, my, acc);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawControl(ModDef.Setting s, int ry, int mx, int my, int acc) {
        int[] c = ctrl(s, ry);
        if (s instanceof ModDef.Toggle) {
            Ui.toggle(c[0], c[1], ((ModDef.Toggle) s).get.get());
        } else if (s instanceof ModDef.Slider) {
            ModDef.Slider sl = (ModDef.Slider) s;
            int v = sl.get.getAsInt();
            int handle = (int) ((v - sl.min) / (float) (sl.max - sl.min) * c[2]);
            String txt = v + " " + sl.unit;
            Ui.text(txt, c[0] - 8 - Ui.width(txt), ry + 10, 0xFFFFFFFF);
            Ui.rect(c[0], c[1] + 6, c[2], 4, 0xFF2A2438);
            Ui.rect(c[0], c[1] + 6, handle, 4, acc);
            Ui.rect(c[0] + handle - 3, c[1] + 2, 6, 12, 0xFFFFFFFF);
        } else if (s instanceof ModDef.Text) {
            ModDef.Text tx = (ModDef.Text) s;
            boolean ed = editing == tx;
            Ui.roundBorder(c[0], c[1], c[2], c[3], 0xFF120E1C, ed ? acc : 0xFF332E45);
            String v = ed ? buf + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : tx.get.get();
            Ui.text(mc.fontRendererObj.trimStringToWidth(v, c[2] - 10, true), c[0] + 5, c[1] + 5, 0xFFFFFFFF);
        } else if (s instanceof ModDef.KeyRow) {
            Ui.button(c[0], c[1], c[2], c[3], listening ? "Press a key..." : keyName(cur().key),
                    Ui.in(mx, my, c[0], c[1], c[2], c[3]));
        } else if (s instanceof ModDef.KeySetting) {
            ModDef.KeySetting ks = (ModDef.KeySetting) s;
            Ui.button(c[0], c[1], c[2], c[3], listeningKs == ks ? "Press a key..." : keyName(ks.key),
                    Ui.in(mx, my, c[0], c[1], c[2], c[3]));
        } else if (s instanceof ModDef.Info) {
            String v = mc.fontRendererObj.trimStringToWidth(((ModDef.Info) s).value.get(), 170);
            Ui.text(v, x0 + cw - 10 - Ui.width(v), ry + 10, 0xFFC8C2D8);
        } else if (s instanceof ModDef.Action) {
            Ui.button(c[0], c[1], c[2], c[3], ((ModDef.Action) s).button, Ui.in(mx, my, c[0], c[1], c[2], c[3]));
        }
    }

    // ---- appearance

    private int[] themeRect(int i) {
        int w = (cw - 3 * GAP) / 4;
        return new int[]{x0 + i * (w + GAP), y0, w, 36};
    }

    private void drawAppearance(int mx, int my, int acc) {
        Theme[] all = Theme.values();
        for (int i = 0; i < all.length; i++) {
            int[] r = themeRect(i);
            boolean sel = ClickerConfig.theme == i;
            Ui.roundBorder(r[0], r[1], r[2], r[3], all[i].panel, sel ? all[i].accent() : 0xFF332E45);
            Ui.rect(r[0] + 6, r[1] + 6, r[2] - 12, 5, all[i].accent());
            Ui.text(all[i].label, r[0] + 6, r[1] + 20, 0xFFFFFFFF);
        }
        drawRows(mx, my, acc);
    }

    // ------------------------------------------------------------ input

    private void commitText() {
        if (editing != null) {
            editing.set.accept(buf.trim());
            ClickerConfig.save();
            editing = null;
        }
    }

    private void updateSlider(int mx) {
        float f = Math.max(0f, Math.min(1f, (mx - dragX) / (float) dragW));
        dragging.set.accept(dragging.min + Math.round(f * (dragging.max - dragging.min)));
    }

    @Override
    protected void mouseClicked(int rawX, int rawY, int btn) throws IOException {
        super.mouseClicked(rawX, rawY, btn);
        if (btn != 0) return;
        int mx = tx(rawX), my = ty(rawY);
        commitText();
        listening = false;
        listeningKs = null;

        for (int i = 0; i < PAGES.length; i++) {
            if (Ui.in(mx, my, px + 8, py + 44 + i * 22, SB - 16, 18)) {
                if (page != i) scroll = 0;
                page = i;
                if (i == 1) open = null;
                return;
            }
        }

        if (page == 0) clickHome(mx, my);
        else if (page == 1) {
            if (open == null) clickModList(mx, my);
            else clickDetail(mx, my);
        } else {
            for (int i = 0; i < Theme.values().length; i++) {
                int[] r = themeRect(i);
                if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                    ClickerConfig.theme = i;
                    ClickerConfig.save();
                    return;
                }
            }
            clickRows(mx, my);
        }
    }

    private void clickHome(int mx, int my) {
        List<Tile> tl = homeTiles();
        for (int i = 0; i < tl.size(); i++) {
            int[] r = tileRect(i);
            if (Ui.in(mx, my, r[0], r[1], r[2], r[3])) {
                tl.get(i).set.accept(!tl.get(i).get.get());
                return;
            }
        }
        int[] e = editBtn();
        if (Ui.in(mx, my, e[0], e[1], e[2], e[3])) mc.displayGuiScreen(new HudEditGui());
    }

    private void clickModList(int mx, int my) {
        List<Object> es = entries();
        for (int i = 0; i < es.size(); i++) {
            if (!(es.get(i) instanceof ModDef)) continue;
            ModDef m = (ModDef) es.get(i);
            int y = entryY(es, i);
            if (!Ui.in(mx, my, x0, y, cw, 30)) continue;
            if (Ui.in(mx, my, x0 + cw - 36, y + 6, 30, 18)) {
                m.set.accept(!m.get.get());
            } else {
                open = m;
                scroll = 0;
            }
            return;
        }
    }

    private void clickDetail(int mx, int my) {
        if (Ui.in(mx, my, x0, y0 - 6, 72, 16)) {
            open = null;
            scroll = 0;
            return;
        }
        int hy = y0 + 16;
        if (Ui.in(mx, my, x0 + cw - 62, hy + 4, 62, 24)) {
            open.set.accept(!open.get.get());
            return;
        }
        clickRows(mx, my);
    }

    private void clickRows(int mx, int my) {
        int ay = listTop(), ah = listHeight();
        if (!Ui.in(mx, my, x0, ay, cw, ah)) return;
        List<ModDef.Setting> rs = rows(cur());
        for (int i = 0; i < rs.size(); i++) {
            int ry = ay + i * RH - scroll;
            ModDef.Setting s = rs.get(i);
            int[] c = ctrl(s, ry);
            boolean hit = s instanceof ModDef.Slider
                    ? Ui.in(mx, my, c[0] - 4, c[1] - 2, c[2] + 8, c[3] + 4)
                    : Ui.in(mx, my, c[0], c[1], c[2], c[3]);
            if (!hit) continue;
            if (s instanceof ModDef.Toggle) {
                ModDef.Toggle tg = (ModDef.Toggle) s;
                tg.set.accept(!tg.get.get());
                ClickerConfig.save();
            } else if (s instanceof ModDef.Slider) {
                dragging = (ModDef.Slider) s;
                dragX = c[0];
                dragW = c[2];
                updateSlider(mx);
            } else if (s instanceof ModDef.Text) {
                editing = (ModDef.Text) s;
                buf = editing.get.get();
            } else if (s instanceof ModDef.KeyRow) {
                listening = true;
            } else if (s instanceof ModDef.KeySetting) {
                listeningKs = (ModDef.KeySetting) s;
            } else if (s instanceof ModDef.Action) {
                ((ModDef.Action) s).run.run();
            }
            return;
        }
    }

    @Override
    protected void mouseClickMove(int rawX, int rawY, int btn, long time) {
        if (dragging != null) updateSlider(tx(rawX));
    }

    @Override
    protected void mouseReleased(int rawX, int rawY, int state) {
        super.mouseReleased(rawX, rawY, state);
        if (dragging != null) {
            if (dragging.onRelease != null) dragging.onRelease.run();
            dragging = null;
            ClickerConfig.save();
            ui = uiScale();     // window size changes only once you let go, so dragging stays steady
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0 && ((page == 1 && open != null) || page == 2)) {
            scroll += d > 0 ? -RH : RH;
            scroll = Math.max(0, Math.min(maxScroll(), scroll));
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (listening || listeningKs != null) {
            int bound = (key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE) ? 0 : key;
            if (key != Keyboard.KEY_ESCAPE) {
                if (listeningKs != null) listeningKs.setKey(bound);
                else open.setKey(bound);
            }
            listening = false;
            listeningKs = null;
            return;
        }
        if (editing != null) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_ESCAPE) {
                commitText();
            } else if (key == Keyboard.KEY_BACK) {
                if (buf.length() > 0) buf = buf.substring(0, buf.length() - 1);
            } else if (ChatAllowedCharacters.isAllowedCharacter(c) && buf.length() < 60) {
                buf += c;
            }
            return;
        }
        super.keyTyped(c, key);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
