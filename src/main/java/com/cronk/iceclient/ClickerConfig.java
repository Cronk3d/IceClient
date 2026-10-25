package com.cronk.iceclient;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class ClickerConfig {
    // The clicker fires at most once per game tick, so 20 CPS is the ceiling.
    public static final int MAX_CPS = 20;

    public static boolean enabled = true;     // master switch for the whole mod
    public static boolean autoOn = false;     // Auto Clicker mod switch
    public static boolean leftOn = true;
    public static boolean rightOn = true;

    public static int leftMin = 8, leftMax = 11;
    public static int rightMin = 8, rightMax = 11;
    public static int paperMin = 18, paperMax = 20;
    public static int rightDelay = 0;          // ms before right click starts repeating
    public static String allowedOn = "saicopvp.com";   // empty = any server

    public static boolean updOn = true;       // check for updates on startup
    public static boolean chatOn = true;      // Chat Tabs mod
    public static int mentionSecs = 6;
    public static boolean mentionSound = true;

    public static boolean worldMapOn = true;
    public static int mapSize = 100;
    public static int mapZoom = 4;            // pixels per block = mapZoom / 2
    public static boolean mapRotate = true;
    public static boolean mapCoords = true;
    public static boolean mapCircle = false;  // false = square
    public static boolean mapSave = true;     // save explored map to disk
    public static int guiScale = 100;         // settings window size, percent
    public static int chatScale = 100;        // chat tab bar size, percent

    public static int theme = 0;
    public static int gappleSeconds = 30;

    private static Configuration cfg;

    public static int geti(String cat, String key, int def) {
        return cfg == null ? def : cfg.get(cat, key, def).getInt(def);
    }

    public static void seti(String cat, String key, int v) {
        if (cfg == null) return;
        cfg.get(cat, key, v).set(v);
        cfg.save();
    }

    public static String gets(String cat, String key, String def) {
        return cfg == null ? def : cfg.get(cat, key, def).getString();
    }

    public static void sets(String cat, String key, String v) {
        if (cfg == null) return;
        cfg.get(cat, key, v).set(v);
        cfg.save();
    }

    public static boolean getb(String cat, String key, boolean def) {
        return cfg == null ? def : cfg.get(cat, key, def).getBoolean(def);
    }

    public static void setb(String cat, String key, boolean v) {
        if (cfg == null) return;
        cfg.get(cat, key, v).set(v);
        cfg.save();
    }

    public static void load(File file) {
        cfg = new Configuration(file);
        enabled = cfg.getBoolean("enabled", "general", true, "Master switch for Ice Client");
        autoOn = cfg.getBoolean("autoOn", "clicker", false, "Auto Clicker enabled");
        leftOn = cfg.getBoolean("leftOn", "clicker", true, "Left click while held");
        rightOn = cfg.getBoolean("rightOn", "clicker", true, "Right click while held");
        leftMin = cfg.getInt("leftMin", "clicker", 8, 1, MAX_CPS, "");
        leftMax = cfg.getInt("leftMax", "clicker", 11, 1, MAX_CPS, "");
        rightMin = cfg.getInt("rightMin", "clicker", 8, 1, MAX_CPS, "");
        rightMax = cfg.getInt("rightMax", "clicker", 11, 1, MAX_CPS, "");
        paperMin = cfg.getInt("paperMin", "clicker", 18, 1, MAX_CPS, "");
        paperMax = cfg.getInt("paperMax", "clicker", 20, 1, MAX_CPS, "");
        rightDelay = cfg.getInt("rightDelay", "clicker", 0, 0, 1000, "");
        allowedOn = cfg.getString("allowedOn", "clicker", "saicopvp.com",
                "Servers it may run on (part of the address, comma separated). Empty = any.");
        updOn = cfg.getBoolean("updOn", "updater", true, "Check for updates on startup");
        chatOn = cfg.getBoolean("chatOn", "chat", true, "Chat Tabs enabled");
        mentionSecs = cfg.getInt("mentionSecs", "chat", 6, 1, 20, "Seconds the mention bar stays up");
        mentionSound = cfg.getBoolean("mentionSound", "chat", true, "Play a sound on mention");
        worldMapOn = cfg.getBoolean("worldMapOn", "map", true, "World Map enabled");
        mapSize = cfg.getInt("mapSize", "map", 100, 60, 200, "Minimap size in pixels");
        mapZoom = cfg.getInt("mapZoom", "map", 4, 1, 8, "Minimap zoom");
        mapRotate = cfg.getBoolean("mapRotate", "map", true, "Rotate the minimap with you");
        mapCoords = cfg.getBoolean("mapCoords", "map", true, "Show coordinates under the minimap");
        mapCircle = cfg.getBoolean("mapCircle", "map", false, "Circular minimap instead of square");
        mapSave = cfg.getBoolean("mapSave", "map", true, "Save the explored map to disk");
        guiScale = cfg.getInt("guiScale", "ui", 100, 50, 150, "Settings window size in percent");
        chatScale = cfg.getInt("chatScale", "chat", 100, 50, 200, "Chat tab bar size in percent");
        theme = cfg.getInt("theme", "ui", 0, 0, 3, "0 Amethyst, 1 Orchid, 2 Ultraviolet, 3 Chroma");
        gappleSeconds = cfg.getInt("gappleSeconds", "hud", 30, 1, 120, "Seconds shown on the gapple ring");
        if (cfg.hasChanged()) cfg.save();
    }

    public static void save() {
        if (cfg == null) return;
        cfg.get("general", "enabled", true).set(enabled);
        cfg.get("clicker", "autoOn", false).set(autoOn);
        cfg.get("clicker", "leftOn", true).set(leftOn);
        cfg.get("clicker", "rightOn", true).set(rightOn);
        cfg.get("clicker", "leftMin", 8).set(leftMin);
        cfg.get("clicker", "leftMax", 11).set(leftMax);
        cfg.get("clicker", "rightMin", 8).set(rightMin);
        cfg.get("clicker", "rightMax", 11).set(rightMax);
        cfg.get("clicker", "paperMin", 18).set(paperMin);
        cfg.get("clicker", "paperMax", 20).set(paperMax);
        cfg.get("clicker", "rightDelay", 0).set(rightDelay);
        cfg.get("clicker", "allowedOn", "saicopvp.com").set(allowedOn);
        cfg.get("updater", "updOn", true).set(updOn);
        cfg.get("chat", "chatOn", true).set(chatOn);
        cfg.get("chat", "mentionSecs", 6).set(mentionSecs);
        cfg.get("chat", "mentionSound", true).set(mentionSound);
        cfg.get("map", "worldMapOn", true).set(worldMapOn);
        cfg.get("map", "mapSize", 100).set(mapSize);
        cfg.get("map", "mapZoom", 4).set(mapZoom);
        cfg.get("map", "mapRotate", true).set(mapRotate);
        cfg.get("map", "mapCoords", true).set(mapCoords);
        cfg.get("map", "mapCircle", false).set(mapCircle);
        cfg.get("map", "mapSave", true).set(mapSave);
        cfg.get("ui", "guiScale", 100).set(guiScale);
        cfg.get("chat", "chatScale", 100).set(chatScale);
        cfg.get("ui", "theme", 0).set(theme);
        cfg.get("hud", "gappleSeconds", 30).set(gappleSeconds);
        cfg.save();
    }

    public static void resetAuto() {
        leftOn = true; rightOn = true;
        leftMin = 8; leftMax = 11;
        rightMin = 8; rightMax = 11;
        paperMin = 18; paperMax = 20;
        rightDelay = 0;
        allowedOn = "saicopvp.com";
        save();
    }
}
