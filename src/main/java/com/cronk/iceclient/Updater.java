package com.cronk.iceclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Update checker and installer.
 *
 * Safety rules:
 *  - HTTPS only, for both the version file and the jar;
 *  - nothing is downloaded until you press "Install update";
 *  - the download must match the SHA-256 in the version file or it is thrown away;
 *  - the running jar is never touched while the game is open. The new jar is saved
 *    next to it as "<name>.jar.update" and swapped in when you close the game.
 *
 * version.json looks like:
 *   { "version": "0.2.0", "url": "https://.../IceClient-0.2.0.jar", "sha256": "...", "notes": "..." }
 */
public class Updater {
    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR, DISABLED }

    public static volatile State state = State.IDLE;
    public static volatile String status = "Not checked yet";

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MAX_JAR = 60 * 1024 * 1024;

    private static volatile String latestVer = "", jarUrl = "", sha = "";
    private static File jarFile, tmpFile;
    private static boolean hookSet, notified;

    // ------------------------------------------------------------ setup

    public static void init() {
        jarFile = findJar();
        if (jarFile == null) {
            state = State.DISABLED;
            status = "Off in a development build";
            return;
        }
        tmpFile = new File(jarFile.getParentFile(), jarFile.getName() + ".update");
        if (tmpFile.exists()) tmpFile.delete();   // leftover from an update that never got applied
        if (ClickerConfig.updOn && !url().isEmpty()) check();
        else status = url().isEmpty() ? "Set an update URL first" : "Automatic checks are off";
    }

    private static String url() {
        return ClickerConfig.gets("updater", "url", "").trim();
    }

    private static File findJar() {
        try {
            URL loc = IceClient.class.getProtectionDomain().getCodeSource().getLocation();
            String s = loc.toString();
            if (s.startsWith("jar:")) s = s.substring(4);
            int bang = s.indexOf('!');
            if (bang >= 0) s = s.substring(0, bang);
            File f = new File(new URI(s));
            if (f.isFile() && f.getName().endsWith(".jar")) return f;
        } catch (Exception ignored) { }
        return null;
    }

    // ------------------------------------------------------------ checking

    private static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private static HttpURLConnection open(String address) throws Exception {
        URL u = new URL(address);
        if (!"https".equalsIgnoreCase(u.getProtocol())) throw new Exception("only https links are allowed");
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "IceClient/" + IceClient.VERSION);
        return c;
    }

    public static void check() {
        if (state == State.DISABLED || state == State.CHECKING || state == State.DOWNLOADING) return;
        final String address = url();
        if (address.isEmpty()) {
            state = State.IDLE;
            status = "Set an update URL first";
            return;
        }
        state = State.CHECKING;
        status = "Checking...";
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = open(address);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        if (out.size() > 64 * 1024) throw new Exception("version file is too large");
                    }
                }
                JsonObject o = new JsonParser().parse(out.toString("UTF-8")).getAsJsonObject();
                String v = o.get("version").getAsString();
                if (newer(v, IceClient.VERSION)) {
                    latestVer = v;
                    jarUrl = o.has("url") ? o.get("url").getAsString() : "";
                    sha = o.has("sha256") ? o.get("sha256").getAsString().trim().toLowerCase() : "";
                    state = State.AVAILABLE;
                    status = "Version " + v + " is available";
                } else {
                    state = State.UP_TO_DATE;
                    status = "Up to date (" + IceClient.VERSION + ")";
                }
            } catch (Exception e) {
                state = State.ERROR;
                status = "Check failed: " + e.getMessage();
            }
        }, "IceClient-update-check");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------ installing

    public static void install() {
        if (state != State.AVAILABLE) {
            if (state == State.IDLE || state == State.UP_TO_DATE || state == State.ERROR) check();
            return;
        }
        if (sha.isEmpty()) {
            state = State.ERROR;
            status = "Not installed: the version file has no sha256";
            return;
        }
        state = State.DOWNLOADING;
        status = "Downloading " + latestVer + "...";
        final String address = jarUrl;
        final String expected = sha;
        final String ver = latestVer;
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = open(address);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                long total = 0;
                byte[] first = new byte[2];
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmpFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (total == 0 && n >= 2) { first[0] = buf[0]; first[1] = buf[1]; }
                        total += n;
                        if (total > MAX_JAR) throw new Exception("file is too large");
                        md.update(buf, 0, n);
                        out.write(buf, 0, n);
                    }
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : md.digest()) hex.append(String.format("%02x", b));
                if (!hex.toString().equals(expected)) throw new Exception("checksum does not match");
                if (first[0] != 'P' || first[1] != 'K') throw new Exception("not a jar file");
                state = State.READY;
                status = "Version " + ver + " downloaded. Close the game to finish";
                registerHook();
            } catch (Exception e) {
                if (tmpFile != null) tmpFile.delete();
                state = State.ERROR;
                status = "Not installed: " + e.getMessage();
            }
        }, "IceClient-update-download");
        t.setDaemon(true);
        t.start();
    }

    /** Throws away a downloaded update. */
    public static void cancel() {
        if (state == State.READY) {
            if (tmpFile != null) tmpFile.delete();
            state = State.AVAILABLE;
            status = "Version " + latestVer + " is available";
        }
    }

    private static synchronized void registerHook() {
        if (hookSet) return;
        hookSet = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (state != State.READY || tmpFile == null || !tmpFile.exists()) return;
                String from = tmpFile.getAbsolutePath(), to = jarFile.getAbsolutePath();
                boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
                ProcessBuilder pb = win
                        ? new ProcessBuilder("cmd", "/c", "ping -n 4 127.0.0.1 >nul & move /Y \"" + from + "\" \"" + to + "\"")
                        : new ProcessBuilder("sh", "-c", "sleep 3; mv -f \"" + from + "\" \"" + to + "\"");
                pb.start();   // waits a few seconds for the game to release the jar, then swaps the file
            } catch (Exception ignored) { }
        }));
    }

    // ------------------------------------------------------------ in-game notice

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || notified || mc.thePlayer == null) return;
        if (state == State.AVAILABLE) {
            notified = true;
            mc.thePlayer.addChatMessage(new ChatComponentText("\u00A7d[Ice Client]\u00A7r Version " + latestVer
                    + " is available. Open settings (Right Shift) > Mods > Auto Updater to install it."));
        }
    }
}
