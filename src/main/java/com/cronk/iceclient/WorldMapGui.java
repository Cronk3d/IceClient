package com.cronk.iceclient;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/** Full-screen terrain map of everything you have explored this session. Drag to pan, scroll to zoom. */
public class WorldMapGui extends GuiScreen {
    private double cx, cz;     // world position at the centre of the screen
    private float zoom = 2f;   // pixels per block
    private int lastX, lastY;
    private int frame;

    @Override
    public void initGui() {
        if (mc.thePlayer != null && cx == 0 && cz == 0) {
            cx = mc.thePlayer.posX;
            cz = mc.thePlayer.posZ;
        }
    }

    private int[] centerBtn() { return new int[]{width - 76, 8, 68, 16}; }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        drawRect(0, 0, width, height, 0xFF08060E);
        int acc = Theme.current().accent();

        float halfW = width / 2f / zoom, halfH = height / 2f / zoom;
        int minX = (int) Math.floor((cx - halfW) / 16) - 1, maxX = (int) Math.floor((cx + halfW) / 16) + 1;
        int minZ = (int) Math.floor((cz - halfH) / 16) - 1, maxZ = (int) Math.floor((cz + halfH) / 16) + 1;

        MapManager.mapFocus = true;
        MapManager.focusCx = (int) Math.floor(cx / 16);
        MapManager.focusCz = (int) Math.floor(cz / 16);
        if ((frame++ % 15) == 0) MapManager.requestView(minX, maxX, minZ, maxZ);

        GlStateManager.pushMatrix();
        GlStateManager.translate(width / 2f, height / 2f, 0);
        GlStateManager.scale(zoom, zoom, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        for (int tx = minX; tx <= maxX; tx++) {
            for (int tz = minZ; tz <= maxZ; tz++) {
                MapManager.Tile t = MapManager.get(tx, tz);
                if (t != null) MapManager.drawTile(t, (float) (tx * 16 - cx), (float) (tz * 16 - cz));
            }
        }
        GlStateManager.popMatrix();

        if (mc.thePlayer != null) {
            float sx = (float) (width / 2f + (mc.thePlayer.posX - cx) * zoom);
            float sy = (float) (height / 2f + (mc.thePlayer.posZ - cz) * zoom);
            MapManager.drawArrow(sx, sy, mc.thePlayer.rotationYaw + 180f, 2.2f, 0xFF000000);
            MapManager.drawArrow(sx, sy, mc.thePlayer.rotationYaw + 180f, 1.6f, 0xFFFFFFFF);
        }

        int wx = (int) Math.floor(cx + (mx - width / 2f) / zoom);
        int wz = (int) Math.floor(cz + (my - height / 2f) / zoom);
        Ui.roundBorder(8, height - 22, 150, 14, 0xCC0B0812, Ui.alpha(acc, 0x70));
        Ui.text("X " + wx + "   Z " + wz, 14, height - 19, 0xFFFFFFFF);
        Ui.text("Drag to move  \u2022  Scroll to zoom  \u2022  Esc to close", 8, 10, 0xFFC8C2D8);
        int[] b = centerBtn();
        Ui.button(b[0], b[1], b[2], b[3], "Centre", Ui.in(mx, my, b[0], b[1], b[2], b[3]));
        super.drawScreen(mx, my, pt);
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        super.mouseClicked(mx, my, btn);
        int[] b = centerBtn();
        if (btn == 0 && Ui.in(mx, my, b[0], b[1], b[2], b[3]) && mc.thePlayer != null) {
            cx = mc.thePlayer.posX;
            cz = mc.thePlayer.posZ;
            return;
        }
        lastX = mx;
        lastY = my;
    }

    @Override
    protected void mouseClickMove(int mx, int my, int btn, long time) {
        cx -= (mx - lastX) / zoom;
        cz -= (my - lastY) / zoom;
        lastX = mx;
        lastY = my;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) zoom = Math.max(0.4f, Math.min(8f, d > 0 ? zoom * 1.25f : zoom * 0.8f));
    }

    @Override
    public void onGuiClosed() {
        MapManager.mapFocus = false;
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
