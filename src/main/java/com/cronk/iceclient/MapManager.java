package com.cronk.iceclient;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Chunk-colour cache for the minimap and world map, saved to disk.
 *
 * Performance notes:
 *  - all disk reads and writes run on one low-priority background thread;
 *  - writes are batched every 15 seconds and only cover tiles whose pixels changed;
 *  - the main thread only copies a 1 KB array per changed tile and loads saved tiles
 *    under a ~1.5 ms per-tick time budget;
 *  - GL textures are created lazily, only for tiles that actually get drawn.
 *
 * Only terrain colours are stored. Saved files live in config/iceclient/maps/.
 */
public class MapManager {
    static class Tile {
        int[] pixels = new int[256];
        DynamicTexture tex;
        boolean texDirty = true;
        long updated;

        void ensureTex() {
            if (tex == null) tex = new DynamicTexture(16, 16);
            if (texDirty) {
                System.arraycopy(pixels, 0, tex.getTextureData(), 0, 256);
                tex.updateDynamicTexture();
                texDirty = false;
            }
        }
    }

    private static class Store {
        final File dir;
        final ConcurrentHashMap<Long, int[]> pending = new ConcurrentHashMap<Long, int[]>();
        final Set<Long> requested = new HashSet<Long>();   // main thread only

        Store(File dir) { this.dir = dir; }
    }

    private static class Loaded {
        Store store;
        int cx, cz;
        int[] px;
    }

    static final Map<Long, Tile> TILES = new HashMap<Long, Tile>();
    private static final int MAX_TILES = 12000;
    private static final int MAGIC = 0x49434531;   // "ICE1"
    private static final Object FILE_LOCK = new Object();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ConcurrentLinkedQueue<Loaded> loaded = new ConcurrentLinkedQueue<Loaded>();
    private static final int[] scratch = new int[256];

    private static final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "IceClient-map-io");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static volatile Store store;
    private static World world;
    private static int cursor = 0;
    private static int tickCount = 0;
    private static int[][] offs;
    private static int offR = -1;

    /** The world map screen sets this so tiles near what you are looking at are kept. */
    static boolean mapFocus = false;
    static int focusCx, focusCz;

    static {
        io.scheduleWithFixedDelay(() -> {
            try { flush(store); } catch (Throwable ignored) { }
        }, 15, 15, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { flush(store); } catch (Throwable ignored) { }
        }));
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    static Tile get(int cx, int cz) {
        return TILES.get(key(cx, cz));
    }

    static void clear() {
        for (Tile t : TILES.values()) if (t.tex != null) t.tex.deleteGlTexture();
        TILES.clear();
    }

    private static boolean wanted() {
        return ClickerConfig.enabled && (ClickerConfig.worldMapOn || Hud.minimapOn());
    }

    // ------------------------------------------------------------ session

    private static File storeDir() {
        String name;
        if (mc.isSingleplayer() && mc.getIntegratedServer() != null) {
            name = "sp_" + mc.getIntegratedServer().getFolderName();
        } else {
            ServerData sd = mc.getCurrentServerData();
            name = sd != null && sd.serverIP != null ? sd.serverIP : "unknown";
        }
        name = name.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        return new File(mc.mcDataDir, "config/iceclient/maps/" + name + "/dim" + mc.thePlayer.dimension);
    }

    private static void endSession() {
        final Store old = store;
        store = null;
        if (old != null) io.submit(() -> flush(old));
        clear();
        loaded.clear();
    }

    private static void buildOffsets(int r) {
        List<int[]> l = new ArrayList<int[]>();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) l.add(new int[]{dx, dz});
        Collections.sort(l, (a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        offs = l.toArray(new int[0][]);
        offR = r;
        cursor = 0;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (mc.theWorld == null || mc.thePlayer == null) {
            if (world != null) { endSession(); world = null; }
            return;
        }
        if (mc.theWorld != world) {      // new server or dimension
            endSession();
            world = mc.theWorld;
            store = new Store(storeDir());
            cursor = 0;
        }
        if (!wanted()) return;

        int pcx = ((int) Math.floor(mc.thePlayer.posX)) >> 4;
        int pcz = ((int) Math.floor(mc.thePlayer.posZ)) >> 4;
        if (!mapFocus) { focusCx = pcx; focusCz = pcz; }

        if (ClickerConfig.mapSave) {
            if ((tickCount++ % 20) == 0) requestAround(pcx >> 5, pcz >> 5);
            drainLoaded();
        }
        scan(pcx, pcz);
    }

    // ------------------------------------------------------------ scanning the live world

    private static void scan(int pcx, int pcz) {
        int r = Math.max(2, Math.min(mc.gameSettings.renderDistanceChunks, 12));
        if (offR != r) buildOffsets(r);
        long now = System.currentTimeMillis();
        int budget = 3;
        Store s = store;

        for (int n = 0; n < 48 && budget > 0; n++) {
            int[] o = offs[cursor];
            cursor = (cursor + 1) % offs.length;
            int cx = pcx + o[0], cz = pcz + o[1];
            Chunk ch = world.getChunkFromChunkCoords(cx, cz);
            if (ch == null || ch.isEmpty()) continue;

            long k = key(cx, cz);
            Tile t = TILES.get(k);
            int dist = Math.max(Math.abs(o[0]), Math.abs(o[1]));
            long refresh = dist <= 3 ? 2000L : 20000L;
            if (t != null && now - t.updated < refresh) continue;
            if (t == null) {
                if (TILES.size() >= MAX_TILES) evict();
                t = new Tile();
                TILES.put(k, t);
            }
            renderInto(ch, cx, cz, scratch);
            t.updated = now;
            budget--;
            if (!Arrays.equals(scratch, t.pixels)) {
                System.arraycopy(scratch, 0, t.pixels, 0, 256);
                t.texDirty = true;
                if (s != null && ClickerConfig.mapSave) s.pending.put(k, t.pixels.clone());
            }
        }
    }

    private static void evict() {
        long worst = 0;
        long worstD = -1;
        for (Long k : TILES.keySet()) {
            int cx = (int) (k >> 32), cz = (int) k.longValue();
            long d = (long) (cx - focusCx) * (cx - focusCx) + (long) (cz - focusCz) * (cz - focusCz);
            if (d > worstD) { worstD = d; worst = k; }
        }
        Tile t = TILES.remove(worst);
        if (t != null && t.tex != null) t.tex.deleteGlTexture();
        Store s = store;
        if (s != null) s.requested.remove(regionKey((int) (worst >> 32) >> 5, ((int) worst) >> 5));
    }

    private static int scale(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void renderInto(Chunk ch, int cx, int cz, int[] out) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                int y = Math.min(ch.getHeightValue(x, z), 255);
                Block b = null;
                MapColor col = MapColor.airColor;
                while (y > 0) {
                    IBlockState st = ch.getBlockState(new BlockPos(wx, y, wz));
                    b = st.getBlock();
                    col = b.getMapColor(st);
                    if (col != MapColor.airColor) break;
                    y--;
                }
                if (col == MapColor.airColor || b == null) {
                    out[z * 16 + x] = 0;
                    continue;
                }
                int rgb = col.colorValue;
                if (b.getMaterial() == Material.water) {
                    int yy = y;
                    while (yy > 1 && ch.getBlockState(new BlockPos(wx, yy - 1, wz)).getBlock()
                            .getMaterial() == Material.water) yy--;
                    out[z * 16 + x] = scale(rgb, Math.max(0.5f, 1f - (y - yy) * 0.04f));
                } else {
                    int hn = z > 0 ? ch.getHeightValue(x, z - 1)
                            : world.getHeight(new BlockPos(wx, 0, wz - 1)).getY();
                    int d = Math.max(-4, Math.min(4, (y + 1) - hn));
                    out[z * 16 + x] = scale(rgb, 1f + d * 0.06f);
                }
            }
        }
    }

    // ------------------------------------------------------------ disk: loading

    static long regionKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    private static File regionFile(File dir, int rx, int rz) {
        return new File(dir, "r." + rx + "." + rz + ".dat");
    }

    private static void requestRegion(final Store s, final int rx, final int rz) {
        if (s.requested.add(regionKey(rx, rz))) {
            io.submit(() -> {
                try { loadRegion(s, rx, rz); } catch (Throwable ignored) { }
            });
        }
    }

    private static void requestAround(int rx, int rz) {
        Store s = store;
        if (s == null) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) requestRegion(s, rx + dx, rz + dz);
    }

    /** Called by the world map: loads saved regions covering the visible area (nearest first, max 9). */
    static void requestView(int minCx, int maxCx, int minCz, int maxCz) {
        final Store s = store;
        if (s == null || !ClickerConfig.mapSave) return;
        List<int[]> regs = new ArrayList<int[]>();
        for (int rx = minCx >> 5; rx <= maxCx >> 5; rx++) {
            for (int rz = minCz >> 5; rz <= maxCz >> 5; rz++) regs.add(new int[]{rx, rz});
        }
        final int fx = focusCx >> 5, fz = focusCz >> 5;
        Collections.sort(regs, (a, b) -> Integer.compare(
                (a[0] - fx) * (a[0] - fx) + (a[1] - fz) * (a[1] - fz),
                (b[0] - fx) * (b[0] - fx) + (b[1] - fz) * (b[1] - fz)));
        for (int i = 0; i < Math.min(9, regs.size()); i++) requestRegion(s, regs.get(i)[0], regs.get(i)[1]);
    }

    private static void loadRegion(Store s, int rx, int rz) {
        int[][] t;
        synchronized (FILE_LOCK) {
            t = readRegion(regionFile(s.dir, rx, rz));
        }
        for (int i = 0; i < 1024; i++) {
            if (t[i] == null) continue;
            Loaded l = new Loaded();
            l.store = s;
            l.cx = (rx << 5) + (i & 31);
            l.cz = (rz << 5) + (i >> 5);
            l.px = t[i];
            loaded.add(l);
        }
    }

    private static void drainLoaded() {
        long start = System.nanoTime();
        Store s = store;
        for (int n = 0; n < 512; n++) {
            Loaded l = loaded.poll();
            if (l == null) break;
            if (l.store != s) continue;
            long k = key(l.cx, l.cz);
            if (!TILES.containsKey(k)) {
                if (TILES.size() >= MAX_TILES) evict();
                Tile t = new Tile();
                t.pixels = l.px;
                t.updated = 0;       // the live scan refreshes it as soon as the chunk is loaded
                TILES.put(k, t);
            }
            if (System.nanoTime() - start > 1_500_000L) break;
        }
    }

    // ------------------------------------------------------------ disk: saving (background thread)

    private static int[][] readRegion(File f) {
        int[][] t = new int[1024][];
        if (!f.exists()) return t;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(f))))) {
            if (in.readInt() != MAGIC) return t;
            for (int i = 0; i < 1024; i++) {
                if (in.readBoolean()) {
                    int[] px = new int[256];
                    for (int j = 0; j < 256; j++) px[j] = in.readInt();
                    t[i] = px;
                }
            }
        } catch (Exception ignored) {
            // damaged file: keep whatever was read and carry on
        }
        return t;
    }

    private static void writeRegion(File f, int[][] t) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            f.getParentFile().mkdirs();
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(new FileOutputStream(tmp))))) {
                out.writeInt(MAGIC);
                for (int i = 0; i < 1024; i++) {
                    out.writeBoolean(t[i] != null);
                    if (t[i] != null) for (int j = 0; j < 256; j++) out.writeInt(t[i][j]);
                }
            }
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } catch (Exception ignored) {
            tmp.delete();
        }
    }

    private static void flush(Store s) {
        if (s == null || s.pending.isEmpty()) return;
        Map<Long, Map<Integer, int[]>> byRegion = new HashMap<Long, Map<Integer, int[]>>();
        for (Long k : new ArrayList<Long>(s.pending.keySet())) {
            int[] px = s.pending.remove(k);
            if (px == null) continue;
            int cx = (int) (k >> 32), cz = (int) k.longValue();
            long rk = regionKey(cx >> 5, cz >> 5);
            Map<Integer, int[]> m = byRegion.get(rk);
            if (m == null) { m = new HashMap<Integer, int[]>(); byRegion.put(rk, m); }
            m.put((cz & 31) * 32 + (cx & 31), px);
        }
        synchronized (FILE_LOCK) {
            for (Map.Entry<Long, Map<Integer, int[]>> en : byRegion.entrySet()) {
                int rx = (int) (en.getKey() >> 32), rz = (int) en.getKey().longValue();
                File f = regionFile(s.dir, rx, rz);
                int[][] t = readRegion(f);
                for (Map.Entry<Integer, int[]> te : en.getValue().entrySet()) t[te.getKey()] = te.getValue();
                writeRegion(f, t);
            }
        }
    }

    /** Wipes the saved map for the current server/dimension and rebuilds it from what is loaded. */
    static void clearSaved() {
        final Store s = store;
        if (s == null) return;
        s.pending.clear();
        s.requested.clear();
        clear();
        loaded.clear();
        io.submit(() -> {
            synchronized (FILE_LOCK) {
                File[] fs = s.dir.listFiles();
                if (fs != null) for (File f : fs) if (f.getName().startsWith("r.")) f.delete();
            }
        });
    }

    // ------------------------------------------------------------ drawing helpers

    /** Draws one chunk as a textured quad. x and z are in whatever units the caller set up. */
    static void drawTile(Tile t, float x, float z) {
        t.ensureTex();
        GlStateManager.bindTexture(t.tex.getGlTextureId());
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        float s = 16.04f;   // tiny overlap so scaling never leaves hairline gaps
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x, z + s, 0).tex(0, 1).endVertex();
        wr.pos(x + s, z + s, 0).tex(1, 1).endVertex();
        wr.pos(x + s, z, 0).tex(1, 0).endVertex();
        wr.pos(x, z, 0).tex(0, 0).endVertex();
        tess.draw();
    }

    private static void setColor(int argb) {
        GlStateManager.color(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f,
                (argb & 255) / 255f, ((argb >>> 24) & 255) / 255f);
    }

    /** Small arrow, pointing up when angle is 0. */
    static void drawArrow(float cx, float cy, float angle, float size, int argb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.rotate(angle, 0, 0, 1);
        GlStateManager.scale(size, size, 1f);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        setColor(argb);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION);
        wr.pos(0, -5, 0).endVertex();
        wr.pos(4, 4, 0).endVertex();
        wr.pos(0, 2, 0).endVertex();
        wr.pos(0, -5, 0).endVertex();
        wr.pos(0, 2, 0).endVertex();
        wr.pos(-4, 4, 0).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /** Flat ring (used as the circular minimap border). */
    static void ring(float cx, float cy, float r1, float r2, int argb) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        setColor(argb);
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION);
        int seg = 64;
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            double c = Math.cos(a), s = Math.sin(a);
            wr.pos(cx + c * r1, cy + s * r1, 0).endVertex();
            wr.pos(cx + c * r2, cy + s * r2, 0).endVertex();
        }
        tess.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
