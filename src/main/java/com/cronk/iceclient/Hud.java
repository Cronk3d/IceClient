package com.cronk.iceclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Hud {
    public static final List<Element> ELEMENTS = new ArrayList<Element>();
    private static final Minecraft mc = Minecraft.getMinecraft();

    static long gappleEnd = 0;
    static float partial = 1f;

    /** Draws an element at (x, y) at its own size. Non-movable ones (the gapple ring) scale themselves. */
    static void drawScaled(Element el, int x, int y, boolean preview) {
        el.ax = x;
        el.ay = y;
        if (!el.movable) {
            el.draw(x, y, preview);
            return;
        }
        float s = el.sf();
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, 0f);
        GlStateManager.scale(s, s, 1f);
        el.draw(0, 0, preview);
        GlStateManager.popMatrix();
    }

    static boolean minimapOn() {
        for (Element e : ELEMENTS) if (e instanceof Minimap && e.enabled) return true;
        return false;
    }
    static String mentionText = "";
    static long mentionUntil = 0;

    /** Called by ChatTabs when someone says your name. */
    static void pushMention(String text) {
        for (Element e : ELEMENTS) {
            if (e instanceof MentionBar && e.enabled) {
                mentionText = text;
                mentionUntil = System.currentTimeMillis() + ClickerConfig.mentionSecs * 1000L;
                if (ClickerConfig.mentionSound && mc.thePlayer != null) {
                    mc.thePlayer.playSound("random.orb", 0.8f, 1.2f);
                }
            }
        }
    }
    private boolean eating = false;
    private int eatSize = 0;

    public static void init() {
        ELEMENTS.clear();
        ELEMENTS.add(new Minimap());
        ELEMENTS.add(new LookingAt());
        ELEMENTS.add(new MentionBar());
        ELEMENTS.add(new Compass());
        ELEMENTS.add(new HeartCounter());
        ELEMENTS.add(new GappleRing());
        for (Element e : ELEMENTS) e.load();
    }

    // ---- events ----

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!ClickerConfig.enabled || mc.gameSettings.showDebugInfo) return;
        partial = e.partialTicks;
        ScaledResolution sr = e.resolution;
        for (Element el : ELEMENTS) {
            if (el.enabled && el.visible()) {
                drawScaled(el, el.px(sr.getScaledWidth()), el.py(sr.getScaledHeight()), false);
            }
        }
    }

    /** Detects finishing an enchanted golden apple to start the ring timer. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer p = mc.thePlayer;
        if (p == null) return;
        ItemStack held = p.getHeldItem();
        boolean nowEating = p.isUsingItem() && held != null
                && held.getItem() == Items.golden_apple && held.getMetadata() == 1;
        if (nowEating) {
            eating = true;
            eatSize = held.stackSize;
        } else if (eating) {
            eating = false;
            if (held == null || held.stackSize < eatSize) {
                gappleEnd = System.currentTimeMillis() + ClickerConfig.gappleSeconds * 1000L;
            }
        }
    }

    // ---- elements ----

    public abstract static class Element {
        public final String name;
        final String id;
        public boolean enabled = true;
        public int fx, fy;                 // position in 1/1000ths of the screen
        final int dfx, dfy;
        public final boolean movable;
        public int scale = 100;            // size in percent
        int ax, ay;                        // screen position it was last drawn at

        public float sf() { return scale / 100f; }
        public int sw() { return Math.round(w() * sf()); }
        public int sh() { return Math.round(h() * sf()); }

        Element(String name, int dfx, int dfy, boolean movable) {
            this.name = name;
            this.id = name.replace(" ", "");
            this.dfx = dfx;
            this.dfy = dfy;
            this.movable = movable;
        }

        public abstract int w();
        public abstract int h();
        abstract void draw(int x, int y, boolean preview);
        boolean visible() { return true; }

        void load() {
            enabled = ClickerConfig.getb("hud", id + ".on", true);
            fx = ClickerConfig.geti("hud", id + ".fx", dfx);
            fy = ClickerConfig.geti("hud", id + ".fy", dfy);
            scale = ClickerConfig.geti("hud", id + ".scale", 100);
        }

        public void save() {
            ClickerConfig.setb("hud", id + ".on", enabled);
            ClickerConfig.seti("hud", id + ".fx", fx);
            ClickerConfig.seti("hud", id + ".fy", fy);
            ClickerConfig.seti("hud", id + ".scale", scale);
        }

        public int px(int screenW) {
            return Math.max(0, Math.min(Math.max(0, screenW - sw()), fx * screenW / 1000));
        }

        public int py(int screenH) {
            return Math.max(0, Math.min(Math.max(0, screenH - sh()), fy * screenH / 1000));
        }

        public void setPos(int x, int y, int sw, int sh) {
            fx = Math.max(0, Math.min(1000, x * 1000 / sw));
            fy = Math.max(0, Math.min(1000, y * 1000 / sh));
        }
    }

    public static class LookingAt extends Element {
        LookingAt() { super("Looking At", 10, 20, true); }

        private String[] info() {
            MovingObjectPosition m = mc.objectMouseOver;
            if (m == null || mc.theWorld == null) return null;
            if (m.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos p = m.getBlockPos();
                String n = mc.theWorld.getBlockState(p).getBlock().getLocalizedName();
                return new String[]{n, p.getX() + ", " + p.getY() + ", " + p.getZ()};
            }
            if (m.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && m.entityHit != null) {
                return new String[]{m.entityHit.getName(), ""};
            }
            return null;
        }

        private String[] data(boolean preview) {
            return preview ? new String[]{"Sand", "9998, 253, 9622"} : info();
        }

        public int w() {
            String[] d = data(mc.currentScreen instanceof HudEditGui);
            if (d == null) return 0;
            return Ui.width(d[0]) + (d[1].isEmpty() ? 0 : Ui.width(d[1]) + 8) + 16;
        }

        public int h() { return 16; }

        boolean visible() { return info() != null; }

        void draw(int x, int y, boolean preview) {
            String[] d = data(preview);
            if (d == null) return;
            int acc = Theme.current().accent();
            Ui.roundBorder(x, y, w(), 16, 0xB00B0812, Ui.alpha(acc, 0x70));
            Ui.text(d[0], x + 8, y + 4, acc);
            if (!d[1].isEmpty()) Ui.text(d[1], x + 8 + Ui.width(d[0]) + 8, y + 4, 0xFFC8C2D8);
        }
    }

    public static class Compass extends Element {
        private static final String[] N = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

        Compass() { super("Compass", 400, 10, true); }

        public int w() { return 150; }
        public int h() { return 16; }

        private static float wrap(float v) {
            v = ((v % 360f) + 360f) % 360f;
            return v > 180f ? v - 360f : v;
        }

        void draw(int x, int y, boolean preview) {
            int acc = Theme.current().accent();
            Ui.roundBorder(x, y, w(), h(), 0xB00B0812, Ui.alpha(acc, 0x70));
            float yaw = preview || mc.thePlayer == null ? 45f : mc.thePlayer.rotationYaw;
            int half = w() / 2;
            for (int i = 0; i < 8; i++) {
                float rel = wrap(i * 45f - yaw);   // MC yaw: 0 = south, 90 = west
                if (Math.abs(rel) > 85f) continue;
                int cx = x + half + (int) (rel * (half - 8) / 90f);
                boolean cardinal = i % 2 == 0;
                Ui.text(N[i], cx - Ui.width(N[i]) / 2, y + 4, cardinal ? 0xFFFFFFFF : 0xFF9A93AD);
            }
            Ui.rect(x + half, y + 2, 1, h() - 4, acc);
        }
    }

    public static class HeartCounter extends Element {
        HeartCounter() { super("Heart Counter", 470, 560, true); }

        private String main(boolean preview) {
            float hp = preview || mc.thePlayer == null ? 20f : mc.thePlayer.getHealth();
            return "\u2665 " + (int) Math.ceil(hp / 2f);
        }

        private String extra(boolean preview) {
            float a = preview ? 4f : (mc.thePlayer == null ? 0f : mc.thePlayer.getAbsorptionAmount());
            return a > 0f ? " +" + (int) Math.ceil(a / 2f) : "";
        }

        public int w() {
            boolean pv = mc.currentScreen instanceof HudEditGui;
            return Ui.width(main(pv) + extra(pv));
        }

        public int h() { return 10; }

        void draw(int x, int y, boolean preview) {
            String m = main(preview);
            Ui.text(m, x, y, 0xFF55FFAA);
            String ex = extra(preview);
            if (!ex.isEmpty()) Ui.text(ex, x + Ui.width(m), y, 0xFFFFC83C);
        }
    }

    /** Top-down terrain minimap, square or circle. Terrain only: no players or entities are shown. */
    public static class Minimap extends Element {
        private Framebuffer fb;
        private int fbSize;

        Minimap() { super("Minimap", 800, 20, true); }

        public int w() { return ClickerConfig.mapSize; }
        public int h() { return ClickerConfig.mapSize + (ClickerConfig.mapCoords ? 12 : 0); }

        /** Draws the chunk textures. The caller has already moved the origin to the map centre. */
        private void content(double px, double pz, float glScale, float reach, boolean rot, float yaw) {
            GlStateManager.pushMatrix();
            if (rot) GlStateManager.rotate(180f - yaw, 0, 0, 1);
            GlStateManager.scale(glScale, glScale, 1f);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1f, 1f, 1f, 1f);
            int minX = (int) Math.floor((px - reach) / 16), maxX = (int) Math.floor((px + reach) / 16);
            int minZ = (int) Math.floor((pz - reach) / 16), maxZ = (int) Math.floor((pz + reach) / 16);
            for (int tx = minX; tx <= maxX; tx++) {
                for (int tz = minZ; tz <= maxZ; tz++) {
                    MapManager.Tile t = MapManager.get(tx, tz);
                    if (t != null) MapManager.drawTile(t, (float) (tx * 16 - px), (float) (tz * 16 - pz));
                }
            }
            GlStateManager.popMatrix();
        }

        void draw(int x, int y, boolean preview) {
            EntityPlayer p = mc.thePlayer;
            if (p == null) return;
            int s = ClickerConfig.mapSize;
            int acc = Theme.current().accent();
            double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * partial;
            double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partial;
            float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial;
            float zoom = ClickerConfig.mapZoom / 2f;       // pixels per block
            boolean rot = ClickerConfig.mapRotate;
            float cx = x + s / 2f, cy = y + s / 2f;
            float reach = (s / 2f) / zoom * (rot ? 1.45f : 1.05f);
            float sc = sf();
            int f = new ScaledResolution(mc).getScaleFactor();

            if (ClickerConfig.mapCircle) {
                // Render the map into a small texture, then draw that texture on a round disc.
                int size = Math.max(32, Math.round(s * sc * f));
                if (fb == null || fbSize != size) {
                    if (fb != null) fb.deleteFramebuffer();
                    fb = new Framebuffer(size, size, false);
                    fb.setFramebufferFilter(9729);
                    fbSize = size;
                }
                fb.setFramebufferColor(0.043f, 0.031f, 0.071f, 1f);
                fb.framebufferClear();
                fb.bindFramebuffer(true);
                GlStateManager.matrixMode(5889);
                GlStateManager.pushMatrix();
                GlStateManager.loadIdentity();
                GlStateManager.ortho(0, size, size, 0, 1000, 3000);
                GlStateManager.matrixMode(5888);
                GlStateManager.pushMatrix();
                GlStateManager.loadIdentity();
                GlStateManager.translate(0f, 0f, -2000f);
                GlStateManager.translate(size / 2f, size / 2f, 0f);
                content(px, pz, zoom * size / s, reach, rot, yaw);
                GlStateManager.matrixMode(5889);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(5888);
                GlStateManager.popMatrix();
                mc.getFramebuffer().bindFramebuffer(true);

                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.color(1f, 1f, 1f, 1f);
                fb.bindFramebufferTexture();
                Tessellator tess = Tessellator.getInstance();
                WorldRenderer wr = tess.getWorldRenderer();
                wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_TEX);
                wr.pos(cx, cy, 0).tex(0.5, 0.5).endVertex();
                float R = s / 2f;
                for (int i = 0; i <= 64; i++) {
                    double a = 2 * Math.PI * i / 64;
                    double ca = Math.cos(a), sa = Math.sin(a);
                    wr.pos(cx + ca * R, cy + sa * R, 0).tex(0.5 + 0.5 * ca, 0.5 - 0.5 * sa).endVertex();
                }
                tess.draw();
                MapManager.ring(cx, cy, R - 1.5f, R, Ui.alpha(acc, 0x90));
            } else {
                Ui.roundBorder(x, y, s, s, 0xFF0B0812, Ui.alpha(acc, 0x90));
                int sx = Math.round((ax + sc) * f);
                int sy = Math.round(mc.displayHeight - (ay + (s - 1) * sc) * f);
                int sz = Math.round((s - 2) * sc * f);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(sx, sy, sz, sz);
                GlStateManager.pushMatrix();
                GlStateManager.translate(cx, cy, 0f);
                content(px, pz, zoom, reach, rot, yaw);
                GlStateManager.popMatrix();
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            float arrowAngle = rot ? 0f : yaw + 180f;
            MapManager.drawArrow(cx, cy, arrowAngle, 1.5f, 0xFF000000);
            MapManager.drawArrow(cx, cy, arrowAngle, 1.1f, 0xFFFFFFFF);

            double a = Math.toRadians(rot ? 180f - yaw : 0f);
            float r = s / 2f - 7f;
            Ui.text("N", (int) (cx + Math.sin(a) * r) - 2, (int) (cy - Math.cos(a) * r) - 4, acc);

            if (ClickerConfig.mapCoords) {
                String c = (int) Math.floor(p.posX) + ", " + (int) Math.floor(p.posY) + ", " + (int) Math.floor(p.posZ);
                Ui.text(c, x + (s - Ui.width(c)) / 2, y + s + 3, 0xFFC8C2D8);
            }
        }
    }

    /** Popup box shown when your name is mentioned in chat. */
    public static class MentionBar extends Element {
        MentionBar() { super("Mention Bar", 360, 80, true); }

        public int w() { return 250; }
        public int h() { return 28; }

        boolean visible() { return mentionUntil > System.currentTimeMillis(); }

        void draw(int x, int y, boolean preview) {
            String body = preview ? "Bob: hey " + mc.getSession().getUsername() + " come to base" : mentionText;
            int acc = Theme.current().accent();
            Ui.roundBorder(x, y, w(), h(), 0xD00B0812, Ui.alpha(acc, 0x80));
            Ui.rect(x + 1, y + 3, 2, h() - 6, acc);
            Ui.text("Mention", x + 9, y + 4, acc);
            Ui.text(mc.fontRendererObj.trimStringToWidth(body, w() - 18), x + 9, y + 15, 0xFFFFFFFF);
        }
    }

    /** Timer ring around the crosshair after eating an enchanted golden apple. */
    public static class GappleRing extends Element {
        GappleRing() { super("Gapple Ring", 0, 0, false); }

        public int w() { return 0; }
        public int h() { return 0; }

        boolean visible() { return gappleEnd > System.currentTimeMillis(); }

        void draw(int x, int y, boolean preview) {
            ScaledResolution sr = new ScaledResolution(mc);
            float cx = sr.getScaledWidth() / 2f;
            float cy = sr.getScaledHeight() / 2f;
            long total = ClickerConfig.gappleSeconds * 1000L;
            long left = preview ? (long) (total * 0.6) : gappleEnd - System.currentTimeMillis();
            if (left <= 0) return;
            float frac = Math.min(1f, left / (float) total);

            float k = sf();
            arc(cx, cy, 11f * k, 13.5f * k, 1f, 0.25f, 0.2f, 0.22f, 0.7f);
            arc(cx, cy, 11f * k, 13.5f * k, frac, 1f, 0.78f, 0.2f, 1f);
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx + 18f * k, cy - 4f * k, 0f);
            GlStateManager.scale(k, k, 1f);
            Ui.text(String.valueOf((int) Math.ceil(left / 1000.0)), 0, 0, 0xFFFFC83C);
            GlStateManager.popMatrix();
        }

        private void arc(float cx, float cy, float r1, float r2, float frac,
                         float r, float g, float b, float a) {
            int steps = 64;
            int n = Math.max(1, (int) (steps * frac));
            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(r, g, b, a);
            WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
            wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION);
            for (int i = 0; i <= n; i++) {
                double ang = -Math.PI / 2 + (2 * Math.PI * Math.min(i, steps * frac)) / steps;
                double cs = Math.cos(ang), sn = Math.sin(ang);
                wr.pos(cx + cs * r1, cy + sn * r1, 0).endVertex();
                wr.pos(cx + cs * r2, cy + sn * r2, 0).endVertex();
            }
            Tessellator.getInstance().draw();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        }
    }
}
